package org.togetherjava.tjbot.features.leaderboard;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;
import org.togetherjava.tjbot.features.tophelper.TopHelpersService;
import org.togetherjava.tjbot.features.utils.Colors;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Implements the {@code /leaderboard} slash command, which displays the all-time top helpers
 * leaderboard by reading the hall-of-fame channel history.
 */
public final class LeaderboardCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(LeaderboardCommand.class);

    private static final String COMMAND_NAME = "leaderboard";
    private static final int TOP_LIMIT = 10;
    private static final int HISTORY_LIMIT = 500;

    private static final String MEDAL_FIRST = "🥇";
    private static final String MEDAL_SECOND = "🥈";
    private static final String MEDAL_THIRD = "🥉";
    private static final String BULLET = "▸";

    private final Config config;

    private final Map<Long, Map<Long, Integer>> winsByGuild = new ConcurrentHashMap<>();
    private final Map<Long, Instant> lastFetchedPerGuild = new ConcurrentHashMap<>();

    public LeaderboardCommand(Config config) {
        super(COMMAND_NAME, "Show the all-time top helpers leaderboard", CommandVisibility.GUILD);
        this.config = config;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        Guild guild = Objects.requireNonNull(event.getGuild());

        event.deferReply().queue();

        Pattern channelPattern =
                Pattern.compile(config.getTopHelpers().getAnnouncementChannelPattern());
        TextChannel hallOfFame = guild.getTextChannels()
            .stream()
            .filter(channel -> channelPattern.matcher(channel.getName()).find())
            .findFirst()
            .orElse(null);

        if (hallOfFame == null) {
            event.getHook()
                .editOriginal(
                        "Could not find channel matching '%s'.".formatted(channelPattern.pattern()))
                .queue();
            return;
        }

        long guildId = guild.getIdLong();
        InteractionHook hook = event.getHook();

        Map<Long, Integer> cachedWins =
                winsByGuild.computeIfAbsent(guildId, _ -> new ConcurrentHashMap<>());
        Instant lastFetched = lastFetchedPerGuild.get(guildId);

        fetchNewMessages(hallOfFame, lastFetched).thenAccept(newMessages -> {
            if (!newMessages.isEmpty()) {
                countWinsInto(newMessages, cachedWins);
                lastFetchedPerGuild.put(guildId,
                        newMessages.getFirst().getTimeCreated().toInstant());
            }
            sendLeaderboard(guild, cachedWins, hook);
        }).exceptionally(error -> {
            logger.error("Failed to read hall of fame channel", error);
            hook.editOriginal("Failed to read the hall of fame channel.").queue();
            return null;
        });
    }

    private static CompletableFuture<List<Message>> fetchNewMessages(TextChannel channel,
            Instant lastFetched) {
        if (lastFetched == null) {
            return channel.getIterableHistory().takeAsync(HISTORY_LIMIT);
        }
        return channel.getIterableHistory()
            .takeWhileAsync(HISTORY_LIMIT,
                    msg -> msg.getTimeCreated().toInstant().isAfter(lastFetched));
    }

    private void sendLeaderboard(Guild guild, Map<Long, Integer> wins, InteractionHook hook) {
        List<Map.Entry<Long, Integer>> sorted = wins.entrySet()
            .stream()
            .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
            .limit(TOP_LIMIT)
            .toList();

        if (sorted.isEmpty()) {
            hook.editOriginal("No top helper data found.").queue();
            return;
        }

        List<Long> ids = sorted.stream().map(Map.Entry::getKey).toList();

        guild.retrieveMembersByIds(ids).onSuccess(members -> {
            Map<Long, Member> memberById = TopHelpersService.mapUserIdToMember(members);

            StringJoiner description = new StringJoiner("\n");
            for (int i = 0; i < sorted.size(); i++) {
                Map.Entry<Long, Integer> entry = sorted.get(i);
                Member member = memberById.get(entry.getKey());
                String name = TopHelpersService.getUsernameDisplay(member);
                int winCount = entry.getValue();
                description.add("%s **%s** — %d month%s".formatted(rankPrefix(i), name, winCount,
                        winCount == 1 ? "" : "s"));
            }

            EmbedBuilder embed = new EmbedBuilder().setTitle("🏆 Top Helpers — Hall of Fame")
                .setDescription(description.toString())
                .setColor(Colors.SUCCESS_COLOR)
                .setFooter("Times awarded Top Helper");

            hook.editOriginalEmbeds(embed.build()).queue();

        }).onError(error -> {
            logger.error("Failed to retrieve members for leaderboard", error);
            hook.editOriginal("Failed to load member data, please try again.").queue();
        });
    }

    private static void countWinsInto(List<Message> messages, Map<Long, Integer> wins) {
        for (Message message : messages) {
            String content = message.getContentRaw();
            if (!content.toLowerCase().contains("top helper")) {
                continue;
            }
            for (User user : message.getMentions().getUsers()) {
                wins.merge(user.getIdLong(), 1, Integer::sum);
            }
        }
    }

    private static String rankPrefix(int zeroBasedIndex) {
        return switch (zeroBasedIndex) {
            case 0 -> MEDAL_FIRST;
            case 1 -> MEDAL_SECOND;
            case 2 -> MEDAL_THIRD;
            default -> BULLET + " #" + (zeroBasedIndex + 1);
        };
    }
}
