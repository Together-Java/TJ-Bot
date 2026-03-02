package org.togetherjava.tjbot.features.analytics;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.CommandUsage;

import java.time.Instant;

/**
 * Service for tracking and recording command usage analytics.
 * <p>
 * This service records every command execution along with its success/failure status, allowing for
 * analysis of command usage patterns, popular commands, error rates, and activity over time.
 * <p>
 * Commands should call {@link #recordCommandExecution(long, String, long, boolean, String)} after
 * execution to track their usage.
 */
public final class AnalyticsService {
    private static final Logger logger = LoggerFactory.getLogger(AnalyticsService.class);

    private final Database database;

    /**
     * Creates a new instance.
     *
     * @param database the database to use for storing and retrieving analytics data
     */
    public AnalyticsService(Database database) {
        this.database = database;
    }

    /**
     * Records a command execution with success/failure status.
     * <p>
     * This method should be called by commands after they complete execution to track usage
     * patterns and error rates.
     *
     * @param channelId the channel ID where the command was executed
     * @param commandName the name of the command that was executed
     * @param userId the ID of the user who executed the command
     * @param success whether the command executed successfully
     * @param errorMessage optional error message if the command failed (null if successful)
     */
    public void recordCommandExecution(long channelId, String commandName, long userId,
            boolean success, @Nullable String errorMessage) {

        database.write(context -> context.newRecord(CommandUsage.COMMAND_USAGE)
            .setChannelId(channelId)
            .setCommandName(commandName)
            .setUserId(userId)
            .setExecutedAt(Instant.now())
            .setSuccess(success)
            .setErrorMessage(errorMessage)
            .insert());

        if (!success && errorMessage != null) {
            logger.warn("Command '{}' failed on channel {} with error: {}", commandName, channelId,
                    errorMessage);
        }
    }

    /**
     * Records a successful command execution.
     *
     * @param channelId the channel ID where the command was executed
     * @param commandName the name of the command that was executed
     * @param userId the ID of the user who executed the command
     */
    public void recordCommandSuccess(long channelId, String commandName, long userId) {
        recordCommandExecution(channelId, commandName, userId, true, null);
    }

    /**
     * Records a failed command execution.
     *
     * @param channelId the channel ID where the command was executed
     * @param commandName the name of the command that was executed
     * @param userId the ID of the user who executed the command
     * @param errorMessage a description of what went wrong
     */
    public void recordCommandFailure(long channelId, String commandName, long userId,
            String errorMessage) {
        recordCommandExecution(channelId, commandName, userId, false, errorMessage);
    }
}
