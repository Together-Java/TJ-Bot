package org.togetherjava.tjbot.features.projects;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.NumericScoreConfig;
import org.togetherjava.tjbot.features.EventReceiver;
import org.togetherjava.tjbot.features.Routine;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Manages a numeric score display on forum posts in configured project forums.
 *
 * <p>
 * When a new thread is created in a configured forum:
 * <ul>
 * <li>The bot reacts with upvote and downvote emojis so users know how to vote</li>
 * <li>The bot reacts with the initial score emoji (score = 1, counting the OP)</li>
 * </ul>
 *
 * <p>
 * When users add or remove upvote/downvote reactions on the post, the score emoji is updated. Score
 * emojis cannot be added by users — any such reaction is removed immediately.
 *
 * <p>
 * Score formula: {@code 1 (base for OP) + upvotes - downvotes}, where the OP's own votes are
 * excluded. The displayed emoji is determined by the configured score-to-emoji mapping.
 *
 * <p>
 * On startup, a routine scan corrects any missing or stale score emojis on all active posts,
 * recovering from bot downtime.
 */
public final class ProjectsNumericScoreListener extends ListenerAdapter
        implements EventReceiver, Routine {

    private static final Logger logger =
            LoggerFactory.getLogger(ProjectsNumericScoreListener.class);
    private static final int BASE_SCORE = 1;

    private final Map<Long, NumericScoreConfig> forumIdToConfig;
    private final Map<Long, Set<Emoji>> forumIdToScoreEmojis;

    /**
     * Creates a new instance.
     *
     * @param config the application configuration
     */
    public ProjectsNumericScoreListener(Config config) {
        forumIdToConfig = config.getNumericScoreConfigs()
            .stream()
            .collect(Collectors.toMap(NumericScoreConfig::forumId, Function.identity()));

        forumIdToScoreEmojis = config.getNumericScoreConfigs()
            .stream()
            .collect(Collectors.toMap(NumericScoreConfig::forumId,
                    ProjectsNumericScoreListener::buildScoreEmojiSet));
    }

    @Override
    public Schedule createSchedule() {
        // Run immediately on startup, then daily as a maintenance check
        return new Schedule(ScheduleMode.FIXED_RATE, 0, 24, TimeUnit.HOURS);
    }

    @Override
    public void runRoutine(JDA jda) {
        forumIdToConfig.forEach((forumId, cfg) -> syncForum(forumId, cfg, jda));
    }

    private void syncForum(long forumId, NumericScoreConfig cfg, JDA jda) {
        ForumChannel forum = jda.getChannelById(ForumChannel.class, forumId);
        if (forum == null) {
            logger.warn("Numeric score: forum channel {} not found or not cached, skipping sync",
                    forumId);
            return;
        }

        List<ThreadChannel> activeThreads = forum.getThreadChannels()
            .stream()
            .filter(t -> !t.isArchived())
            .toList();

        logger.debug("Syncing score emojis for {} active threads in forum {}", activeThreads.size(),
                forum.getName());

        Guild guild = forum.getGuild();
        activeThreads.forEach(thread -> thread.retrieveMessageById(thread.getIdLong())
            .queue(post -> refreshScore(post, thread, guild, jda),
                    e -> logger.warn("Failed to retrieve post for thread {} during sync",
                            thread.getId(), e)));
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromThread()) {
            return;
        }

        ThreadChannel thread = event.getChannel().asThreadChannel();
        NumericScoreConfig cfg = forumIdToConfig.get(thread.getParentChannel().getIdLong());
        if (cfg == null) {
            return;
        }

        // Only act on the initial post (first message — its ID equals the thread ID)
        if (!event.getMessageId().equals(thread.getId())) {
            return;
        }

        Message post = event.getMessage();
        Guild guild = event.getGuild();

        addVoteEmoji(cfg.upVoteEmoteName(), guild, post);
        addVoteEmoji(cfg.downVoteEmoteName(), guild, post);

        Emoji initialEmoji = Emoji.fromUnicode(scoreToEmojiStr(BASE_SCORE, cfg));
        post.addReaction(initialEmoji).queue(_ -> {}, e -> logger
            .warn("Failed to add initial score emoji to post {}", post.getId(), e));
    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        if (!event.isFromThread()) {
            return;
        }

        ThreadChannel thread = event.getChannel().asThreadChannel();
        long forumId = thread.getParentChannel().getIdLong();
        NumericScoreConfig cfg = forumIdToConfig.get(forumId);
        if (cfg == null) {
            return;
        }

        if (event.getMessageIdLong() != thread.getIdLong()) {
            return;
        }

        long selfId = event.getJDA().getSelfUser().getIdLong();
        if (event.getUserIdLong() == selfId) {
            return;
        }

        Emoji reacted = event.getReaction().getEmoji();
        if (isScoreEmoji(reacted, forumId)) {
            event.retrieveUser()
                .flatMap(user -> event.getReaction().removeReaction(user))
                .queue(_ -> {}, e -> logger.warn(
                        "Failed to remove score emoji added by user {} on post {}",
                        event.getUserId(), event.getMessageId(), e));
            return;
        }

        event.retrieveMessage()
            .queue(post -> refreshScore(post, thread, event.getGuild(), event.getJDA()));
    }

    @Override
    public void onMessageReactionRemove(MessageReactionRemoveEvent event) {
        if (!event.isFromThread()) {
            return;
        }

        ThreadChannel thread = event.getChannel().asThreadChannel();
        long forumId = thread.getParentChannel().getIdLong();
        NumericScoreConfig cfg = forumIdToConfig.get(forumId);
        if (cfg == null) {
            return;
        }

        if (event.getMessageIdLong() != thread.getIdLong()) {
            return;
        }

        // Score emoji removals are caused by the bot updating the score — do not react
        if (isScoreEmoji(event.getReaction().getEmoji(), forumId)) {
            return;
        }

        event.retrieveMessage()
            .queue(post -> refreshScore(post, thread, event.getGuild(), event.getJDA()));
    }

    private void refreshScore(Message post, ThreadChannel thread, Guild guild, JDA jda) {
        long forumId = thread.getParentChannel().getIdLong();
        NumericScoreConfig cfg = forumIdToConfig.get(forumId);
        if (cfg == null) {
            return;
        }

        long opId = thread.getOwnerIdLong();
        long botId = jda.getSelfUser().getIdLong();

        int upvotes = countVotes(post, cfg.upVoteEmoteName(), guild, opId, botId);
        int downvotes = countVotes(post, cfg.downVoteEmoteName(), guild, opId, botId);
        int score = BASE_SCORE + upvotes - downvotes;

        Emoji newScoreEmoji = Emoji.fromUnicode(scoreToEmojiStr(score, cfg));

        Optional<MessageReaction> currentBotScoreReaction = post.getReactions()
            .stream()
            .filter(r -> isScoreEmoji(r.getEmoji(), forumId) && r.isSelf())
            .findFirst();

        if (currentBotScoreReaction.isPresent()) {
            Emoji current = currentBotScoreReaction.get().getEmoji();
            if (current.equals(newScoreEmoji)) {
                return;
            }
            post.removeReaction(current)
                .flatMap(_ -> post.addReaction(newScoreEmoji))
                .queue(_ -> logger.debug("Updated score to {} on post {}", score, post.getId()),
                        e -> logger.warn("Failed to update score emoji on post {}", post.getId(),
                                e));
        } else {
            post.addReaction(newScoreEmoji)
                .queue(_ -> logger.debug("Added score {} to post {}", score, post.getId()),
                        e -> logger.warn("Failed to add score emoji to post {}", post.getId(), e));
        }
    }

    private static int countVotes(Message post, String emoteName, Guild guild, long opId,
            long botId) {
        Optional<Emoji> emojiOpt = resolveEmoji(emoteName, guild);
        if (emojiOpt.isEmpty()) {
            return 0;
        }
        Emoji voteEmoji = emojiOpt.get();

        return post.getReactions()
            .stream()
            .filter(r -> r.getEmoji().equals(voteEmoji))
            .findFirst()
            .map(r -> (int) r.retrieveUsers()
                .stream()
                .filter(u -> u.getIdLong() != opId && u.getIdLong() != botId)
                .count())
            .orElse(0);
    }

    private static void addVoteEmoji(String emoteName, Guild guild, Message post) {
        resolveEmoji(emoteName, guild).ifPresent(emoji -> post.addReaction(emoji).queue(_ -> {
        }, e -> logger.warn("Failed to add vote emoji '{}' to post {}", emoteName, post.getId(),
                e)));
    }

    private static Optional<Emoji> resolveEmoji(String emoteName, Guild guild) {
        List<RichCustomEmoji> custom = guild.getEmojisByName(emoteName, false);
        if (!custom.isEmpty()) {
            return Optional.of(custom.get(0));
        }
        return Optional.of(Emoji.fromUnicode(emoteName));
    }

    private boolean isScoreEmoji(Emoji emoji, long forumId) {
        Set<Emoji> scoreEmojis = forumIdToScoreEmojis.get(forumId);
        return scoreEmojis != null && scoreEmojis.contains(emoji);
    }

    private static Set<Emoji> buildScoreEmojiSet(NumericScoreConfig cfg) {
        return Stream
            .concat(Stream.concat(Stream.of(cfg.zeroScore()), cfg.positiveScores().stream()),
                    cfg.negativeScores().stream())
            .map(Emoji::fromUnicode)
            .collect(Collectors.toUnmodifiableSet());
    }

    private static String scoreToEmojiStr(int score, NumericScoreConfig cfg) {
        if (score == 0) {
            return cfg.zeroScore();
        }
        if (score > 0) {
            List<String> positive = cfg.positiveScores();
            return positive.get(Math.min(score - 1, positive.size() - 1));
        }
        List<String> negative = cfg.negativeScores();
        return negative.get(Math.min(-score - 1, negative.size() - 1));
    }
}
