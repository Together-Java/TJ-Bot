package org.togetherjava.tjbot.features.numericscore;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.message.GenericMessageEvent;
import net.dv8tion.jda.api.events.message.react.*;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.internal.requests.CompletedRestAction;
import org.jetbrains.annotations.NotNull;

import org.togetherjava.tjbot.config.NumericScoreConfig;
import org.togetherjava.tjbot.features.EventReceiver;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

public class NumericScoreFeature extends ListenerAdapter implements EventReceiver {
    private final List<NumericScoreConfig> numericScoreConfig;

    public NumericScoreFeature(List<NumericScoreConfig> numericScoreConfig) {
        this.numericScoreConfig = numericScoreConfig;
    }

    /**
     * Runs a function with the config found for the given thread channel. If the given channel
     * isn't a thread channel in a forum or no config is found for it, nothing will happen.
     *
     * @param channel the supposed thread channel
     * @param configConsumer the function to run with the config found on the post message
     */
    private void withConfig(Channel channel,
            BiConsumer<Message, NumericScoreConfig> configConsumer) {
        if (channel instanceof ThreadChannel threadChannel
                && threadChannel.getParentChannel() instanceof ForumChannel) {
            numericScoreConfig.stream()
                .filter(c -> c.forumId() == threadChannel.getParentChannel().getIdLong())
                .findFirst()
                .ifPresent(c -> threadChannel.retrieveStartMessage()
                    .queue(m -> configConsumer.accept(m, c)));
        }
    }

    /**
     * Runs a function with the config found for the given thread channel. If the given message
     * isn't a post in a forum or no config is found for it, nothing will happen.
     *
     * @param message the supposed forum post
     * @param configConsumer the function to run with the config found on the post message
     */
    private void withConfig(Message message,
            BiConsumer<Message, NumericScoreConfig> configConsumer) {
        withConfig(message.getChannel(), configConsumer);
    }

    /**
     * Runs a function with the config found for the given thread channel. If the given message
     * isn't a post in a forum or no config is found for it, nothing will happen.
     *
     * @param event the supposed forum post reaction event
     * @param configConsumer the function to run with the config found on the post message
     */
    private void withConfig(GenericMessageEvent event,
            BiConsumer<Message, NumericScoreConfig> configConsumer) {
        event.getChannel()
            .retrieveMessageById(event.getMessageId())
            .queue(message -> withConfig(message, configConsumer));
    }

    private String findStringEmojiFromScore(NumericScoreConfig config, int score) {
        if (score > 0) {
            score = Math.min(score, config.positiveScoresEmojis().size() - 1);
            return config.positiveScoresEmojis().get(score);
        } else if (score < 0) {
            score = Math.min(-score, config.negativeScoresEmojis().size() - 1);
            return config.negativeScoresEmojis().get(score);
        } else {
            return config.zeroScoreEmoji();
        }
    }

    private RestAction<Integer> calculateScore(NumericScoreConfig config, Message message) {
        EmojiUnion upvoteEmoji = findEmoji(message.getGuild(), config.upvoteEmoji());
        EmojiUnion downvoteEmoji = findEmoji(message.getGuild(), config.downvoteEmoji());
        var upvotesReaction = message.getReaction(upvoteEmoji);
        var downvotesReaction = message.getReaction(downvoteEmoji);

        RestAction<List<User>> retrieveUpvotesUsers = upvotesReaction == null
                ? new CompletedRestAction<>(message.getJDA(), new ArrayList<>())
                : upvotesReaction.retrieveUsers();
        RestAction<List<User>> retrieveDownvotesUsers = downvotesReaction == null
                ? new CompletedRestAction<>(message.getJDA(), new ArrayList<>())
                : downvotesReaction.retrieveUsers();

        return RestAction.allOf(retrieveUpvotesUsers, retrieveDownvotesUsers).map(reactionUsers -> {
            List<User> upvotesUsers = reactionUsers.get(0);
            List<User> downvotesUsers = reactionUsers.get(1);
            int score = 1;
            score += (int) upvotesUsers.stream()
                .filter(u -> filterReaction(message.getAuthor(), u))
                .count();
            score -= (int) downvotesUsers.stream()
                .filter(u -> filterReaction(message.getAuthor(), u))
                .count();
            return score;
        });
    }

    private void updateEmojis(NumericScoreConfig config, Message message) {
        addVoteEmojis(config, message);
        calculateScore(config, message).queue(score -> {
            String stringEmoji = findStringEmojiFromScore(config, score);
            Emoji emoji = findEmoji(message.getGuild(), stringEmoji);
            clearEmojis(config, message, () -> message.addReaction(emoji).queue());
        });
    }

    private boolean filterReaction(User author, User reacted) {
        return !reacted.isBot() && reacted.getIdLong() != author.getIdLong();
    }

    private EmojiUnion findEmoji(Guild guild, String nameOrUnicode) {
        return guild.getEmojisByName(nameOrUnicode, true)
            .stream()
            .findAny()
            .map(e -> (EmojiUnion) e)
            .orElse((EmojiUnion) Emoji.fromUnicode(nameOrUnicode));
    }

    private void clearEmojis(NumericScoreConfig config, Message message, Runnable postAction) {
        List<RestAction<Void>> actions = config.streamAllBlacklistedEmojis()
            .map(e -> (Emoji) findEmoji(message.getGuild(), e))
            .map(e -> {
                MessageReaction reaction = message.getReaction(e);
                return reaction == null ? null : message.clearReactions(e);
            })
            .filter(Objects::nonNull)
            .toList();
        if (actions.isEmpty()) {
            postAction.run();
        } else {
            RestAction.allOf(actions).queue(_ -> postAction.run());
        }
    }

    private void addVoteEmojis(NumericScoreConfig config, Message message) {
        Emoji upvote = findEmoji(message.getGuild(), config.upvoteEmoji());
        Emoji downvote = findEmoji(message.getGuild(), config.downvoteEmoji());
        MessageReaction reactionUpvote = message.getReaction(upvote);
        MessageReaction reactionDownvote = message.getReaction(downvote);
        if (reactionUpvote != null && !reactionUpvote.isSelf()) {
            message.addReaction(upvote).queue();
        }
        if (reactionDownvote != null && !reactionDownvote.isSelf()) {
            message.addReaction(downvote).queue();
        }
    }

    @Override
    public void onChannelCreate(@NotNull ChannelCreateEvent event) {
        withConfig(event.getChannel(), (post, config) -> {
            updateEmojis(config, post);
            addVoteEmojis(config, post);
        });
    }

    /**
     * Handles ADD/REMOVE reaction only
     * 
     * @param event the message event
     */
    @Override
    public void onGenericMessageReaction(@NotNull GenericMessageReactionEvent event) {
        event.retrieveUser().queue(user -> {
            if (!user.isBot()) {
                withConfig(event, (post, config) -> updateEmojis(config, post));
            }
        });
    }
}
