package org.togetherjava.tjbot.commands.github;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.apache.commons.collections4.ListUtils;
import org.kohsuke.github.*;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.features.MessageReceiverAdapter;

import java.awt.*;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * GitHub Referencing feature. If someone sends #id of an issue (e.g. #207) in specified channel,
 * the bot replies with an embed that contains info on the issue/PR.
 */
public class GitHubReference extends MessageReceiverAdapter {
    /**
     * The pattern used to determine whether a message is referencing an issue.
     */
    protected static final String ID_GROUP = "id";
    protected static final Pattern ISSUE_REFERENCE_PATTERN =
            Pattern.compile("#(?<%s>\\d+)".formatted(ID_GROUP));
    private static final int ISSUE_OPEN = Color.green.getRGB();
    private static final int ISSUE_CLOSE = Color.red.getRGB();
    private final Config config;

    /**
     * The repositories that are searched when looking for an issue.
     */
    private List<GHRepository> repositories;

    public GitHubReference(Config config) {
        super(Pattern.compile(config.getGitHubReferencingEnabledChannelPattern()));

        this.config = config;

        acquireRepositories();
    }

    /**
     * Acquires the list of repositories to use as a source for lookup.
     */
    private void acquireRepositories() {
        try {
            repositories = new ArrayList<>();

            GitHub githubApi = GitHub.connectUsingOAuth(config.getGitHubApiKey());

            for (long repoId : config.getGitHubRepositories()) {
                repositories.add(githubApi.getRepositoryById(repoId));
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }

        Message message = event.getMessage();
        String content = message.getContentRaw();
        Matcher matcher = ISSUE_REFERENCE_PATTERN.matcher(content);
        List<MessageEmbed> embeds = new ArrayList<>();

        while (matcher.find()) {
            findIssue(Integer.parseInt(matcher.group(ID_GROUP)))
                .ifPresent(issue -> embeds.add(generateReply(issue)));
        }

        replyBatchEmbeds(embeds, message, false);
    }

    /**
     * Replies to the given message with the given embeds in "batches", sending
     * {@value Message#MAX_EMBED_COUNT} embeds at a time (the discord limit)
     */
    private void replyBatchEmbeds(List<MessageEmbed> embeds, Message message,
            boolean mentionRepliedUser) {
        List<List<MessageEmbed>> partition = ListUtils.partition(embeds, Message.MAX_EMBED_COUNT);
        boolean isFirstBatch = true;
        TextChannel textChannel = message.getChannel().asTextChannel();

        for (List<MessageEmbed> messageEmbeds : partition) {
            if (isFirstBatch) {
                message.replyEmbeds(messageEmbeds).mentionRepliedUser(mentionRepliedUser).queue();

                isFirstBatch = false;
            } else {
                textChannel.sendMessageEmbeds(messageEmbeds).queue();
            }
        }
    }

    /**
     * Generates the embed to reply with when someone references an issue.
     */
    protected MessageEmbed generateReply(GHIssue issue) throws UncheckedIOException {
        try {
            String title = "[#%d] %s".formatted(issue.getNumber(), issue.getTitle());
            String titleUrl = issue.getHtmlUrl().toString();
            String description = issue.getBody();

            String labels = issue.getLabels()
                .stream()
                .map(GHLabel::getName)
                .collect(Collectors.joining(", "));

            String assignees = issue.getAssignees()
                .stream()
                .map(this::getUserNameOrThrow)
                .collect(Collectors.joining(", "));

            String createdAt = issue.getCreatedAt().toString();

            String footer = "%s • %s • %s".formatted(labels, assignees, createdAt);

            return new EmbedBuilder()
                .setColor(issue.getState() == GHIssueState.OPEN ? ISSUE_OPEN : ISSUE_CLOSE)
                .setTitle(title, titleUrl)
                .setDescription(description)
                .setAuthor(issue.getUser().getName(), null, issue.getUser().getAvatarUrl())
                .setFooter(footer)
                .build();

        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * Either properly gathers the name of a user or throws a UncheckedIOException.
     */
    private String getUserNameOrThrow(GHUser user) throws UncheckedIOException {
        try {
            return user.getName();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * Looks through all of the given repositories for an issue/pr with the given id.
     */
    protected Optional<GHIssue> findIssue(int id) {
        return repositories.stream().map(repository -> {
            try {
                return Optional.of(repository.getIssue(id));
            } catch (FileNotFoundException ignored) {
                return Optional.<GHIssue>empty();
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }).filter(Optional::isPresent).map(Optional::orElseThrow).findAny();
    }

    /**
     * All repositories monitored by this instance.
     */
    protected List<GHRepository> getRepositories() {
        return repositories;
    }
}
