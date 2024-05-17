package org.togetherjava.tjbot.features.javamail;

import com.apptasticsoftware.rssreader.Item;
import com.apptasticsoftware.rssreader.RssReader;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.cache.SnowflakeCacheView;
import org.apache.commons.text.StringEscapeUtils;
import org.jetbrains.annotations.Nullable;
import org.jooq.tools.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
        this.interval = this.config.pollIntervalInMinutes();
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
     *
     * @param jda The JDA instance.
     * @param feedConfig The configuration object for the RSS feed.
     */
    private void sendRSS(JDA jda, RSSFeed feedConfig) {
        List<TextChannel> textChannels = getTextChannelsFromFeed(jda, feedConfig);
        if (textChannels.isEmpty()) {
            logger.warn(
                    "Tried to send an RSS post, but neither a target channel nor a fallback channel was found.");
            return;
        }

        List<Item> rssItems = fetchRSSItemsFromURL(feedConfig.url());
        if (rssItems.isEmpty()) {
            return;
        }

        for (Item item : rssItems) {
            if (!isValidDateFormat(item, feedConfig)) {
                logger.warn("Could not find valid or matching date format for RSS feed {}",
                        feedConfig.url());
                return;
            }
        }

        final Optional<Predicate<Item>> shouldItemBePosted =
                prepareItemPostPredicate(feedConfig, rssItems);
        if (shouldItemBePosted.isEmpty()) {
            return;
        }
        rssItems.reversed()
            .stream()
            .filter(shouldItemBePosted.get())
            .forEachOrdered(item -> postItem(textChannels, item, feedConfig));
    }

    private Optional<Predicate<Item>> prepareItemPostPredicate(RSSFeed feedConfig,
            List<Item> rssItems) {
        Optional<RssFeedRecord> rssFeedRecord = getRssFeedRecordFromDatabase(feedConfig);
        Optional<ZonedDateTime> lastPostedDate =
                getLatestPostDateFromItems(rssItems, feedConfig.dateFormatterPattern());

        lastPostedDate.ifPresent(
                date -> updateLastDateToDatabase(feedConfig, rssFeedRecord.orElse(null), date));

        if (rssFeedRecord.isEmpty()) {
            return Optional.empty();
        }

        Optional<ZonedDateTime> lastSavedDate = getLastSavedDateFromDatabaseRecord(
                rssFeedRecord.orElseThrow(), feedConfig.dateFormatterPattern());

        if (lastSavedDate.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(item -> {
            ZonedDateTime itemPubDate =
                    getDateTimeFromItem(item, feedConfig.dateFormatterPattern());
            return itemPubDate.isAfter(lastSavedDate.orElseThrow());
        });
    }

    /**
     * Retrieves an RSS feed record from the database based on the provided RSS feed configuration.
     *
     * @param feedConfig the RSS feed configuration to retrieve the record for
     * @return an optional RSS feed record retrieved from the database
     */
    private Optional<RssFeedRecord> getRssFeedRecordFromDatabase(RSSFeed feedConfig) {
        return Optional.ofNullable(database.read(context -> context.selectFrom(RSS_FEED)
            .where(RSS_FEED.URL.eq(feedConfig.url()))
            .limit(1)
            .fetchAny()));
    }

    /**
     * Retrieves the last saved date from the database record associated with the given RSS feed
     * record.
     *
     * @param rssRecord an existing RSS feed record to retrieve the last saved date from
     * @param dateFormatterPattern the pattern used to parse the date from the database record
     * @return An {@link Optional} containing the last saved date if it could be retrieved and
     *         parsed successfully, otherwise an empty {@link Optional}
     */
    private Optional<ZonedDateTime> getLastSavedDateFromDatabaseRecord(RssFeedRecord rssRecord,
            String dateFormatterPattern) throws DateTimeParseException {
        try {
            ZonedDateTime savedDate =
                    getZonedDateTime(rssRecord.getLastDate(), dateFormatterPattern);
            return Optional.of(savedDate);
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /**
     * Retrieves the latest post date from the given list of items.
     *
     * @param items the list of items to retrieve the latest post date from
     * @param dateFormatterPattern the pattern used to parse the date from the database record
     * @return the latest post date as a {@link ZonedDateTime} object, or null if the list is empty
     */
    private Optional<ZonedDateTime> getLatestPostDateFromItems(List<Item> items,
            String dateFormatterPattern) {
        return items.stream()
            .map(item -> getDateTimeFromItem(item, dateFormatterPattern))
            .max(ZonedDateTime::compareTo);
    }

    /**
     * Posts an RSS item to a text channel.
     *
     * @param textChannels the text channels to which the item will be posted
     * @param rssItem the RSS item to post
     * @param feedConfig the RSS feed configuration
     */
    private void postItem(List<TextChannel> textChannels, Item rssItem, RSSFeed feedConfig) {
        MessageEmbed embed = constructEmbedMessage(rssItem, feedConfig).build();
        textChannels.forEach(channel -> channel.sendMessageEmbeds(List.of(embed)).queue());
    }

    /**
     * Updates the last posted date to the database for the specified RSS feed configuration.
     * <p>
     * This will insert a <b>new</b> entry to the database if the provided {@link RssFeedRecord} is
     * null.
     *
     * @param feedConfig the RSS feed configuration
     * @param rssFeedRecord the record representing the RSS feed, can be null if not found in the
     *        database
     * @param lastPostedDate the last posted date to be updated
     *
     * @throws DateTimeParseException if the date cannot be parsed
     */
    private void updateLastDateToDatabase(RSSFeed feedConfig, @Nullable RssFeedRecord rssFeedRecord,
            ZonedDateTime lastPostedDate) {
        DateTimeFormatter dateTimeFormatter =
                DateTimeFormatter.ofPattern(feedConfig.dateFormatterPattern());
        String lastDateStr = lastPostedDate.format(dateTimeFormatter);

        if (rssFeedRecord == null) {
            database.write(context -> context.newRecord(RSS_FEED)
                .setUrl(feedConfig.url())
                .setLastDate(lastDateStr)
                .insert());
            return;
        }

        database.write(context -> context.update(RSS_FEED)
            .set(RSS_FEED.LAST_DATE, lastDateStr)
            .where(RSS_FEED.URL.eq(feedConfig.url()))
            .executeAsync());
    }

    /**
     * Attempts to get a {@link ZonedDateTime} from an {@link Item} with a provided string date time
     * format.
     * <p>
     * If either of the function inputs are null or a {@link DateTimeParseException} is caught, the
     * oldest-possible {@link ZonedDateTime} will get returned instead.
     *
     * @param item The {@link Item} from which to extract the date.
     * @param dateTimeFormat The format of the date time string.
     * @return The computed {@link ZonedDateTime}
     * @throws DateTimeParseException if the date cannot be parsed
     */
    private static ZonedDateTime getDateTimeFromItem(Item item, String dateTimeFormat)
            throws DateTimeParseException {
        Optional<String> pubDate = item.getPubDate();

        return pubDate.map(s -> getZonedDateTime(s, dateTimeFormat)).orElse(ZONED_TIME_MIN);

    }

    /**
     * Checks if the dates between an RSS item and the provided config match.
     *
     * @param rssItem the RSS feed item
     * @param feedConfig the RSS feed configuration containing the date formatter pattern
     * @return true if the date format is valid, false otherwise
     */
    private static boolean isValidDateFormat(Item rssItem, RSSFeed feedConfig) {
        Optional<String> firstRssFeedPubDate = rssItem.getPubDate();

        if (firstRssFeedPubDate.isEmpty()) {
            return false;
        }

        try {
            // If this throws a DateTimeParseException then it's certain
            // that the format pattern defined in the config and the
            // feed's actual format differ.
            getZonedDateTime(firstRssFeedPubDate.get(), feedConfig.dateFormatterPattern());
        } catch (DateTimeParseException e) {
            return false;
        }
        return true;
    }

    /**
     * Attempts to find text channels from a given RSS feed configuration.
     *
     * @param jda the JDA instance
     * @param feed the RSS feed configuration to search for text channels
     * @return an {@link List} of the text channels found, or empty if none are found
     */
    private List<TextChannel> getTextChannelsFromFeed(JDA jda, RSSFeed feed) {
        final SnowflakeCacheView<TextChannel> textChannelCache = jda.getTextChannelCache();
        List<TextChannel> textChannels = textChannelCache.stream()
            .filter(channel -> targetChannelPatterns.get(feed).test(channel.getName()))
            .toList();

        if (!textChannels.isEmpty()) {
            return textChannels;
        }

        return textChannelCache.stream()
            .filter(channel -> fallbackChannelPattern.test(channel.getName()))
            .toList();
    }

    /**
     * Provides the {@link EmbedBuilder} from an RSS item used for sending RSS posts.
     *
     * @param item the RSS item to construct the embed message from
     * @param feedConfig the configuration of the RSS feed
     * @return the constructed {@link EmbedBuilder} containing information from the RSS item
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
        if (rawDescription.isPresent() && !rawDescription.orElseThrow().isEmpty()) {
            Document fullDescription =
                    Jsoup.parse(StringEscapeUtils.unescapeHtml4(rawDescription.orElseThrow()));
            String finalDescription = fullDescription.body()
                .select("*")
                .stream()
                .map(Element::text)
                .collect(Collectors.joining(". "));

            embedBuilder.setDescription(StringUtils.abbreviate(finalDescription, MAX_CONTENTS));
        } else {
            embedBuilder.setDescription("No description");
        }

        return embedBuilder;
    }

    /**
     * Fetches a list of {@link Item} from a given RSS url.
     *
     * @param rssUrl the URL of the RSS feed to fetch
     * @return a list of {@link Item} parsed from the RSS feed, or an empty list if there's an
     *         {@link IOException}
     */
    private List<Item> fetchRSSItemsFromURL(String rssUrl) {
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
     * @param date the date value to parse, can be null
     * @param format the format pattern to use for parsing
     * @return the parsed {@link ZonedDateTime} object
     * @throws DateTimeParseException if the date cannot be parsed
     */
    private static ZonedDateTime getZonedDateTime(@Nullable String date, String format)
            throws DateTimeParseException {
        if (date == null) {
            return ZONED_TIME_MIN;
        }

        return ZonedDateTime.parse(date, DateTimeFormatter.ofPattern(format));
    }
}
