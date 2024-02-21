package org.togetherjava.tjbot.features.basic;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.Category;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.features.Routine;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class MemberCountDisplayRoutine implements Routine {
    private final String wordsInCategory;

    public MemberCountDisplayRoutine(Config config) {
        this.wordsInCategory = config.getMemberCountCategoryName();
    }

    private void updateCategoryName(Category category) {
        int totalMemberCount = category.getGuild().getMemberCount();
        String basename = category.getName();
        category.getManager()
            .setName("%s - %d Members".formatted(basename, totalMemberCount))
            .queue();
    }

    @Override
    public Schedule createSchedule() {
        return new Schedule(ScheduleMode.FIXED_RATE, 0, 24, TimeUnit.HOURS);
    }

    @Override
    public void runRoutine(JDA jda) {
        jda.getGuilds()
            .stream()
            .map(Guild::getCategories)
            .flatMap(List::stream)
            .filter(cat -> cat.getName().equalsIgnoreCase(wordsInCategory))
            .forEach(this::updateCategoryName);
    }
}
