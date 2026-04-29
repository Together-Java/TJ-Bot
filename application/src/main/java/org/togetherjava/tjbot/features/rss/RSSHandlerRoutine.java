package org.togetherjava.tjbot.features.rss;

import com.apptasticsoftware.rssreader.Item;
import com.apptasticsoftware.rssreader.RssReader;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.cache.SnowflakeCacheView;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
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
import org.togetherjava.tjbot.features.analytics.Metrics;

import javax.annotation.Nonnull;

import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
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
 * <p>
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
    private static final String HTTP_USER_AGENT =
            "TJ-Bot/1.0 (+https://github.com/Together-Java/TJ-Bot)";
    private final RssReader rssReader;
    private final RSSFeedsConfig config;
    private final Predicate<String> fallbackChannelPattern;
    private final Predicate<String> isVideoLink;
    private final Map<RSSFeed, Predicate<String>> targetChannelPatterns;
    private final int interval;
    private final Database database;
    private final Metrics metrics;

    private final Cache<String, FailureState> circuitBreaker =
            Caffeine.newBuilder().expireAfterWrite(7, TimeUnit.DAYS).maximumSize(500).build();

    private static final int DEAD_RSS_FEED_FAILURE_THRESHOLD = 15;
    private static final double BACKOFF_BASE = 2.0;
    private static final double BACKOFF_EXPONENT_OFFSET = 1.0;
    private static final double MAX_BACKOFF_HOURS = 24.0;

    /**
     * Constructs an RSSHandlerRoutine with the provided configuration and database.
     *
     * @param config The configuration containing RSS feed details.
     * @param database The database for storing RSS feed data.
     * @param metrics to track events
     */
    public RSSHandlerRoutine(Config config, Database database, Metrics metrics) {
        this.config = config.getRSSFeedsConfig();
        this.interval = this.config.pollIntervalInMinutes();
        this.database = database;
        this.metrics = metrics;

        this.fallbackChannelPattern =
                Pattern.compile(this.config.fallbackChannelPattern()).asMatchPredicate();
        isVideoLink = Pattern.compile(this.config.videoLinkPattern()).asMatchPredicate();
        this.targetChannelPatterns = new HashMap<>();
        this.config.feeds().forEach(feed -> {
            if (feed.targetChannelPattern() != null) {
                Predicate<String> predicate =
                        Pattern.compile(feed.targetChannelPattern()).asMatchPredicate();
                targetChannelPatterns.put(feed, predicate);
            }
        });

        this.rssReader = new RssReader();
        this.rssReader.setUserAgent(HTTP_USER_AGENT);
    }

    @Override
    public Schedule createSchedule() {
        return new Schedule(ScheduleMode.FIXED_DELAY, 0, interval, TimeUnit.MINUTES);
    }

    @Override
    public void runRoutine(@Nonnull JDA jda) {
        this.config.feeds().forEach(feed -> {
            if (isBackingOff(feed.url())) {
                logger.debug("Skipping RSS feed (Backing off): {}", feed.url());
                return;
            }

            sendRSS(jda, feed);
        });
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
            .filter(shouldItemBePosted.orElseThrow())
            .forEachOrdered(item -> postItem(textChannels, item, feedConfig));
    }

    private Optional<Predicate<Item>> prepareItemPostPredicate(RSSFeed feedConfig,
            List<Item> rssItems) {
        Optional<RssFeedRecord> rssFeedRecord = getRssFeedRecordFromDatabase(feedConfig);
        Optional<Instant> lastPostedDate =
                getLatestPostDateFromItems(rssItems, feedConfig.dateFormatterPattern());

        lastPostedDate.ifPresent(
                date -> updateLastDateToDatabase(feedConfig, rssFeedRecord.orElse(null), date));

        if (rssFeedRecord.isEmpty()) {
            return Optional.empty();
        }

        Instant lastSavedDate;
        try {
            lastSavedDate = getLastSavedDateFromDatabaseRecord(rssFeedRecord.orElseThrow());
        } catch (DateTimeParseException _) {
            Optional<Instant> convertedDate = convertDateTimeToInstant(feedConfig);

            if (convertedDate.isEmpty()) {
                return Optional.empty();
            }

            lastSavedDate = convertedDate.get();
        }

        final Instant convertedLastSavedDate = lastSavedDate;

        return Optional.of(item -> {
            Instant itemPubDate = getDateTimeFromItem(item, feedConfig.dateFormatterPattern());
            return itemPubDate.isAfter(convertedLastSavedDate);
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
     * @return An {@link Optional} containing the last saved date if it could be retrieved and
     *         parsed successfully, otherwise an empty {@link Optional}
     */
    private Instant getLastSavedDateFromDatabaseRecord(RssFeedRecord rssRecord)
            throws DateTimeParseException {
        return Instant.parse(rssRecord.getLastDate());
    }

    /**
     * Retrieves the latest post date from the given list of items.
     *
     * @param items the list of items to retrieve the latest post date from
     * @param dateFormatterPattern the pattern used to parse the date from the database record
     * @return the latest post date as a {@link Instant} object, or null if the list is empty
     */
    private Optional<Instant> getLatestPostDateFromItems(List<Item> items,
            String dateFormatterPattern) {
        return items.stream()
            .map(item -> getDateTimeFromItem(item, dateFormatterPattern))
            .max(Instant::compareTo);
    }

    /**
     * Posts an RSS item to a text channel.
     *
     * @param textChannels the text channels to which the item will be posted
     * @param rssItem the RSS item to post
     * @param feedConfig the RSS feed configuration
     */
    private void postItem(List<TextChannel> textChannels, Item rssItem, RSSFeed feedConfig) {
        metrics.count("rss-item_posted");
        MessageCreateData message = constructMessage(rssItem, feedConfig);
        textChannels.forEach(channel -> channel.sendMessage(message).queue());
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
     * @throws DateTimeParseException if the date cannot be parsed
     */
    private void updateLastDateToDatabase(RSSFeed feedConfig, @Nullable RssFeedRecord rssFeedRecord,
            Instant lastPostedDate) {
        String lastDateStr = lastPostedDate.toString();

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
     * Attempts to get a {@link Instant} from an {@link Item} with a provided string date time
     * format.
     * <p>
     * If either of the function inputs are null or a {@link DateTimeParseException} is caught, the
     * oldest-possible {@link Instant} will get returned instead.
     *
     * @param item The {@link Item} from which to extract the date.
     * @param dateTimeFormat The format of the date time string.
     * @return The computed {@link Instant}
     * @throws DateTimeParseException if the date cannot be parsed
     */
    private static Instant getDateTimeFromItem(Item item, String dateTimeFormat)
            throws DateTimeParseException {
        Optional<String> pubDate = item.getPubDate();

        return pubDate.map(s -> parseDateTime(s, dateTimeFormat)).orElse(Instant.MIN);

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
            parseDateTime(firstRssFeedPubDate.get(), feedConfig.dateFormatterPattern());
        } catch (DateTimeParseException _) {
            return false;
        }
        return true;
    }

    private Optional<Instant> convertDateTimeToInstant(RSSFeed feedConfig) {
        Optional<RssFeedRecord> feedOptional = getRssFeedRecordFromDatabase(feedConfig);
        String dateTimeFormat = feedConfig.dateFormatterPattern();

        if (feedOptional.isEmpty() || dateTimeFormat.isEmpty()) {
            return Optional.empty();
        }

        RssFeedRecord feedRecord = feedOptional.get();
        String lastDate = feedRecord.getLastDate();

        ZonedDateTime zonedDateTime;
        try {
            zonedDateTime =
                    ZonedDateTime.parse(lastDate, DateTimeFormatter.ofPattern(dateTimeFormat));
        } catch (DateTimeParseException exception) {
            logger.error(
                    "Attempted to convert date time from database ({}) to instant, but failed:",
                    lastDate, exception);
            return Optional.empty();
        }

        return Optional.of(zonedDateTime.toInstant());
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
     * Provides the message from an RSS item used for sending RSS posts.
     *
     * @param item the RSS item to construct the embed message from
     * @param feedConfig the configuration of the RSS feed
     * @return the constructed message containing information from the RSS item
     */
    private MessageCreateData constructMessage(Item item, RSSFeed feedConfig) {
        if (item.getLink().filter(isVideoLink).isPresent()) {
            // Automatic video previews are created on normal messages, not on embeds
            return MessageCreateData.fromContent(item.getLink().orElseThrow());
        }

        final EmbedBuilder embedBuilder = new EmbedBuilder();
        String title = item.getTitle().orElse("No title");
        String titleLink = item.getLink().orElse("");
        Optional<String> rawDescription = item.getDescription();

        // Set the item's timestamp to the embed if found
        item.getPubDate()
            .ifPresent(dateTime -> embedBuilder
                .setTimestamp(parseDateTime(dateTime, feedConfig.dateFormatterPattern())));

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

        return MessageCreateData.fromEmbeds(embedBuilder.build());
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
            List<Item> items = rssReader.read(rssUrl).toList();
            circuitBreaker.invalidate(rssUrl);
            return items;
        } catch (IOException e) {
            FailureState oldState = circuitBreaker.getIfPresent(rssUrl);
            int newCount = (oldState == null) ? 1 : oldState.count() + 1;

            if (newCount >= DEAD_RSS_FEED_FAILURE_THRESHOLD) {
                logger.error(
                        "Possibly dead RSS feed URL: {} - Failed {} times. Please remove it from config.",
                        rssUrl, newCount);
            }
            circuitBreaker.put(rssUrl, new FailureState(newCount, Instant.now()));

            long blacklistedHours = calculateWaitHours(newCount);

            logger.debug(
                    "RSS fetch failed for {} (Attempt #{}). Backing off for {} hours. Reason: {}",
                    rssUrl, newCount, blacklistedHours, e.getMessage(), e);

            return List.of();
        }
    }

    /**
     * Helper function for parsing a given date value to a {@link Instant} with a given format.
     *
     * @param dateTime the date and time value to parse, can be null
     * @return the parsed {@link Instant} object
     * @throws DateTimeParseException if the date cannot be parsed
     */
    private static Instant parseDateTime(@Nullable String dateTime, String datePattern)
            throws DateTimeParseException {
        if (dateTime == null) {
            return Instant.MIN;
        }

        return Instant.from(DateTimeFormatter.ofPattern(datePattern).parse(dateTime));
    }

    private long calculateWaitHours(int failureCount) {
        return (long) Math.min(Math.pow(BACKOFF_BASE, failureCount - BACKOFF_EXPONENT_OFFSET),
                MAX_BACKOFF_HOURS);
    }

    private boolean isBackingOff(String url) {
        FailureState state = circuitBreaker.getIfPresent(url);
        if (state == null) {
            return false;
        }

        long waitHours = calculateWaitHours(state.count());
        Instant retryAt = state.lastFailure().plus(waitHours, ChronoUnit.HOURS);

        return Instant.now().isBefore(retryAt);
    }
}
