package org.togetherjava.tjbot.features.javamail;

import com.apptasticsoftware.rssreader.Item;
import com.apptasticsoftware.rssreader.RssReader;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.apache.commons.text.StringEscapeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jooq.tools.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.RSSFeed;
import org.togetherjava.tjbot.config.RSSFeedsConfig;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.records.RssFeedRecord;
import org.togetherjava.tjbot.features.Routine;

import javax.annotation.Nonnull;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static org.togetherjava.tjbot.db.generated.tables.RssFeed.RSS_FEED;

/**
 * This class orchestrates the retrieval, organization, and distribution of RSS feed posts sourced
 * from various channels, all of which can be easily configured via the {@code config.json}
 * <p>
 * To include a new RSS feed, simply define an {@link RSSFeed} entry in the {@code "rssFeeds"} array
 * within the configuration file, adhering to the format shown below:
 * 
 * <pre>
 * {@code
 * {
 *     "url": "https://example.com/feed",
 *     "targetChannelPattern": "example",
 *     "dateFormatterPattern": "EEE, dd MMM yyyy HH:mm:ss Z"
 * }
 * }
 * </pre>
 * 
 * Where:
 * <ul>
 * <li>{@code url} represents the URL of the RSS feed.</li>
 * <li>{@code targetChannelPattern} specifies the pattern to identify the target channel for the
 * feed posts.</li>
 * <li>{@code dateFormatterPattern} denotes the pattern for parsing the date and time information in
 * the feed.</li>
 * </ul>
 */
public final class RSSHandlerRoutine implements Routine {

    private static final Logger logger = LoggerFactory.getLogger(RSSHandlerRoutine.class);
    private static final int MAX_CONTENTS = 1000;
    private static final ZonedDateTime ZONED_TIME_MIN =
            ZonedDateTime.of(LocalDateTime.MIN, ZoneId.systemDefault());
    private final RssReader rssReader;
    private final RSSFeedsConfig config;
    private final Predicate<String> fallbackChannelPattern;
    private final Map<RSSFeed, Predicate<String>> targetChannelPatterns;
    private final int interval;
    private final Database database;

    /**
     * Constructs an RSSHandlerRoutine with the provided configuration and database.
     *
     * @param config The configuration containing RSS feed details.
     * @param database The database for storing RSS feed data.
     */
    public RSSHandlerRoutine(Config config, Database database) {
        this.config = config.getRSSFeedsConfig();
        this.interval = this.config.rssPollInterval();
        this.database = database;
        this.fallbackChannelPattern =
                Pattern.compile(this.config.fallbackChannelPattern()).asMatchPredicate();
        this.targetChannelPatterns = new HashMap<>();
        this.config.feeds().forEach(feed -> {
            if (feed.targetChannelPattern() != null) {
                Predicate<String> predicate =
                        Pattern.compile(feed.targetChannelPattern()).asMatchPredicate();
                targetChannelPatterns.put(feed, predicate);
            }
        });
        this.rssReader = new RssReader();
    }

    @Override
    @NotNull
    public Schedule createSchedule() {
        return new Schedule(ScheduleMode.FIXED_DELAY, 0, interval, TimeUnit.MINUTES);
    }

    @Override
    public void runRoutine(@Nonnull JDA jda) {
        this.config.feeds().forEach(feed -> sendRSS(jda, feed));
    }

    /**
     * Sends all the necessary posts from a given RSS feed.
     * <p>
     * This handles fetching the latest posts from the given URL, checking which ones have already
     * been posted by reading information from the database and updating the last posted date.
     */
    private void sendRSS(JDA jda, RSSFeed feedConfig) throws DateTimeParseException {
        // Don't proceed if the text channel was not found
        TextChannel textChannel = getTextChannelFromFeed(jda, feedConfig).orElse(null);
        if (textChannel == null) {
            logger.warn("Tried to sendRss, got empty response (channel {} not found)",
                    feedConfig.targetChannelPattern());
            return;
        }

        // Acquire the list of RSS items from the configured URL
        List<Item> rssItems = fetchRss(feedConfig.url());
        if (rssItems.isEmpty()) {
            return;
        }

        // Do not proceed if the configured date format does not match
        // the actual date format provided by the feed's contents
        if (!isValidDateFormat(rssItems, feedConfig)) {
            logger.warn("Could not find valid date format for RSS feed {}", feedConfig.url());
            return;
        }

        // Attempt to find any stored information regarding the provided RSS URL
        RssFeedRecord dateResult = database.read(context -> context.selectFrom(RSS_FEED)
            .where(RSS_FEED.URL.eq(feedConfig.url()))
            .limit(1)
            .fetchAny());

        String dateStr = dateResult == null ? null : dateResult.getLastDate();
        ZonedDateTime lastSavedDate = getZonedDateTime(dateStr, feedConfig.dateFormatterPattern());

        final Predicate<Item> shouldItemBePosted = item -> {
            ZonedDateTime itemPubDate =
                    getDateTimeFromItem(item, feedConfig.dateFormatterPattern());
            return itemPubDate.isAfter(lastSavedDate);
        };

        // Date that will be stored in the database at the end
        ZonedDateTime lastPostedDate = lastSavedDate;

        // Send each item that should be posted and concurrently
        // find the post with the latest date
        for (Item item : rssItems.reversed()) {
            if (!shouldItemBePosted.test(item)) {
                continue;
            }

            MessageEmbed embed = constructEmbedMessage(item, feedConfig).build();
            textChannel.sendMessageEmbeds(List.of(embed)).queue();

            // Get the last posted date so that we update the database
            ZonedDateTime pubDate = getDateTimeFromItem(item, feedConfig.dateFormatterPattern());
            if (pubDate.isAfter(lastPostedDate)) {
                lastPostedDate = pubDate;
            }
        }

        // Finally, save the last posted date to the database.
        DateTimeFormatter dateTimeFormatter =
                DateTimeFormatter.ofPattern(feedConfig.dateFormatterPattern());
        String lastDateStr = lastPostedDate.format(dateTimeFormatter);
        if (dateResult == null) {
            database.write(context -> context.newRecord(RSS_FEED)
                .setUrl(feedConfig.url())
                .setLastDate(lastDateStr)
                .insert());
            return;
        }

        // If we already have an existing record with the given URL,
        // now is the time to update it
        database.write(context -> context.update(RSS_FEED)
            .set(RSS_FEED.LAST_DATE, lastDateStr)
            .where(RSS_FEED.URL.eq(feedConfig.url()))
            .executeAsync());
    }

