package org.togetherjava.tjbot.commands.system;

import net.dv8tion.jda.api.JDA;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.*;

public class JDAScheduler {
    private static JDAScheduler instance;

    private final JDA jda;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private JDAScheduler(JDA jda) {
        this.jda = jda;
    }

    public static void create(@NotNull JDA jda) {
        Objects.requireNonNull(jda, "Cannot create a JDAScheduler without an instance of JDA");
        if (instance != null) {
            throw new IllegalStateException("Can only create one instance of JDAScheduler");
        }

        instance = new JDAScheduler(jda);
    }

    public static ScheduledFuture scheduleTask(Callable task, long delay, TimeUnit timeUnit) {
        Objects.requireNonNull(instance,
                "Cannot schedule a task before the scheduler has been created");

        return instance.scheduler.schedule(task, delay, timeUnit);
    }
}
