package org.togetherjava.tjbot.features.javamail;

import com.apptasticsoftware.rssreader.Item;
import com.apptasticsoftware.rssreader.RssReader;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.apache.commons.text.StringEscapeUtils;
import org.jetbrains.annotations.Nullable;
import org.jooq.tools.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.RSSFeed;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.records.RssFeedRecord;
import org.togetherjava.tjbot.features.Routine;

import javax.annotation.Nonnull;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static org.togetherjava.tjbot.db.generated.tables.RssFeed.RSS_FEED;

public final class JavaMailRSSRoutine implements Routine {

    private static final Logger logger = LoggerFactory.getLogger(JavaMailRSSRoutine.class);
    private static final RssReader RSS_READER = new RssReader();
    private static final int MAX_CONTENTS = 300;
    private static final DateTimeFormatter RSS_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
    private final List<RSSFeed> feeds;
    private final Predicate<String> defaultChannelPattern;
    private final Map<RSSFeed, Predicate<String>> targetChannelPatterns = new HashMap<>();
    private final int interval;
    private final Database database;

    public JavaMailRSSRoutine(Config config, Database database) {
        this.feeds = config.getRssFeeds();
        this.interval = config.getRssPollInterval();
        this.database = database;
        this.defaultChannelPattern =
                Pattern.compile(config.getJavaNewsChannelPattern()).asMatchPredicate();
        this.feeds.forEach(feed -> {
            if (feed.targetChannelPattern() != null) {
                var predicate = Pattern.compile(feed.targetChannelPattern()).asMatchPredicate();
                targetChannelPatterns.put(feed, predicate);
            }
        });
    }

    @Override
    public Schedule createSchedule() {
        return new Schedule(ScheduleMode.FIXED_DELAY, 0, interval, TimeUnit.MINUTES);
    }

    @Override
    public void runRoutine(@Nonnull JDA jda) {
        feeds.forEach(feed -> sendRSS(jda, feed));
    }

    private void sendRSS(JDA jda, RSSFeed feed) {
        var textChannel = getTextChannelFromFeed(jda, feed);

        RssFeedRecord entry = database.read(context -> context.selectFrom(RSS_FEED)
            .where(RSS_FEED.URL.eq(feed.url()))
            .limit(1)
            .fetchOne());

        List<Item> items;
        if (entry == null) {
            items = fetchRss(feed.url());
        } else {
            items = fetchRssAfterDate(feed.url(), entry.getLastDate());
        }

        if (textChannel.isEmpty()) {
            logger.warn("Tried to sendRss, got empty response (channel {} not found)",
                    feed.targetChannelPattern());
            return;
        }

        items.forEach(item -> {
            MessageEmbed embed = constructEmbedMessage(item).build();
            textChannel.get().sendMessageEmbeds(List.of(embed)).queue();
        });

        String lastDate = getLatestDate(items);

        if (lastDate == null) {
            return;
        }

        if (entry == null) {
            // Insert
            database.write(context -> context.newRecord(RSS_FEED)
                .setUrl(feed.url())
                .setLastDate(lastDate)
                .insert());
            return;
        }

        database.write(context -> context.update(RSS_FEED)
            .set(RSS_FEED.LAST_DATE, lastDate)
            .where(RSS_FEED.URL.eq(feed.url()))
            .executeAsync());
    }

    @Nullable
    private static String getLatestDate(List<Item> items) {
        String lastDate = null;

        for (Item item : items) {
            if (lastDate == null) {
                lastDate = item.getPubDate().orElseThrow();
                continue;
            }

            LocalDateTime formattedLastDate = getLocalDateTime(lastDate);
            LocalDateTime itemDate = getLocalDateTime(item.getPubDate().orElseThrow());

            if (itemDate.isAfter(formattedLastDate)) {
                lastDate = item.getPubDate().orElseThrow();
            }
        }
        return lastDate;
    }

    private Optional<TextChannel> getTextChannelFromFeed(JDA jda, RSSFeed feed) {
        if (feed.targetChannelPattern() != null) {
            return jda.getTextChannelCache()
                .stream()
                .filter(channel -> targetChannelPatterns.get(feed).test(channel.getName()))
                .findFirst();
        }

        return jda.getTextChannelCache()
            .stream()
            .filter(channel -> defaultChannelPattern.test(channel.getName()))
            .findFirst();
    }

    private static EmbedBuilder constructEmbedMessage(Item item) {
        final EmbedBuilder embedBuilder = new EmbedBuilder();
        String title = item.getTitle().orElse("No title");
        String titleLink = item.getLink().orElse("");
        Optional<String> rawDescription = item.getDescription();

        item.getPubDate().ifPresent(date -> embedBuilder.setTimestamp(getLocalDateTime(date)));

        embedBuilder.setTitle(title, titleLink);
        embedBuilder.setAuthor(item.getChannel().getLink());

        if (rawDescription.isPresent()) {
            Document fullDescription =
                    Jsoup.parse(StringEscapeUtils.unescapeHtml4(rawDescription.get()));
            StringBuilder finalDescription = new StringBuilder();
            fullDescription.body()
                .select("p")
                .forEach(p -> finalDescription.append(p.text()).append(". "));

            embedBuilder
                .setDescription(StringUtils.abbreviate(finalDescription.toString(), MAX_CONTENTS));
            return embedBuilder;
        }

        embedBuilder.setDescription("No description");
        return embedBuilder;
    }

    private static List<Item> fetchRssAfterDate(String rssUrl, String afterDate) {
        final LocalDateTime afterDateTime = getLocalDateTime(afterDate);
        List<Item> rssList = fetchRss(rssUrl);
        final Predicate<Item> itemAfterDate = item -> {
            var pubDateString = item.getPubDate();
            if (pubDateString.isEmpty()) {
                return false;
            }
            var pubDate = getLocalDateTime(pubDateString.get());

            return pubDate.isAfter(afterDateTime);
        };

        if (rssList == null) {
            return List.of();
        }

        return rssList.stream().filter(itemAfterDate).toList();
    }

    /**
     * Fetches new items from a given RSS url.
     */
    private static List<Item> fetchRss(String rssUrl) {
        try {
            return RSS_READER.read(rssUrl).toList();
        } catch (Exception e) {
            logger.warn("Could not fetch RSS from URL ({})", rssUrl);
            return List.of();
        }
    }

    private static LocalDateTime getLocalDateTime(String date) {
        return LocalDateTime.parse(date, RSS_DATE_FORMAT);
    }
}
