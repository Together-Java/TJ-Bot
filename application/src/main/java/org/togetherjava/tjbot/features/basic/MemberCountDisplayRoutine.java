package org.togetherjava.tjbot.features.basic;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.Category;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.features.Routine;

import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class MemberCountDisplayRoutine implements Routine {
    private final Predicate<String> wordsInCategory;


    public MemberCountDisplayRoutine(Config config) {
        wordsInCategory = Pattern.compile(config.getMemberCountCategoryName()).asMatchPredicate();
    }

    private void updateCategoryName(Category category) {
        int totalMemberCount = category.getGuild().getMemberCount();
        String baseName = category.getName();
        if (baseName.contains(" Members")) {
            baseName = Pattern.compile("(.+) - \\d+ Members").toString();
        }
        category.getManager()
            .setName("%s - %d Members".formatted(baseName.trim(), totalMemberCount))
            .queue();
    }

    @Override
    public Schedule createSchedule() {
        return new Schedule(ScheduleMode.FIXED_RATE, 0, 24, TimeUnit.HOURS);
    }

    @Override
    public void runRoutine(JDA jda) {
        jda.getGuilds().forEach(guild -> {
            guild.getCategories()
                .stream()
                .filter(cat -> wordsInCategory.test(cat.getName()))
                .forEach(this::updateCategoryName);
        });
    }
}
