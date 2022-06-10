package org.togetherjava.tjbot.commands.help;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.RestAction;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;
import org.togetherjava.tjbot.config.Config;

import java.util.Optional;

/**
 * Implements the {@code /change-help-category} command, which is able to change the category of a
 * help thread.
 * <p>
 * This is either used for threads that do not have categories yet (as created by
 * {@link ImplicitAskListener}), or simply to adjust the category afterwards.
 * <p>
 * Changing the category will invite all helpers interested into the corresponding category to the
 * question thread.
 */
public final class ChangeHelpCategoryCommand extends SlashCommandAdapter {
    private static final String CATEGORY_OPTION = "category";

    private final HelpSystemHelper helper;

    /**
     * Creates a new instance.
     *
     * @param config the config to use
     * @param helper the helper to use
     */
    public ChangeHelpCategoryCommand(@NotNull Config config, @NotNull HelpSystemHelper helper) {
        super("change-help-category", "changes the category of a help thread",
                SlashCommandVisibility.GUILD);

        OptionData category = new OptionData(OptionType.STRING, CATEGORY_OPTION,
                "select what describes the question the best", true);
        config.getHelpSystem()
            .getCategories()
            .forEach(categoryText -> category.addChoice(categoryText, categoryText));

        getData().addOptions(category);

        this.helper = helper;
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandInteractionEvent event) {
        String category = event.getOption(CATEGORY_OPTION).getAsString();

        if (!helper.handleIsHelpThread(event)) {
            return;
        }

        ThreadChannel helpThread = event.getThreadChannel();
        if (helpThread.isArchived()) {
            event.reply("This thread is already closed.").setEphemeral(true).queue();
            return;
        }

        event.deferReply().queue();

        helper.renameChannelToCategoryTitle(helpThread, category)
            .flatMap(any -> sendCategoryChangedMessage(helpThread.getGuild(), event.getHook(),
                    helpThread, category))
            .queue();
    }

    private @NotNull RestAction<Message> sendCategoryChangedMessage(@NotNull Guild guild,
            @NotNull InteractionHook hook, @NotNull ThreadChannel helpThread,
            @NotNull String category) {
        String changedContent = "Changed the category to **%s**.".formatted(category);
        var action = hook.editOriginal(changedContent);

        Optional<Role> helperRole = helper.handleFindRoleForCategory(category, guild);
        if (helperRole.isEmpty()) {
            return action;
        }

        // We want to invite all members of a role, but without hard-pinging them. However,
        // manually inviting them is cumbersome and can hit rate limits.
        // Instead, we abuse the fact that a role-ping through an edit will not hard-ping users,
        // but still invite them to a thread.
        String headsUpPattern = "%splease have a look, thanks.";
        String headsUpWithoutRole = headsUpPattern.formatted("");
        String headsUpWithRole =
                headsUpPattern.formatted(helperRole.orElseThrow().getAsMention() + " ");
        return action.flatMap(any -> helpThread.sendMessage(headsUpWithoutRole)
            .flatMap(message -> message.editMessage(headsUpWithRole)));
    }
}
