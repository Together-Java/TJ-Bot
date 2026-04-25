package org.togetherjava.tjbot.features.leaderboard;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;
import org.togetherjava.tjbot.features.tophelper.TopHelpersService;
import org.togetherjava.tjbot.features.utils.Colors;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
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

    public LeaderboardCommand(Config config) {
        super(COMMAND_NAME, "Show the all-time top helpers leaderboard", CommandVisibility.GUILD);
        this.config = config;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("This command can only be used inside a server.")
                .setEphemeral(true)
                .queue();
            return;
        }

        event.deferReply().queue();

        Pattern channelPattern =
                Pattern.compile(config.getTopHelpers().getAnnouncementChannelPattern());
        TextChannel hallOfFame = guild.getTextChannels()
            .stream()
            .filter(channel -> channelPattern.matcher(channel.getName()).find())
            .findFirst()
            .orElse(null);

        if (hallOfFame == null) {
            event.getHook().editOriginal("Could not find the hall of fame channel.").queue();
            return;
        }

        hallOfFame.getIterableHistory().takeAsync(HISTORY_LIMIT).thenAccept(messages -> {
            Map<Long, Integer> winsByUser = countWins(messages);

            List<Map.Entry<Long, Integer>> sorted = winsByUser.entrySet()
                .stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue(Comparator.reverseOrder()))
                .limit(TOP_LIMIT)
                .toList();

            if (sorted.isEmpty()) {
                event.getHook().editOriginal("No top helper data found.").queue();
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
                    int wins = entry.getValue();
                    description.add("%s **%s** — %d month%s".formatted(rankPrefix(i), name, wins,
                            wins == 1 ? "" : "s"));
                }

                EmbedBuilder embed = new EmbedBuilder().setTitle("🏆 Top Helpers — Hall of Fame")
                    .setDescription(description.toString())
                    .setColor(Colors.SUCCESS_COLOR)
                    .setFooter("Times awarded Top Helper");

                event.getHook().editOriginalEmbeds(embed.build()).queue();

            }).onError(error -> {
                logger.error("Failed to retrieve members for leaderboard", error);
                event.getHook()
                    .editOriginal("Failed to load member data, please try again.")
                    .queue();
            });

        }).exceptionally(error -> {
            logger.error("Failed to read hall of fame channel", error);
            event.getHook().editOriginal("Failed to read the hall of fame channel.").queue();
            return null;
        });
    }

    private static Map<Long, Integer> countWins(List<Message> messages) {
        Map<Long, Integer> wins = new HashMap<>();
        for (Message message : messages) {
            String content = message.getContentRaw();
            if (!content.toLowerCase().contains("top helper")) {
                continue;
            }
            for (User user : message.getMentions().getUsers()) {
                wins.merge(user.getIdLong(), 1, Integer::sum);
            }
        }
        return wins;
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
