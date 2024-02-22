package org.togetherjava.tjbot.features.javamail;

import com.apptasticsoftware.rssreader.Item;
import com.apptasticsoftware.rssreader.RssReader;
import com.ctc.wstx.shaded.msv.org_isorelax.verifier.impl.SAXEventGenerator;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.RSSFeed;
import org.togetherjava.tjbot.features.Routine;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class JavaMailRSSRoutine implements Routine {

    private static final Logger logger = LoggerFactory.getLogger(JavaMailRSSRoutine.class);
    private static final RssReader RSS_READER = new RssReader();
    private static Predicate<String> javaNewsChannelPredicate;
    private final List<RSSFeed> feeds;

    public JavaMailRSSRoutine(Config config) {
        this.feeds = config.getRssFeeds();

        javaNewsChannelPredicate =
                Pattern.compile(config.getJavaNewsChannelPattern()).asPredicate();
    }

    @Override
    public Schedule createSchedule() {
        // TODO: Make this adjustable in the future
        return new Schedule(ScheduleMode.FIXED_DELAY, 0, 10, TimeUnit.MINUTES);
    }

    @Override
    public void runRoutine(JDA jda) {
        // TODO: REMOVE ME
        // 1. make http request and check if the latest rss feed id matches last saved RSS feed id
        // 2. if it doesn't then post the new one and store it in db.

        final TextChannel channel = jda.getTextChannelCache()
            .stream()
            .filter(c -> javaNewsChannelPredicate.test(c.getName()))
            .findFirst()
            .orElse(null);

        if (channel == null) {
            logger.warn("Could not find the Java news channel");
            return;
        }

        // parse latest channel message and get the date that the rss feed from that is from.
        // footer format is {DATE} | https://wiki.openjdk.org (RSS CHANNEL ID)
        List<String[]> lastDates = channel.getIterableHistory()
            .stream()
            .filter(message -> message.getAuthor().getId().equals(jda.getSelfUser().getId()))
            .map(message -> message.getEmbeds().getFirst().getFooter().getText().split(" | "))
            .filter(footer -> Arrays.stream(footer).findAny().isPresent())
            .collect(Collectors.toList());

        // loop through lastDates and check if we are still tracking that rss feed and then call
        // getAfter
        for (String[] footer : lastDates) {
            RSSFeed feed = null;
            if (footer.length != 2) {
                continue;
            }

            // check if we are still tracking
            long date = parseDate(footer[0]);
            for (RSSFeed trackedFeed : feeds) {
                // they likely won't match exactly.
                if (trackedFeed.url().contains(footer[1])) {
                    feed = trackedFeed;
                }
            }

            if (feed == null) {
                continue;
            }

            // now we can handle all posts for this specific rss feed
            List<Item> posts = getAfter(feed.url(), date);
        }


    }

    private static void sendRSSPost() {

    }

    @SuppressWarnings("static-access")
    private static EmbedBuilder constructEmbedMessage(Item item, TextChannel channel) {
        final EmbedBuilder embedBuilder = new EmbedBuilder();

        // TODO: You do this stuff
        embedBuilder.setTitle(item.getTitle().get(), item.getLink().get());
        embedBuilder.setDescription(
                StringEscapeUtils.unescapeHtml4(item.getDescription().get().substring(0, 150))
                        + "...");
        embedBuilder
            .setFooter("%s | %d".format(item.getPubDate().get(), item.getChannel().getLink()));
        return embedBuilder;
    }


    private static List<Item> getAfter(String rssUrl, long date) {
        List<Item> rssList = fetchRss(rssUrl).orElse(null);
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
        return fetchRss(rssUrl).orElseThrow().stream().findFirst();
    }

    /**
     * Fetches new items from a given RSS url.
     */
    private static Optional<List<Item>> fetchRss(String rssUrl) {
        try {
            return Optional.of(RSS_READER.read(rssUrl).collect(Collectors.toList()));
        } catch (Exception e) {
            logger.warn("Could not fetch RSS from URL ({})", rssUrl);
            return Optional.empty();
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
