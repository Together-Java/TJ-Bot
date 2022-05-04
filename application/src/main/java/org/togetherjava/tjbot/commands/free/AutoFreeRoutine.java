package org.togetherjava.tjbot.commands.free;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.Routine;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Routine that automatically marks busy help channels free after a certain time without any
 * activity.
 */
public final class AutoFreeRoutine implements Routine {
    private final FreeChannelMonitor channelMonitor;

    /**
     * Creates a new instance.
     *
     * @param channelMonitor used to monitor and control the free-status of channels
     */
    public AutoFreeRoutine(@NotNull FreeChannelMonitor channelMonitor) {
        this.channelMonitor = channelMonitor;
    }

    @Override
    public void runRoutine(@NotNull JDA jda) {
        channelMonitor.guildIds()
            .map(jda::getGuildById)
            .filter(Objects::nonNull)
            .forEach(this::processGuild);
    }

    private void processGuild(@NotNull Guild guild) {
        // Mark inactive channels free
        Collection<TextChannel> inactiveChannels = channelMonitor.freeInactiveChannels(guild);

        // Then update the status
        channelMonitor.displayStatus(guild);

        // Finally, send the messages (the order is important to ensure sane behavior in case of
        // crashes)
        inactiveChannels.forEach(inactiveChannel -> inactiveChannel
            .sendMessage(UserStrings.AUTO_MARK_AS_FREE.message())
            .queue());
    }

    @Override
    public @NotNull Schedule createSchedule() {
        return new Schedule(ScheduleMode.FIXED_RATE, 1, 5, TimeUnit.MINUTES);
    }
}
