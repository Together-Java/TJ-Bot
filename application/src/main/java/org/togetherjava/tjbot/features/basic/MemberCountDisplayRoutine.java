package org.togetherjava.tjbot.features.basic;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.Category;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.features.Routine;

import java.text.NumberFormat;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Shows the guild member count on selected category, which updates everyday.
 */
public class MemberCountDisplayRoutine implements Routine {
    private final Predicate<String> memberCountCategoryPredicate;

    /**
     * Creates an instance on member count display routine.
     *
     * @param config the config to use
     */
    public MemberCountDisplayRoutine(Config config) {
        memberCountCategoryPredicate =
                Pattern.compile(config.getMemberCountCategoryPattern() + "( - [\\d,]+ Members)?")
                    .asMatchPredicate();
    }

    private void updateCategoryName(Category category) {
        String memberCount =
                NumberFormat.getInstance().format(category.getGuild().getMemberCount());
        String baseName = category.getName().split("-")[0].trim();

        category.getManager().setName("%s - %s Members".formatted(baseName, memberCount)).queue();
    }

    @Override
    public Schedule createSchedule() {
        return new Schedule(ScheduleMode.FIXED_RATE, 0, 24, TimeUnit.HOURS);
    }

    @Override
    public void runRoutine(JDA jda) {
        jda.getGuilds()
            .forEach(guild -> guild.getCategories()
                .stream()
                .filter(category -> memberCountCategoryPredicate.test(category.getName()))
                .findAny()
                .ifPresent(this::updateCategoryName));
    }
}
