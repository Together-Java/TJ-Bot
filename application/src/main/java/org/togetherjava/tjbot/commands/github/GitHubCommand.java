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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;

/**
 * Slash command (/github) used to search for an issue in one of the repositories listed in the
 * config. It even comes with auto completion!
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class GitHubCommand extends SlashCommandAdapter {
    private static final long ONE_MINUTE_IN_MILLIS = 60_000L;

    /**
     * The command option name of this slash command (this is only there to make the linter happy lol)
     */
    private static final String COMMAND_OPTION_NAME = "title";

    private final GitHubReference reference;

    /**
     * The last time the cache was updated (millis after Jan 1st 1970)
     * <p>
     * The cache updates every minute (see the ONE_MINUTE_IN_MILLIS constant)
     */
    private long lastCache;
    /**
     * The currently cached auto completion
     */
    private List<String> autocompleteCache;

    public GitHubCommand(GitHubReference reference) {
        super("github", "Search GitHub for an issue", CommandVisibility.GUILD);

        this.reference = reference;

        getData().addOption(OptionType.STRING, COMMAND_OPTION_NAME, "Title of the issue you're looking for",
                true, true);

        updateCache();
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        String title = event.getOption(COMMAND_OPTION_NAME).getAsString();
        Matcher matcher = GitHubReference.ISSUE_REFERENCE_PATTERN.matcher(title);

        if (!matcher.find()) {
            event.reply("Could not parse your query").setEphemeral(true).queue();

            return;
        }

        int id = Integer.parseInt(matcher.group(1));
        Optional<GHIssue> issue = reference.findIssue(id);

        if (issue.isPresent()) {
            try {
                event.replyEmbeds(reference.generateReply(issue.get())).queue();
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        } else {
            event.reply("Could not find the issue you are looking for").setEphemeral(true).queue();
        }
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
        String title = event.getOption("title").getAsString();

        if (title.isEmpty()) {
            event.replyChoiceStrings(autocompleteCache.stream().limit(25).toList()).queue();
        } else {
            event
                .replyChoiceStrings(autocompleteCache.stream()
                    .sorted(Comparator.comparingInt(s -> StringDistances.editDistance(title,
                            s.replaceFirst("\\[\\d+] ", ""))))
                    .limit(25)
                    .toList())
                .queue();
        }

        if (lastCache <= System.currentTimeMillis() - ONE_MINUTE_IN_MILLIS) {
            updateCache();
        }
    }

    /**
     * Updates the cache (has no time check, i.e. it does not check whether ONE_MINUTE_IN_MILLIS has
     * passed since the last cache)
     */
    private void updateCache() {
        autocompleteCache = reference.getRepositories().parallelStream().map(repo -> {
            try {
                return repo.getIssues(GHIssueState.ALL);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }).flatMap(List::stream).sorted((i1, i2) -> {
            try {
                return i2.getUpdatedAt().compareTo(i1.getUpdatedAt());
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }).map(issue -> "[#%d] %s".formatted(issue.getNumber(), issue.getTitle())).toList();

        lastCache = System.currentTimeMillis();
    }
}
