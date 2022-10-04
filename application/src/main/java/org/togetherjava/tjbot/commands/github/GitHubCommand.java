package org.togetherjava.tjbot.commands.github;

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.togetherjava.tjbot.annotations.MethodsReturnNonnullByDefault;
import org.togetherjava.tjbot.commands.CommandVisibility;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.utils.StringDistances;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.*;
import java.util.regex.Matcher;
import java.util.stream.Stream;

/**
 * Slash command (/github) used to search for an issue in one of the repositories listed in the
 * config. It even comes with auto completion!
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class GitHubCommand extends SlashCommandAdapter {
    private static final long ONE_MINUTE_IN_MILLIS = 60_000L;

    /**
     * Compares the getUpdatedAt values
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
    private List<String> autocompleteCache;

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

        reference.findIssue(Integer.parseInt(matcher.group("id")))
            .ifPresentOrElse(issue -> event.replyEmbeds(reference.generateReply(issue)).queue(),
                    () -> event.reply("Could not find the issue you are looking for.")
                        .setEphemeral(true)
                        .queue());
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
        String title = event.getOption(TITLE_OPTION).getAsString();

        if (title.isEmpty()) {
            event.replyChoiceStrings(autocompleteCache.stream().limit(25).toList()).queue();
        } else {
            Queue<String> queue = new PriorityQueue<>(Comparator.comparingInt(
                    s -> StringDistances.editDistance(title, s.replaceFirst("\\[#\\d+] ", ""))));

            queue.addAll(autocompleteCache);

            event.replyChoiceStrings(Stream.generate(queue::poll).limit(25).toList()).queue();
        }

        if (lastCacheUpdate.isAfter(Instant.now().minus(Duration.ofMinutes(1)))) {
            updateCache();
        }
    }

    private void updateCache() {
        autocompleteCache = reference.getRepositories().parallelStream().map(repo -> {
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
