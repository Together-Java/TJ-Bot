package org.togetherjava.tjbot.features.moderation.history;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.DatabaseException;
import org.togetherjava.tjbot.db.generated.tables.records.MessageHistoryRecord;
import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.togetherjava.tjbot.db.generated.Tables.MESSAGE_HISTORY;

public class PurgeHistoryCommand extends SlashCommandAdapter {

    private static final Logger logger = LoggerFactory.getLogger(PurgeHistoryCommand.class);
    private static final String USER_OPTION = "user";
    private static final String REASON_OPTION = "reason";
    private static final String COMMAND_NAME = "purge-in-channel";
    private static final int PURGE_MESSAGES_AFTER_LIMIT_HOURS = 1;
    private final Database database;

    /**
     * Creates an instance of the command.
     *
     * @param database the database to retrieve message records of a target user
     */

    public PurgeHistoryCommand(Database database) {
        super(COMMAND_NAME, "purge message history of user in same channel",
                CommandVisibility.GUILD);

        String optionUserDescription = "user who's message history you want to purge within %s hr"
            .formatted(PURGE_MESSAGES_AFTER_LIMIT_HOURS);

        getData().addOption(OptionType.USER, USER_OPTION, optionUserDescription, true)
            .addOption(OptionType.STRING, REASON_OPTION,
                    "reason for purging user's message history", true);
        this.database = database;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();

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
        Instant purgeMessagesAfter = now.minus(PURGE_MESSAGES_AFTER_LIMIT_HOURS, ChronoUnit.HOURS);

        User targetUser = targetOption.getAsUser();
        String sourceChannelId = event.getChannel().getId();

        if (!validateHierarchy(author, targetOption)) {
            hook.sendMessage("Cannot purge history of user with a higher role than you").queue();
            return;
        }

        List<String> messageIdsForDeletion = new ArrayList<>();

        Stream<MessageHistoryRecord> data =
                database.writeAndProvide(context -> context.selectFrom(MESSAGE_HISTORY)
                    .where(MESSAGE_HISTORY.AUTHOR_ID.equal(targetUser.getIdLong())
                        .and(MESSAGE_HISTORY.CHANNEL_ID.equal(Long.valueOf(sourceChannelId)))
                        .and(MESSAGE_HISTORY.SENT_AT.greaterOrEqual(purgeMessagesAfter)))
                    .stream());

        try (data) {
            data.forEach(messageHistoryRecord -> {
                String messageId = String.valueOf(messageHistoryRecord.getMessageId());
                messageIdsForDeletion.add(messageId);
                messageHistoryRecord.delete();
            });
        } catch (DatabaseException exception) {
            logger.error("unknown error during fetching message history records for {} command",
                    COMMAND_NAME, exception);
        }

        handleDelete(messageIdsForDeletion, event.getJDA(), event.getChannel(), event.getHook(),
                targetUser, reason, author);
    }

    private void handleDelete(List<String> messageIdsForDeletion, JDA jda,
            MessageChannelUnion channel, InteractionHook hook, User targetUser, String reason,
            Member author) {

        if (messageIdsForDeletion.isEmpty()) {
            handleEmptyMessageHistory(hook, targetUser);
            return;
        }

        if (hasSingleElement(messageIdsForDeletion)) {
            String messageId = messageIdsForDeletion.get(0);
            String messageForMod =
                    "message purged from user %s in this channel.".formatted(targetUser.getName());
            Objects.requireNonNull(jda.getTextChannelById(channel.getId()), "channel was not found")
                .deleteMessageById(messageId)
                .queue();

            hook.sendMessage(messageForMod).queue();
            return;
        }

        int noOfMessagePurged = messageIdsForDeletion.size();
        channel.purgeMessagesById(messageIdsForDeletion);

        String messageForMod = "%s messages purged from user %s in this channel."
            .formatted(noOfMessagePurged, targetUser.getName());
        hook.sendMessage(messageForMod)
            .queue(onSuccess -> logger.info("{} purged messages from {} because: {}",
                    author.getUser(), targetUser, reason));
    }

    private boolean hasSingleElement(List<String> messageIdsForDeletion) {
        return messageIdsForDeletion.size() == 1;
    }

    private void handleEmptyMessageHistory(InteractionHook hook, User targetUser) {
        String messageForMod = "%s has no message history in this channel within last %s hr."
            .formatted(targetUser.getName(), PURGE_MESSAGES_AFTER_LIMIT_HOURS);

        hook.sendMessage(messageForMod).queue();
    }

    private boolean validateHierarchy(Member author, OptionMapping target) {
        int highestRole = 0;
        Role targetUserRole = Objects
            .requireNonNull(target.getAsMember(), "target user for purge command is not a member")
            .getRoles()
            .get(highestRole);
        Role authorRole = author.getRoles().get(highestRole);

        return targetUserRole.getPosition() >= authorRole.getPosition();
    }
}
