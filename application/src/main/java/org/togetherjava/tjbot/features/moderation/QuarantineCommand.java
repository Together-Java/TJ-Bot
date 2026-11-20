package org.togetherjava.tjbot.features.moderation;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.requests.RestAction;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;
import org.togetherjava.tjbot.features.componentids.Lifespan;
import org.togetherjava.tjbot.features.utils.Guilds;
import org.togetherjava.tjbot.features.utils.MessageUtils;

import javax.annotation.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * This command can quarantine users. Quarantining can also be paired with a reason. The command
 * will also try to DM the user to inform them about the action and the reason.
 * <p>
 * The command fails if the user triggering it is lacking permissions to either quarantine other
 * users or to quarantine the specific given user (for example a moderator attempting to quarantine
 * an admin).
 */
public final class QuarantineCommand extends SlashCommandAdapter {
    private static final String TARGET_OPTION = "user";
    private static final String REASON_OPTION = "reason";
    private static final String COMMAND_NAME = "quarantine";
    private static final String ACTION_VERB = "quarantine";
    private static final String ACTION_TITLE = "Quarantine";

    private static final String SELF_UNQUARANTINE_REASON =
            "User regained control and unquarantined themselves.";

    private final ModerationActionsStore actionsStore;
    private final Config config;

    /**
     * Constructs an instance.
     *
     * @param actionsStore used to store actions issued by this command
     * @param config the config to use for this
     */
    public QuarantineCommand(ModerationActionsStore actionsStore, Config config) {
        super(COMMAND_NAME,
                "Puts the given user under quarantine. They can not interact with anyone anymore then.",
                CommandVisibility.GUILD);

        getData()
            .addOption(OptionType.USER, TARGET_OPTION, "The user who you want to quarantine", true)
            .addOption(OptionType.STRING, REASON_OPTION, "Why the user should be quarantined",
                    true);

        this.config = config;
        this.actionsStore = Objects.requireNonNull(actionsStore);
    }

    private static void handleAlreadyQuarantinedTarget(IReplyCallback event) {
        event.reply("The user is already quarantined.").setEphemeral(true).queue();
    }

    private static RestAction<Boolean> sendDm(User target, String reason, Guild guild) {
        String description =
                """
                        Hey there, sorry to tell you but unfortunately you have been put under quarantine.
                        This means you can no longer interact with anyone in the server until you have been unquarantined again.""";

        return ModerationUtils.sendModActionDm(
                ModerationUtils.getModActionEmbed(guild, ACTION_TITLE, description, reason, true),
                target);
    }

    private static MessageEmbed sendFeedback(boolean hasSentDm, Member target, Member author,
            String reason) {
        String dmNoticeText = "";
        if (!hasSentDm) {
            dmNoticeText = "\n(Unable to send them a DM.)";
        }
        return ModerationUtils.createActionResponse(author.getUser(), ModerationAction.QUARANTINE,
                target.getUser(), dmNoticeText, reason);
    }

    private void quarantineUserFlow(Member target, Member author, String reason, Guild guild,
            SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        sendDm(target.getUser(), reason, guild)
            .flatMap(hasSentDm -> ModerationUtils
                .quarantine(config, guild, target, reason,
                        () -> generateComponentId(Lifespan.PERMANENT, target.getId()))
                .map(_ -> hasSentDm))
            .map(hasSentDm -> sendFeedback(hasSentDm, target, author, reason))
            .flatMap(event.getHook()::sendMessageEmbeds)
            .queue();
    }

    private boolean handleChecks(Member bot, Member author, @Nullable Member target,
            CharSequence reason, Guild guild, IReplyCallback event) {
        if (!ModerationUtils.handleRoleChangeChecks(
                ModerationUtils.getQuarantinedRole(guild, config).orElse(null), ACTION_VERB, target,
                bot, author, guild, reason, event)) {
            return false;
        }

        if (Objects.requireNonNull(target)
            .getRoles()
            .stream()
            .map(Role::getName)
            .anyMatch(ModerationUtils.getIsQuarantinedRolePredicate(config))) {
            handleAlreadyQuarantinedTarget(event);
            return false;
        }

        return true;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        Member target = event.getOption(TARGET_OPTION).getAsMember();
        Member author = event.getMember();
        String reason = event.getOption(REASON_OPTION).getAsString();

        Guild guild = Objects.requireNonNull(event.getGuild());
        Member bot = guild.getSelfMember();

        if (!handleChecks(bot, author, target, reason, guild, event)) {
            return;
        }

        quarantineUserFlow(Objects.requireNonNull(target), author, reason, guild, event);
    }

    @Override
    public void onButtonClick(ButtonInteractionEvent event, List<String> args) {

        Guild guild = Objects.requireNonNull(event.getGuild());
        Member member = Objects.requireNonNull(event.getMember());

        if (!Guilds.hasMemberRole(member, ModerationUtils.getIsQuarantinedRolePredicate(config))) {
            event.reply("You are not quarantined  anymore!").setEphemeral(true).queue();

            MessageUtils.disableButtons(event.getMessage());
            return;
        }

        event.deferEdit().queue();

        guild
            .removeRoleFromMember(member,
                    ModerationUtils.getQuarantinedRole(guild, config).orElseThrow())
            .reason(SELF_UNQUARANTINE_REASON)
            .queue(_ -> {
                actionsStore.addAction(guild.getIdLong(), guild.getSelfMember().getIdLong(),
                        event.getUser().getIdLong(), ModerationAction.UNQUARANTINE, null,
                        SELF_UNQUARANTINE_REASON);

                MessageUtils.disableButtons(event.getMessage());
            }, _ -> event.getHook()
                .sendMessage(
                        "Something went wrong while trying to unquarantine your account, try to reach via /modmail.")
                .setEphemeral(true)
                .queue());
    }
}
