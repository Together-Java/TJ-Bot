package org.togetherjava.tjbot.commands.help;

import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import org.togetherjava.tjbot.commands.Feature;

public class HelpPostCreationListener extends ListenerAdapter implements Feature {


    @Override
    public void onChannelCreate(@NotNull final ChannelCreateEvent event) {
        event.getChannel().asMessageChannel().sendMessage("gay").queue();
    }
}
