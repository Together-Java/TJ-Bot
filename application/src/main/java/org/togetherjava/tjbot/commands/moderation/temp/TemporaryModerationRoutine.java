package org.togetherjava.tjbot.commands.moderation.temp;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.RestAction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.Routine;
import org.togetherjava.tjbot.commands.moderation.ActionRecord;
import org.togetherjava.tjbot.commands.moderation.ModerationAction;
import org.togetherjava.tjbot.commands.moderation.ModerationActionsStore;
import org.togetherjava.tjbot.config.Config;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Routine that revokes temporary moderation actions, such as temporary bans, as listed by
 * {@link ModerationActionsStore}.
 * <p>
 * Revoked actions are compatible with {@link ModerationActionsStore} and commands such as
 * {@link org.togetherjava.tjbot.commands.moderation.UnbanCommand} and
 * {@link org.togetherjava.tjbot.commands.moderation.AuditCommand}.
 */
public final class TemporaryModerationRoutine implements Routine {
    private static final Logger logger = LoggerFactory.getLogger(TemporaryModerationRoutine.class);

    private final ModerationActionsStore actionsStore;
    private final JDA jda;
    private final Map<ModerationAction, RevocableModerationAction> typeToRevocableAction;

    /**
     * Creates a new instance.
     *
     * @param jda the JDA instance to use to send messages and retrieve information
     * @param actionsStore the store used to retrieve temporary moderation actions
     * @param config the config to use for this
     */
    public TemporaryModerationRoutine(@NotNull JDA jda,
            @NotNull ModerationActionsStore actionsStore, @NotNull Config config) {
        this.actionsStore = actionsStore;
        this.jda = jda;

        typeToRevocableAction = Stream
            .of(new TemporaryBanAction(), new TemporaryMuteAction(config),
                    new TemporaryQuarantineAction(config))
            .collect(
                    Collectors.toMap(RevocableModerationAction::getApplyType, Function.identity()));
    }

    @Override
    public void runRoutine(@NotNull JDA jda) {
        checkExpiredActions();
    }

    @Override
    public @NotNull Schedule createSchedule() {
        return new Schedule(ScheduleMode.FIXED_DELAY, 5, 5, TimeUnit.MINUTES);
    }

    private void checkExpiredActions() {
        logger.debug("Checking expired temporary moderation actions to revoke...");

        actionsStore.getExpiredActionsAscending()
            .stream()
            .filter(action -> typeToRevocableAction.containsKey(action.actionType()))
            .map(RevocationGroupIdentifier::of)
            .collect(Collectors.toSet())
            .forEach(this::processGroupedActions);

        logger.debug("Finished checking expired temporary moderation actions to revoke.");
    }

    private void processGroupedActions(@NotNull RevocationGroupIdentifier groupIdentifier) {
        // Do not revoke an action which was overwritten by a permanent action that was issued
        // afterwards
        // For example if a user was perm-banned after being temp-banned
        ActionRecord lastApplyAction = actionsStore
            .findLastActionAgainstTargetByType(groupIdentifier.guildId, groupIdentifier.targetId,
                    groupIdentifier.type)
            .orElseThrow();
        if (lastApplyAction.actionExpiresAt() == null) {
            return;
        }

        // Do not revoke an action which was already revoked by another action issued afterwards
        // For example if a user was unbanned manually after being temp-banned,
        // but also if the system automatically revoked a temp-ban already itself
        ModerationAction revokeActionType =
                getRevocableActionByType(groupIdentifier.type).getRevokeType();
        Optional<ActionRecord> lastRevokeActionOpt = actionsStore.findLastActionAgainstTargetByType(
                groupIdentifier.guildId, groupIdentifier.targetId, revokeActionType);
        if (lastRevokeActionOpt.isPresent()) {
            ActionRecord lastRevokeAction = lastRevokeActionOpt.orElseThrow();
            if (lastRevokeAction.issuedAt().isAfter(lastApplyAction.issuedAt())
                    && (lastRevokeAction.actionExpiresAt() == null
                            || lastRevokeAction.actionExpiresAt().isAfter(Instant.now()))) {
                return;
            }
        }

        revokeAction(groupIdentifier);
    }

    private void revokeAction(@NotNull RevocationGroupIdentifier groupIdentifier) {
        Guild guild = jda.getGuildById(groupIdentifier.guildId);
        if (guild == null) {
            logger.debug(
                    "Attempted to revoke a temporary moderation action but the bot is not connected to the guild '{}' anymore, skipping revoking.",
                    groupIdentifier.guildId);
            return;
        }

        jda.retrieveUserById(groupIdentifier.targetId)
            .flatMap(target -> executeRevocation(guild, target, groupIdentifier.type))
            .queue(result -> {
            }, failure -> handleFailure(failure, groupIdentifier));
    }

    private void handleFailure(@NotNull Throwable failure,
            @NotNull RevocationGroupIdentifier groupIdentifier) {
        if (failure instanceof ErrorResponseException errorResponseException
                && !getRevocableActionByType(groupIdentifier.type).handleRevokeFailure(
                        errorResponseException.getErrorResponse(), groupIdentifier.targetId)) {

            return;
        }

        logger.warn(
                "Attempted to revoke a temporary moderation action for user '{}' but something unexpected went wrong.",
                groupIdentifier.targetId, failure);
    }

    private @NotNull RestAction<Void> executeRevocation(@NotNull Guild guild, @NotNull User target,
            @NotNull ModerationAction actionType) {
        logger.info("Revoked temporary action {} against user '{}' ({}).", actionType,
                target.getAsTag(), target.getId());
        RevocableModerationAction action = getRevocableActionByType(actionType);

        String reason = "Automatic revocation of temporary action.";
        actionsStore.addAction(guild.getIdLong(), jda.getSelfUser().getIdLong(), target.getIdLong(),
                action.getRevokeType(), null, reason);

        return action.revokeAction(guild, target, reason);
    }

    private @NotNull RevocableModerationAction getRevocableActionByType(
            @NotNull ModerationAction type) {
        return Objects.requireNonNull(typeToRevocableAction.get(type),
                "Action type is not revocable: " + type);
    }

    private record RevocationGroupIdentifier(long guildId, long targetId,
            @NotNull ModerationAction type) {
        static RevocationGroupIdentifier of(@NotNull ActionRecord actionRecord) {
            return new RevocationGroupIdentifier(actionRecord.guildId(), actionRecord.targetId(),
                    actionRecord.actionType());
        }
    }
}