    /**
     * Attempts to get a {@link ZonedDateTime} from an {@link Item} with a provided string date time
     * format.
     * <p>
     * If either of the function inputs are null, the oldest-possible {@link ZonedDateTime} will get
     * returned instead.
     *
     * @return The computed {@link ZonedDateTime}
     */
    private static ZonedDateTime getDateTimeFromItem(Item item, String dateTimeFormat) {
        String pubDate = item.getPubDate().orElse(null);

        if (pubDate == null || dateTimeFormat == null) {
            return ZONED_TIME_MIN;
        }

        return getZonedDateTime(pubDate, dateTimeFormat);
    }

    /**
     * Given a list of RSS feed items, this function checks if the dates are valid
     * <p>
     * This assumes that all items share the same date format (as is usual in an RSS feed response),
     * therefore it only checks the first item's date for the final result.
     */
    private static boolean isValidDateFormat(List<Item> rssFeeds, RSSFeed feedConfig) {
        try {
            final Item firstRssFeed = rssFeeds.getFirst();
            String firstRssFeedPubDate = firstRssFeed.getPubDate().orElse(null);

            if (firstRssFeedPubDate == null) {
                return false;
            }

            getZonedDateTime(firstRssFeedPubDate, feedConfig.dateFormatterPattern());
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    /**
     * Attempts to find a text channel from a given RSS feed configuration.
     */
    private Optional<TextChannel> getTextChannelFromFeed(JDA jda, RSSFeed feed) {
        // Attempt to find the target channel
        if (feed.targetChannelPattern() != null) {
            return jda.getTextChannelCache()
                .stream()
                .filter(channel -> targetChannelPatterns.get(feed).test(channel.getName()))
                .findFirst();
        }

        // If the target channel was not found, use the fallback
        return jda.getTextChannelCache()
            .stream()
            .filter(channel -> fallbackChannelPattern.test(channel.getName()))
            .findFirst();
    }

    /**
     * Provides the {@link EmbedBuilder} from an RSS item used for sending RSS posts.
     */
    private static EmbedBuilder constructEmbedMessage(Item item, RSSFeed feedConfig) {
        final EmbedBuilder embedBuilder = new EmbedBuilder();
        String title = item.getTitle().orElse("No title");
        String titleLink = item.getLink().orElse("");
        Optional<String> rawDescription = item.getDescription();

        // Set the item's timestamp to the embed if found
        item.getPubDate()
            .ifPresent(date -> embedBuilder
                .setTimestamp(getZonedDateTime(date, feedConfig.dateFormatterPattern())));

        embedBuilder.setTitle(title, titleLink);
        embedBuilder.setAuthor(item.getChannel().getLink());

        // Process embed's description if a raw description was provided
        if (rawDescription.isPresent() && !rawDescription.get().isEmpty()) {
            Document fullDescription =
                    Jsoup.parse(StringEscapeUtils.unescapeHtml4(rawDescription.get()));
            StringBuilder finalDescription = new StringBuilder();
            fullDescription.body()
                .select("*")
                .forEach(p -> finalDescription.append(p.text()).append(". "));

            embedBuilder
                .setDescription(StringUtils.abbreviate(finalDescription.toString(), MAX_CONTENTS));
            return embedBuilder;
        }

        // Fill the description with a placeholder if the description was empty
        embedBuilder.setDescription("No description");
        return embedBuilder;
    }

    /**
     * Fetches a list of {@link Item} from a given RSS url.
     */
    private List<Item> fetchRss(String rssUrl) {
        try {
            return rssReader.read(rssUrl).toList();
        } catch (IOException e) {
            logger.warn("Could not fetch RSS from URL ({})", rssUrl);
            return List.of();
        }
    }

    /**
     * Helper function for parsing a given date value to a {@link ZonedDateTime} with a given
     * format.
     *
     */
    private static ZonedDateTime getZonedDateTime(@Nullable String date, @NotNull String format)
            throws DateTimeParseException {
        if (date == null) {
            return ZONED_TIME_MIN;
        }

        return ZonedDateTime.parse(date, DateTimeFormatter.ofPattern(format));
    }
}
