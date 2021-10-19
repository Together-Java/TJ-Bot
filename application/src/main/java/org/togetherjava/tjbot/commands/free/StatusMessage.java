package org.togetherjava.tjbot.commands.free;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class StatusMessage {
    private final Guild guild;
    private final TextChannel postingChannel;
    private final Message existingStatus;
    private final List<ChannelStatus> channelStatuses;


    private StatusMessage(@NotNull Guild guild, @NotNull TextChannel postingChannel,
            @NotNull List<ChannelStatus> channelStatuses, Message existingStatus) {
        this.guild = guild;
        this.postingChannel = postingChannel;
        this.channelStatuses = channelStatuses;
        this.existingStatus = existingStatus;
    }

    private static class StatusMessageBuilder {
        private List<ChannelStatus> channelStatuses;
        private TextChannel postingChannel;

        private StatusMessageBuilder(@NotNull List<ChannelStatus> channelStatuses) {
            this.channelStatuses = channelStatuses;
        }

        public StatusMessageBuilder channelToPostIn(TextChannel channel) {
            postingChannel = channel;
            return this;
        }
    }

    public static StatusMessageBuilder builder(@NotNull List<ChannelStatus> channelStatuses) {
        return new StatusMessageBuilder(channelStatuses);
    }
}
