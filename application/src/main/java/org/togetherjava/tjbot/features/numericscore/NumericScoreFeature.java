package org.togetherjava.tjbot.features.numericscore;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.message.react.*;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.jooq.Result;

import org.togetherjava.tjbot.config.NumericScoreConfig;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.VoteScore;
import org.togetherjava.tjbot.db.generated.tables.records.VoteScoreRecord;
import org.togetherjava.tjbot.features.EventReceiver;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class NumericScoreFeature extends ListenerAdapter implements EventReceiver {

    private final Database database;
    private final List<NumericScoreConfig> numericScoreConfig;
    /**
     * Map<ForumID, Map<Emoji, Score>>
     */
    private final Map<Long, Map<String, Integer>> reverseEmojiScoreConfig;

    public NumericScoreFeature(Database database, List<NumericScoreConfig> numericScoreConfig) {
        this.database = database;
        this.numericScoreConfig = numericScoreConfig;
        this.reverseEmojiScoreConfig = this.numericScoreConfig.stream()
            .collect(Collectors.toMap(NumericScoreConfig::forumId, c -> {
                Map<String, Integer> map = new HashMap<>();
                map.put(c.zeroScoreEmoji(), 0);
                for (int i = 0; i < c.positiveScoresEmojis().size(); i++) {
                    map.put(c.positiveScoresEmojis().get(i), i + 1);
                }
                for (int i = 0; i < c.negativeScoresEmojis().size(); i++) {
                    map.put(c.negativeScoresEmojis().get(i), -i - 1);
                }
                return map;
            }));
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

    private String findStringEmojiFromScore(NumericScoreConfig config, int score) {
        if (score > 0) {
            score = Math.min(score, config.positiveScoresEmojis().size());
            return config.positiveScoresEmojis().get(score - 1);
        } else if (score < 0) {
            score = Math.min(-score, config.negativeScoresEmojis().size());
            return config.negativeScoresEmojis().get(score - 1);
        } else {
            return config.zeroScoreEmoji();
        }
    }

    private int calculateScore(Message message) {
        Result<VoteScoreRecord> votes = database
            .read(ctx -> ctx.selectFrom(VoteScore.VOTE_SCORE)
                .where(VoteScore.VOTE_SCORE.MESSAGE_ID.eq(message.getIdLong())))
            .fetch();
        int upvotes = (int) votes.stream().filter(v -> v.getVote() == 1).count();
        int downvotes = (int) votes.stream().filter(v -> v.getVote() == -1).count();

        return 1 + upvotes - downvotes;
    }

    private void updateEmojis(NumericScoreConfig config, Message message) {
        Map<String, Integer> emojiToScoreMap = reverseEmojiScoreConfig.get(config.forumId());
        int score = calculateScore(message);

        if (message.getReactions()
            .stream()
            .map(e -> e.getEmoji().getName())
            .anyMatch(e -> Objects.equals(emojiToScoreMap.get(e), score))) {
            // If the score emoji is the same
            return;
        }

        Emoji scoreEmoji = findEmoji(message.getGuild(), findStringEmojiFromScore(config, score));
        Emoji upvote = findEmoji(message.getGuild(), config.upvoteEmoji());
        Emoji downvote = findEmoji(message.getGuild(), config.downvoteEmoji());

        message.clearReactions()
            .flatMap(_ -> message.addReaction(scoreEmoji))
            .flatMap(_ -> message.addReaction(upvote))
            .flatMap(_ -> message.addReaction(downvote))
            .queue();
    }

    private EmojiUnion findEmoji(Guild guild, String nameOrUnicode) {
        return guild.getEmojisByName(nameOrUnicode, true)
            .stream()
            .findAny()
            .map(e -> (EmojiUnion) e)
            .orElse((EmojiUnion) Emoji.fromUnicode(nameOrUnicode));
    }

    @Override
    public void onChannelCreate(@NotNull ChannelCreateEvent event) {
        withConfig(event.getChannel(), (post, config) -> updateEmojis(config, post));
    }

    @Override
    public void onMessageReactionAdd(@NotNull MessageReactionAddEvent event) {
        withConfig(event.getChannel(), (post, config) -> {
            long user = Objects.requireNonNull(event.getUser()).getIdLong();
            if (user == event.getJDA().getSelfUser().getIdLong() || user == post.getIdLong()) {
                return;
            }

            String emoji = event.getEmoji().getName();
            int vote = 0;
            if (emoji.equals(config.upvoteEmoji())) {
                vote = 1;
            }
            if (emoji.equals(config.downvoteEmoji())) {
                vote = -1;
            }

            if (vote == 0) {
                database.write(ctx -> ctx.deleteFrom(VoteScore.VOTE_SCORE)
                    .where(VoteScore.VOTE_SCORE.MESSAGE_ID.eq(event.getMessageIdLong()))
                    .and(VoteScore.VOTE_SCORE.USER_ID.eq(user))
                    .execute());
            } else {
                int vote2 = vote;
                database.write(ctx -> ctx.insertInto(VoteScore.VOTE_SCORE)
                    .set(VoteScore.VOTE_SCORE.MESSAGE_ID, event.getMessageIdLong())
                    .set(VoteScore.VOTE_SCORE.USER_ID, user)
                    .set(VoteScore.VOTE_SCORE.VOTE, vote2)
                    .onConflict(VoteScore.VOTE_SCORE.MESSAGE_ID, VoteScore.VOTE_SCORE.USER_ID)
                    .doUpdate()
                    .set(VoteScore.VOTE_SCORE.VOTE, vote2)
                    .execute());
            }
            updateEmojis(config, post);
        });
    }
}
