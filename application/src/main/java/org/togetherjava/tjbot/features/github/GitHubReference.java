package org.togetherjava.tjbot.features.github;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.apache.commons.collections4.ListUtils;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHIssueStateReason;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.features.MessageReceiverAdapter;

import java.awt.Color;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * GitHub Referencing feature. If someone sends #id of an issue (e.g. #207) in specified channel,
 * the bot replies with an embed that contains info on the issue/PR.
 */
public final class GitHubReference extends MessageReceiverAdapter {
    private static final Logger logger = LoggerFactory.getLogger(GitHubReference.class);
    static final String ID_GROUP = "id";

    /**
     * The pattern(#123) used to determine whether a message is referencing an issue.
     */
    static final Pattern ISSUE_REFERENCE_PATTERN =
            Pattern.compile("#(?<%s>\\d{1,5})".formatted(ID_GROUP));

    // Representing different GitHub states of an Issue/PR
    private static final Color OPEN_STATE = Color.green;
    private static final Color CLOSE_STATE = Color.red;
    private static final Color MERGED_STATE = new Color(141, 106, 187);
    private static final Color NOT_PLANNED_STATE = new Color(72, 72, 72);
    private static final Color DRAFT_STATE = Color.gray;


    /**
     * A constant representing the date and time formatter used for formatting the creation date of
     * an issue. The pattern "dd MMM, yyyy" represents the format "09 Oct, 2023".
     */
    static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd MMM, yyyy").withZone(ZoneOffset.UTC);
    private final Predicate<String> hasGithubIssueReferenceEnabled;
    private final Config config;

    /**
     * The repositories that are searched when looking for an issue.
     */
    private List<GHRepository> repositories;

    /**
     * Constructs an instance of GitHubReference.
     *
     * This constructor initializes a new GitHubReference with the specified Config. It also sets up
     * a predicate for matching allowed channels for feature and acquires repositories.
     *
     * @param config The Config to get allowed channel pattern for feature.
     */
    public GitHubReference(Config config) {
        this.config = config;
        this.hasGithubIssueReferenceEnabled =
                Pattern.compile(config.getGitHubReferencingEnabledChannelPattern())
                    .asMatchPredicate();
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
            logger.warn(
                    "The GitHub key ({}) used in this config is invalid. Skipping GitHubReference feature – {}",
                    config.getGitHubApiKey(), ex.getMessage());
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || !isAllowedChannelOrChildThread(event)) {
            return;
        }

        Message message = event.getMessage();
        String content = message.getContentRaw();
        Matcher matcher = ISSUE_REFERENCE_PATTERN.matcher(content);
        List<MessageEmbed> embeds = new ArrayList<>();

        while (matcher.find()) {
            long defaultRepoId = config.getGitHubRepositories().getFirst();

            int issueId = Integer.parseInt(matcher.group(ID_GROUP));
            findIssue(issueId, defaultRepoId).ifPresent(issue -> embeds.add(generateReply(issue)));
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

        MessageChannel sourceChannel = message.getChannelType() == ChannelType.GUILD_PUBLIC_THREAD
                ? message.getChannel().asThreadChannel()
                : message.getChannel().asTextChannel();

        for (List<MessageEmbed> messageEmbeds : partition) {
            if (isFirstBatch) {
                message.replyEmbeds(messageEmbeds).mentionRepliedUser(mentionRepliedUser).queue();

                isFirstBatch = false;
            } else {
                sourceChannel.sendMessageEmbeds(messageEmbeds).queue();
            }
        }
    }

    /**
     * Generates the embed to reply with when someone references an issue.
     */
    MessageEmbed generateReply(GHIssue issue) throws UncheckedIOException {
        try {
            String title = "[#%d] %s".formatted(issue.getNumber(), issue.getTitle());
            String titleUrl = issue.getHtmlUrl().toString();
            String description = issue.getBody();

            if (description != null && description.length() > MessageEmbed.DESCRIPTION_MAX_LENGTH) {
                description = "too long for preview, visit Github";
            }

            String labels = issue.getLabels()
                .stream()
                .map(GHLabel::getName)
                .collect(Collectors.joining(", "));

            String assignees = issue.getAssignees()
                .stream()
                .map(this::getUserNameOrThrow)
                .collect(Collectors.joining(", "));

            Instant createdAt = issue.getCreatedAt().toInstant();
            String dateOfCreation = FORMATTER.format(createdAt);

            String footer = "%s • %s • %s".formatted(labels, assignees, dateOfCreation);
            return new EmbedBuilder().setColor(getIssueStateColor(issue))
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
     * Returns the color based on the state of the issue/PR
     */
    private Color getIssueStateColor(GHIssue issue) throws IOException {
        if (issue instanceof GHPullRequest pr) {
            if (pr.isMerged()) {
                return MERGED_STATE;
            } else if (pr.isDraft()) {
                return DRAFT_STATE;
            }
        } else {
            if (issue.getStateReason() == GHIssueStateReason.COMPLETED) {
                return MERGED_STATE;
            } else if (issue.getStateReason() == GHIssueStateReason.NOT_PLANNED) {
                return NOT_PLANNED_STATE;
            }
        }
        return issue.getState() == GHIssueState.OPEN ? OPEN_STATE : CLOSE_STATE;
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
    Optional<GHIssue> findIssue(int id, String targetIssueTitle) {
        return repositories.stream().map(repository -> {
            try {
                GHIssue issue = repository.getIssue(id);
                if (issue.isPullRequest()) {
                    issue = repository.getPullRequest(id);
                }
                if (issue.getTitle().equals(targetIssueTitle)) {
                    return Optional.of(issue);
                }
            } catch (FileNotFoundException ignored) {
                return Optional.<GHIssue>empty();
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
            return Optional.<GHIssue>empty();
        }).filter(Optional::isPresent).findFirst().orElse(Optional.empty());
    }

    Optional<GHIssue> findIssue(int id, long defaultRepoId) {
        return repositories.stream()
            .filter(repository -> repository.getId() == defaultRepoId)
            .map(repository -> {
                try {
                    GHIssue issue = repository.getIssue(id);
                    if (issue.isPullRequest()) {
                        issue = repository.getPullRequest(id);
                    }
                    return Optional.of(issue);
                } catch (FileNotFoundException ignored) {
                    return Optional.<GHIssue>empty();
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            })
            .filter(Optional::isPresent)
            .map(Optional::orElseThrow)
            .findAny();
    }

    /**
     * All repositories monitored by this instance.
     */
    List<GHRepository> getRepositories() {
        return repositories;
    }

    private boolean isAllowedChannelOrChildThread(MessageReceivedEvent event) {
        if (event.getChannelType() != ChannelType.TEXT) {
            return false;
        }

        if (event.getChannelType().isThread()) {
            ThreadChannel threadChannel = event.getChannel().asThreadChannel();
            String rootChannel = threadChannel.getParentChannel().getName();
            return this.hasGithubIssueReferenceEnabled.test(rootChannel);
        }

        String textChannel = event.getChannel().asTextChannel().getName();
        return this.hasGithubIssueReferenceEnabled.test(textChannel);
    }
}
