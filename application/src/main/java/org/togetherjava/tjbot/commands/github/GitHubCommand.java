package org.togetherjava.tjbot.commands.github;

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;

import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;
import org.togetherjava.tjbot.features.utils.StringDistances;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.stream.Stream;

/**
 * Slash command (/github) used to search for an issue in one of the repositories listed in the
 * config. It even comes with auto completion!
 */
public class GitHubCommand extends SlashCommandAdapter {
    private static final Duration CACHE_EXPIRES_AFTER = Duration.ofMinutes(1);

    /**
     * Compares the getUpdatedAt values.
     */
    private static final Comparator<GHIssue> GITHUB_ISSUE_TIME_COMPARATOR = (i1, i2) -> {
        try {
            return i2.getUpdatedAt().compareTo(i1.getUpdatedAt());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    };

    private static final String TITLE_OPTION = "title";

    private final GitHubReference reference;

    private Instant lastCacheUpdate;
    private List<String> autocompleteGHIssueCache;

    public GitHubCommand(GitHubReference reference) {
        super("github-search", "Search configured GitHub repositories for an issue/pull request",
                CommandVisibility.GUILD);

        this.reference = reference;

        getData().addOption(OptionType.STRING, TITLE_OPTION,
                "Title of the issue you're looking for", true, true);

        updateCache();
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        String title = event.getOption(TITLE_OPTION).getAsString();
        Matcher matcher = GitHubReference.ISSUE_REFERENCE_PATTERN.matcher(title);

        if (!matcher.find()) {
            event.reply(
                    "Could not parse your query. Was not able to find an issue number in it (e.g. #207).")
                .setEphemeral(true)
                .queue();

            return;
        }

        reference.findIssue(Integer.parseInt(matcher.group(GitHubReference.ID_GROUP)))
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

            event.replyChoiceStrings(Stream.generate(closestSuggestions::poll).limit(25).toList())
                .queue();
        }

        if (lastCacheUpdate.isAfter(Instant.now().minus(CACHE_EXPIRES_AFTER))) {
            updateCache();
        }
    }

    private ToIntFunction<String> suggestionScorer(String title) {
        return s -> StringDistances.editDistance(title, s.replaceFirst("\\[#\\d+] ", ""));
    }

    private void updateCache() {
        autocompleteGHIssueCache = reference.getRepositories().parallelStream().map(repo -> {
            try {
                return repo.getIssues(GHIssueState.ALL);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        })
            .flatMap(List::stream)
            .sorted(GITHUB_ISSUE_TIME_COMPARATOR)
            .map(issue -> "[#%d] %s".formatted(issue.getNumber(), issue.getTitle()))
            .toList();

        lastCacheUpdate = Instant.now();
    }
}
