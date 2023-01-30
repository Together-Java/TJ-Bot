package org.togetherjava.tjbot.features.help;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.features.EventReceiver;

/**
 * Remove all thread channels associated to a user when they leave the guild.
 */
public final class GuildLeaveCloseThreadListener extends ListenerAdapter implements EventReceiver {
    private final String helpForumPattern;

    /**
     * Creates a new instance.
     *
     * @param config the config to get help forum channel pattern from
     */
    public GuildLeaveCloseThreadListener(Config config) {
        this.helpForumPattern = config.getHelpSystem().getHelpForumPattern();
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        MessageEmbed embed = new EmbedBuilder().setTitle("OP left")
            .setDescription("Closing thread...")
            .setColor(HelpSystemHelper.AMBIENT_COLOR)
            .build();

        event.getGuild()
            .retrieveActiveThreads()
            .queue(threads -> threads.stream()
                .filter(thread -> thread.getOwnerIdLong() == event.getUser().getIdLong())
                .filter(thread -> thread.getParentChannel().getName().matches(helpForumPattern))
                .forEach(thread -> thread.sendMessageEmbeds(embed)
                    .flatMap(any -> thread.getManager().setArchived(true))
                    .queue()));
    }
}
