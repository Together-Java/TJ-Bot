package org.togetherjava.tjbot.features.moderation.temp;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.requests.RestAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.features.Routine;
import org.togetherjava.tjbot.features.moderation.ActionRecord;
import org.togetherjava.tjbot.features.moderation.ModerationAction;
import org.togetherjava.tjbot.features.moderation.ModerationActionsStore;
import org.togetherjava.tjbot.features.moderation.audit.AuditCommand;
import org.togetherjava.tjbot.logging.LogMarkers;

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
 * {@link org.togetherjava.tjbot.features.moderation.UnbanCommand} and {@link AuditCommand}.
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
    public TemporaryModerationRoutine(JDA jda, ModerationActionsStore actionsStore, Config config) {
        this.actionsStore = actionsStore;
        this.jda = jda;

        typeToRevocableAction = Stream
            .of(new TemporaryBanAction(), new TemporaryMuteAction(config),
                    new TemporaryQuarantineAction(config))
            .collect(
                    Collectors.toMap(RevocableModerationAction::getApplyType, Function.identity()));
    }

    @Override
    public void runRoutine(JDA jda) {
        checkExpiredActions();
    }

    @Override
    public Schedule createSchedule() {
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

    private void processGroupedActions(RevocationGroupIdentifier groupIdentifier) {
        // Do not revoke an action which was overwritten by a still effective action that was issued
        // afterwards
        // For example if a user was perm-banned after being temp-banned
        ActionRecord lastApplyAction = actionsStore
            .findLastActionAgainstTargetByType(groupIdentifier.guildId, groupIdentifier.targetId,
                    groupIdentifier.type)
            .orElseThrow();
        if (lastApplyAction.isEffective()) {
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
                    && lastRevokeAction.isEffective()) {
                return;
            }
        }

        revokeAction(groupIdentifier);
    }

    private void revokeAction(RevocationGroupIdentifier groupIdentifier) {
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

    private RestAction<Void> executeRevocation(Guild guild, User target,
            ModerationAction actionType) {
        logger.info(LogMarkers.SENSITIVE, "Revoked temporary action {} against user '{}' ({}).",
                actionType, target.getName(), target.getId());
        RevocableModerationAction action = getRevocableActionByType(actionType);

        String reason = "Automatic revocation of temporary action.";
        actionsStore.addAction(guild.getIdLong(), jda.getSelfUser().getIdLong(), target.getIdLong(),
                action.getRevokeType(), null, reason);

        return action.revokeAction(guild, target, reason);
    }

    private void handleFailure(Throwable failure, RevocationGroupIdentifier groupIdentifier) {
        if (getRevocableActionByType(groupIdentifier.type).handleRevokeFailure(failure,
                groupIdentifier.targetId) == RevocableModerationAction.FailureIdentification.KNOWN) {
            return;
        }

        logger.warn(LogMarkers.SENSITIVE,
                "Attempted to revoke a temporary moderation action for user '{}' but something unexpected went wrong.",
                groupIdentifier.targetId, failure);
    }

    private RevocableModerationAction getRevocableActionByType(ModerationAction type) {
        return Objects.requireNonNull(typeToRevocableAction.get(type),
                "Action type is not revocable: " + type);
    }

    private record RevocationGroupIdentifier(long guildId, long targetId, ModerationAction type) {
        static RevocationGroupIdentifier of(ActionRecord actionRecord) {
            return new RevocationGroupIdentifier(actionRecord.guildId(), actionRecord.targetId(),
                    actionRecord.actionType());
        }
    }
}
