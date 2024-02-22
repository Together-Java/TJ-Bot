package org.togetherjava.tjbot.features.javamail;

import com.apptasticsoftware.rssreader.Item;
import com.apptasticsoftware.rssreader.RssReader;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.RSSFeed;
import org.togetherjava.tjbot.features.Routine;

import javax.annotation.Nonnull;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class JavaMailRSSRoutine implements Routine {

    private static final Logger logger = LoggerFactory.getLogger(JavaMailRSSRoutine.class);
    private static final RssReader RSS_READER = new RssReader();
    private static final int MAX_CONTENTS = 150;
    private final List<RSSFeed> feeds;
    private final Map<RSSFeed, Predicate<String>> targetChannelPatterns = new HashMap<>();

    public JavaMailRSSRoutine(Config config) {
        this.feeds = config.getRssFeeds();

        this.feeds.forEach(feed -> {
            var predicate = Pattern.compile(feed.targetChannelPattern()).asMatchPredicate();
            targetChannelPatterns.put(feed, predicate);
        });
    }

    @Override
    public Schedule createSchedule() {
        // TODO: Make this adjustable in the future
        return new Schedule(ScheduleMode.FIXED_DELAY, 0, 10, TimeUnit.MINUTES);
    }

    @Override
    public void runRoutine(@Nonnull JDA jda) {
        feeds.forEach(feed -> sendRSS(jda, feed));
    }

    private void sendRSS(JDA jda, RSSFeed feed) {
        var textChannel = getTextChannelFromFeed(jda, feed);
        var items = fetchRss(feed.url());

        if (textChannel.isEmpty()) {
            logger.warn("Tried to sendRss, got empty response (channel {} not found)",
                    feed.targetChannelPattern());
            return;
        }

        items.forEach(item -> {
            MessageEmbed embed = constructEmbedMessage(item, textChannel.get()).build();
            textChannel.get().sendMessageEmbeds(List.of(embed)).queue();
        });
    }

    private Optional<TextChannel> getTextChannelFromFeed(JDA jda, RSSFeed feed) {
        return jda.getTextChannelCache()
            .stream()
            .filter(channel -> targetChannelPatterns.get(feed).test(channel.getName()))
            .findFirst();
    }

    @SuppressWarnings("static-access")
    private static EmbedBuilder constructEmbedMessage(Item item, TextChannel channel) {
        final EmbedBuilder embedBuilder = new EmbedBuilder();

        embedBuilder.setTitle(item.getTitle().get(), item.getLink().get());
        embedBuilder.setDescription(StringEscapeUtils
            .unescapeHtml4(item.getDescription().get().substring(0, MAX_CONTENTS)) + "...");
        embedBuilder
            .setFooter("%s | %d".format(item.getPubDate().get(), item.getChannel().getLink()));
        return embedBuilder;
    }


    // TODO: make this use DateTime
    private static List<Item> getPostsAfterDate(String rssUrl, long date) {
        List<Item> rssList = fetchRss(rssUrl);
        final Predicate<Item> rssListPredicate = item -> {
            var pubDateTime = item.getPubDate();
            if (pubDateTime.isEmpty()) {
                return false;
            }

            long pubDate = parseDate(pubDateTime.get());

            return pubDate > date;
        };

        if (rssList == null) {
            return List.of();
        }

        return rssList.stream().filter(rssListPredicate).collect(Collectors.toList());
    }

    private static Optional<Item> getLatest(String rssUrl) throws IOException {
        return fetchRss(rssUrl).stream().findFirst();
    }

    /**
     * Fetches new items from a given RSS url.
     */
    private static List<Item> fetchRss(String rssUrl) {
        try {
            return RSS_READER.read(rssUrl).collect(Collectors.toList());
        } catch (Exception e) {
            logger.warn("Could not fetch RSS from URL ({})", rssUrl);
            return List.of();
        }
    }

    private static long parseDate(String date) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        try {
            return dateFormat.parse(date).getTime();
        } catch (Exception e) {
            logger.error("Could not parse date, {}", e.getMessage());
        }
        return 0;
    }
}
