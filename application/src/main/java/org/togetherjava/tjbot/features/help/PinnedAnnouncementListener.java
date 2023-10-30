package org.togetherjava.tjbot.features.help;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.features.MessageReceiverAdapter;

import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Listener that receives all pinned announcement messages from helper forum so that it can be
 * removed
 */
public final class PinnedAnnouncementListener extends MessageReceiverAdapter {

    private final Predicate<String> isHelpForumName;

    /**
     * Creates a new listener to receive all pinned announcement messages sent in help channels.
     *
     * @param config the config to use for this
     */
    public PinnedAnnouncementListener(Config config) {
        isHelpForumName =
                Pattern.compile(config.getHelpSystem().getHelpForumPattern()).asMatchPredicate();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        removePinnedAnnouncement(event);
    }

    private boolean isHelpThread(MessageChannelUnion channel) {
        if (!channel.getType().isThread()) {
            return false;
        }
        ThreadChannel thread = channel.asThreadChannel();
        String rootChannelName = thread.getParentChannel().getName();
        return isHelpForumName.test(rootChannelName);
    }

    private boolean isPinnedAnnouncement(Message message) {
        return message.getType() == MessageType.CHANNEL_PINNED_ADD;
    }

    private void removePinnedAnnouncement(MessageReceivedEvent event) {
        if (isPinnedAnnouncement(event.getMessage()) && isHelpThread(event.getChannel())) {
            event.getMessage().delete().queue();
        }
    }
}
