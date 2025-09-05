package org.togetherjava.tjbot.features.github;

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.kohsuke.github.GHIssue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;
import org.togetherjava.tjbot.features.utils.StringDistances;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.stream.Stream;

/**
 * Slash command (/github-search) used to search for an issue in one of the repositories listed in
 * the config. It also auto suggests issues/PRs on trigger.
 */
public final class GitHubCommand extends SlashCommandAdapter {
    private static final Duration CACHE_EXPIRES_AFTER = Duration.ofMinutes(1);

    /**
     * Compares two GitHub Issues ascending by the time they have been updated at.
     */
    private static final Comparator<GHIssue> GITHUB_ISSUE_TIME_COMPARATOR = (i1, i2) -> {
        try {
            return i2.getUpdatedAt().compareTo(i1.getUpdatedAt());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    };

    private static final String TITLE_OPTION = "title";
    private static final Logger logger = LoggerFactory.getLogger(GitHubCommand.class);

    private final GitHubReference reference;

    private Instant lastCacheUpdate = Instant.EPOCH;
    private List<String> autocompleteGHIssueCache = List.of();

    /**
     * Constructs an instance of GitHubCommand.
     *
     * This constructor initializes a new GitHubCommand with the specified GitHubReference. It also
     * sets the command name to "github-search" and the command description to "Search configured
     * GitHub repositories for an issue/pull request".
     *
     * @param reference The GitHubReference used for searching issue/pull request in configured
     *        repositories.
     */
    public GitHubCommand(GitHubReference reference) {
        super("github-search", "Search configured GitHub repositories for an issue/pull request",
                CommandVisibility.GUILD);

        this.reference = reference;

        getData().addOption(OptionType.STRING, TITLE_OPTION,
                "Title of the issue you're looking for", true, true);

        CompletableFuture.runAsync(() -> {
            try {
                updateCache();
            } catch (Exception e) {
                logger.error("Unknown error updating the GitHub cache", e);
            }
        });

    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        String titleOption = event.getOption(TITLE_OPTION).getAsString();
        Matcher matcher = GitHubReference.ISSUE_REFERENCE_PATTERN.matcher(titleOption);

        if (!matcher.find()) {
            event.reply(
                    "Could not parse your query. Was not able to find an issue number in it (e.g. #207).")
                .setEphemeral(true)
                .queue();

            return;
        }

        int issueId = Integer.parseInt(matcher.group(GitHubReference.ID_GROUP));
        // extracting issue title from "[#10] add more stuff"
        String[] issueData = titleOption.split(" ", 2);
        String targetIssueTitle = issueData[1];

        reference.findIssue(issueId, targetIssueTitle)
            .ifPresentOrElse(issue -> event.replyEmbeds(reference.generateReply(issue)).queue(),
                    () -> event.reply("Could not find the issue you are looking for.")
                        .setEphemeral(true)
                        .queue());
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
        String title = event.getOption(TITLE_OPTION).getAsString();

        if (title.isEmpty()) {
            event.replyChoiceStrings(autocompleteGHIssueCache.stream().limit(25).toList()).queue();
        } else {
            Queue<String> closestSuggestions =
                    new PriorityQueue<>(Comparator.comparingInt(suggestionScorer(title)));

            closestSuggestions.addAll(autocompleteGHIssueCache);

            List<String> choices = Stream.generate(closestSuggestions::poll).limit(25).toList();
            event.replyChoiceStrings(choices).queue();
        }

        if (isCacheExpired()) {
            updateCache();
        }
    }

    private ToIntFunction<String> suggestionScorer(String title) {
        // Remove the ID [#123] and then match
        return s -> StringDistances.editDistance(title, s.replaceFirst("\\[#\\d+] ", ""));
    }

    private boolean isCacheExpired() {
        Instant cacheExpiresAt = lastCacheUpdate.plus(CACHE_EXPIRES_AFTER);
        return Instant.now().isAfter(cacheExpiresAt);
    }

    private void updateCache() {
        logger.debug("GitHub Autocomplete cache update started");

        autocompleteGHIssueCache = reference.getRepositories().stream().map(repo -> {
            try {
                return repo.queryIssues().pageSize(1000).list().toList();
            } catch (IOException ex) {
                throw new UncheckedIOException("Error fetching issues from repo " + repo.getName(),
                        ex);
            }
        })
            .flatMap(List::stream)
            .sorted(GITHUB_ISSUE_TIME_COMPARATOR)
            .map(issue -> "[#%d] %s".formatted(issue.getNumber(), issue.getTitle()))
            .toList();

        lastCacheUpdate = Instant.now();

        logger.debug("GitHub autocomplete cache update completed successfully. Cached {} issues.",
                autocompleteGHIssueCache.size());
    }
}
