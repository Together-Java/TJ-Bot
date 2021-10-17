package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;

import java.util.Objects;

public class WarnCommand extends SlashCommandAdapter {
    private static final String REASON_OPTION = "reason";
    private static final String USER_OPTION = "user";

    protected WarnCommand() {
        super("warn", "Use this command to warn", SlashCommandVisibility.GUILD);

        getData().addOption(OptionType.USER, USER_OPTION, "The user which you want to warn", true)
                .addOption(OptionType.STRING, REASON_OPTION, "The reason of the warb", true);
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        Member user = Objects.requireNonNull(event.getOption(USER_OPTION)).getAsMember();


    }
}
