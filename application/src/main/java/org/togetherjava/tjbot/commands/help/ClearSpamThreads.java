package org.togetherjava.tjbot.commands.help;

import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import org.jetbrains.annotations.NotNull;

import org.togetherjava.tjbot.commands.EventReceiver;

public class ClearSpamThreads implements EventReceiver {
    @Override
    public void onEvent(@NotNull GenericEvent event) {
        if (!(event instanceof GuildBanEvent)) {
            return;
        }

        GuildBanEvent banEvent = (GuildBanEvent) event;

    }
}
