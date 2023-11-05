package org.togetherjava.tjbot.features.moderation.history;

import static org.togetherjava.tjbot.db.generated.Tables.MESSAGE_HISTORY;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;

public class PurgeHistoryCommand extends SlashCommandAdapter {

    private static final String USER_OPTION = "user";
    private static final String REASON_OPTION = "reason";
    private static final String COMMAND_NAME = "purge";
    private final Database database;

    /**
     * Creates an instance of the command.
     *
     * @param database the database to retrieve message records of a target user
     */

    public PurgeHistoryCommand(Database database) {
        super(COMMAND_NAME, "delete user message history upto an hour", CommandVisibility.GUILD);
        getData()
            .addOption(OptionType.USER, USER_OPTION,
                    "user who's messages you want to purge within an hour", true)
            .addOption(OptionType.STRING, REASON_OPTION, "Reason for purging user messages", true);
        this.database = database;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        OptionMapping targetOption =
                Objects.requireNonNull(event.getOption(USER_OPTION), "target is null");
        Member author = Objects.requireNonNull(event.getMember(), "author is null");
        String reason = Objects.requireNonNull(event.getOption(REASON_OPTION).getAsString(),
                "reason is null");

        handleHistory(event, author, reason, targetOption, event.getHook());
    }

    private void handleHistory(SlashCommandInteractionEvent event, Member author, String reason,
            OptionMapping targetOption, InteractionHook hook) {
        Instant now = Instant.now();
        Instant oneHourBack = now.minus(1, ChronoUnit.HOURS);

        User target = targetOption.getAsUser();
        String sourceChannelId = event.getChannel().getId();

        if (!validateHierarchy(author, targetOption)) {
            hook.sendMessage("Target user has a higher role than you").setEphemeral(true).queue();
            return;
        }

        List<String> messageIdsForDeletion = new ArrayList<>();

        database.write(context -> context.selectFrom(MESSAGE_HISTORY)
            .where(MESSAGE_HISTORY.AUTHOR_ID.equal(target.getIdLong())
                .and(MESSAGE_HISTORY.CHANNEL_ID.equal(Long.valueOf(sourceChannelId)))
                .and(MESSAGE_HISTORY.SENT_AT.greaterOrEqual(oneHourBack)))
            .stream()
            .forEach(messageHistoryRecord -> {
                messageIdsForDeletion.add(String.valueOf(messageHistoryRecord.getMessageId()));
                messageHistoryRecord.delete();
            }));

        handleDelete(messageIdsForDeletion, event.getJDA(), event.getChannel(), event.getHook());
    }

    private void handleDelete(List<String> messageIdsForDeletion, JDA jda,
            MessageChannelUnion channel, InteractionHook hook) {

        if (messageIdsForDeletion.isEmpty()) {
            handleEmptyMessageHistory(hook);
            return;
        }

        if (hasSingleElement(messageIdsForDeletion)) {
            String messageId = messageIdsForDeletion.get(0);

            Objects.requireNonNull(jda.getTextChannelById(channel.getId()), "channel was not found")
                .deleteMessageById(messageId)
                .queue();
        }

        channel.purgeMessagesById(messageIdsForDeletion);
        hook.sendMessage("Messages purged from user").setEphemeral(true).queue();
    }

    private boolean hasSingleElement(List<String> messageIdsForDeletion) {
        return messageIdsForDeletion.size() == 1;
    }

    private void handleEmptyMessageHistory(InteractionHook hook) {
        hook.sendMessage("User has no message history in this channel within last one hour")
            .setEphemeral(true)
            .queue();
    }

    private boolean validateHierarchy(Member author, OptionMapping target) {
        int highestRole = 0;
        Role targetUserRole = target.getAsMember().getRoles().get(highestRole);
        Role authorRole = author.getRoles().get(highestRole);

        return targetUserRole.getPosition() >= authorRole.getPosition();
    }
}
