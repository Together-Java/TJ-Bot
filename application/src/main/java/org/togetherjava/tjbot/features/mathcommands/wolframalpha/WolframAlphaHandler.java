package org.togetherjava.tjbot.features.mathcommands.wolframalpha;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import io.mikael.urlbuilder.UrlBuilder;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.features.mathcommands.wolframalpha.api.DidYouMean;
import org.togetherjava.tjbot.features.mathcommands.wolframalpha.api.DidYouMeans;
import org.togetherjava.tjbot.features.mathcommands.wolframalpha.api.Error;
import org.togetherjava.tjbot.features.mathcommands.wolframalpha.api.FutureTopic;
import org.togetherjava.tjbot.features.mathcommands.wolframalpha.api.LanguageMessage;
import org.togetherjava.tjbot.features.mathcommands.wolframalpha.api.Pod;
import org.togetherjava.tjbot.features.mathcommands.wolframalpha.api.QueryResult;
import org.togetherjava.tjbot.features.mathcommands.wolframalpha.api.RelatedExample;
import org.togetherjava.tjbot.features.mathcommands.wolframalpha.api.RelatedExamples;
import org.togetherjava.tjbot.features.mathcommands.wolframalpha.api.SubPod;
import org.togetherjava.tjbot.features.mathcommands.wolframalpha.api.Tip;
import org.togetherjava.tjbot.features.mathcommands.wolframalpha.api.Tips;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Handles Wolfram Alpha API query responses.
 */
final class WolframAlphaHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(WolframAlphaHandler.class);
    private static final XmlMapper XML = new XmlMapper();
    private static final Color AMBIENT_COLOR = Color.decode("#4290F5");
    private static final String SERVICE_NAME = "Wolfram|Alpha";
    /**
     * WolframAlpha API endpoint for regular users (web frontend).
     */
    private static final String USER_API_ENDPOINT = "https://www.wolframalpha.com/input";
    /**
     * The max height to allow for images, in pixel. Images larger than this are downscaled by
     * Discord and do not provide a nice user experience anymore.
     */
    private static final int MAX_IMAGE_HEIGHT_PX = 400;
    /**
     * Maximum amount of tiles to send.
     * <p>
     * One embed is used as initial description and summary.
     */
    private static final int MAX_TILES = Message.MAX_EMBED_COUNT - 1;

    private final String query;
    private final String userApiQuery;

    /**
     * Creates a new instance
     *
     * @param query the original query send to the API
     */
    WolframAlphaHandler(String query) {
        this.query = query;

        userApiQuery = UrlBuilder.fromString(USER_API_ENDPOINT)
            .addParameter("i", query)
            .toUri()
            .toString();
    }

    /**
     * Handles the given response and returns a user-friendly message that can be displayed.
     *
     * @param apiResponse response of the Wolfram Alpha API query
     * @return user-friendly message for display, as list of embeds
     */
    HandlerResponse handleApiResponse(HttpResponse<String> apiResponse) {
        // Check status code
        int statusCode = apiResponse.statusCode();
        if (statusCode != HttpURLConnection.HTTP_OK) {
            LOGGER.warn("Wolfram Alpha API returned an unexpected status code: {}", statusCode);
            return responseOf("Sorry, the Wolfram Alpha API failed for an unknown reason.");
        }

        // Parse XML response
        String queryResultXml = apiResponse.body();
        QueryResult queryResult;
        try {
            queryResult = XML.readValue(queryResultXml, QueryResult.class);
        } catch (IOException e) {
            LOGGER.warn(
                    "Wolfram Alpha API returned a response (for query: '{}') that can not be parsed into a QueryResult: {}",
                    query, queryResultXml, e);
            return responseOf(
                    "Sorry, the Wolfram Alpha API responded with something I do not understand.");
        }

        // Handle unsuccessful
        if (!queryResult.isSuccess()) {
            if (queryResult.isError()) {
                Error error = queryResult.getErrorTag();
                LOGGER.error(
                        "Received an error from the Wolfram Alpha API (for query: '{}'). Code: {}, message: {}",
                        query, error.getCode(), error.getMessage());
                return responseOf("Sorry, the Wolfram Alpha API responded with an error.");
            }

            return handleMisunderstoodQuery(queryResult);
        }

        return handleSuccessfulResponse(queryResult);
    }

    private HandlerResponse handleMisunderstoodQuery(QueryResult result) {
        StringJoiner output = new StringJoiner("\n");
        output.add("Sorry, I did not understand your query.");

        Tips tips = result.getTips();
        if (tips != null && tips.getCount() != 0) {
            output.add("\nHere are some tips:\n"
                    + createBulletPointList(tips.getTipList(), Tip::getText));
        }

        FutureTopic futureTopic = result.getFutureTopic();
        if (futureTopic != null) {
            output
                .add("\n" + "The topic '%s' is not supported yet, but will be added in the future."
                    .formatted(futureTopic.getTopic()));
        }

        LanguageMessage languageMessage = result.getLanguageMessage();
        if (languageMessage != null) {
            // "Wolfram|Alpha does not yet support German."
            // "Wolfram|Alpha versteht noch kein Deutsch."
            output.add("\n" + languageMessage.getEnglish());
            output.add(languageMessage.getOther());
        }

        DidYouMeans didYouMeans = result.getDidYouMeans();
        if (didYouMeans != null && didYouMeans.getCount() != 0) {
            output.add("\nDid you perhaps mean:\n" + createBulletPointList(
                    didYouMeans.getDidYouMeanTips(), DidYouMean::getMessage));
        }

        RelatedExamples relatedExamples = result.getRelatedExamples();
        if (relatedExamples != null && relatedExamples.getCount() != 0) {
            output.add("\nHere are some related examples:\n" + createBulletPointList(
                    relatedExamples.getRelatedExampleTips(), RelatedExample::getCategoryThumb));
        }

        return responseOf(output.toString());
    }

    private static <E> String createBulletPointList(Collection<? extends E> elements,
            Function<E, String> elementToText) {
        return elements.stream()
            .map(elementToText)
            .map(text -> "• " + text)
            .collect(Collectors.joining("\n"));
    }

    private HandlerResponse handleSuccessfulResponse(QueryResult queryResult) {
        StringJoiner messages = new StringJoiner("\n\n");
        messages.add("Click the link to see full results.");

        if (!queryResult.getTimedOutPods().isEmpty()) {
            messages.add("Some of my calculation took very long, so I cancelled them.");
        }

        // Render all the pods and sub-pods
        Collection<BufferedImage> images = new ArrayList<>();
        for (Pod pod : queryResult.getPods()) {
            images.add(WolframAlphaImages.renderTitle(pod.getTitle() + ":"));

            for (SubPod subPod : pod.getSubPods()) {
                try {
                    images.add(WolframAlphaImages.renderSubPod(subPod));
                } catch (IOException | URISyntaxException e) {
                    LOGGER.error(
                            "Failed to render sub pod (title: '{}') from pod (title: '{}') from the WolframAlpha response (for query: '{}')",
                            subPod.getTitle(), pod.getTitle(), query, e);
                    return responseOf(
                            "Sorry, the Wolfram Alpha API responded with something I do not understand.");
                }
            }
        }
        images.add(WolframAlphaImages.renderFooter());

        // Images will be displayed as tiles in Discord embeds
        List<BufferedImage> tiles =
                WolframAlphaImages.combineImagesIntoTiles(images, MAX_IMAGE_HEIGHT_PX);

        List<BufferedImage> tilesToDisplay = tiles.subList(0, Math.min(tiles.size(), MAX_TILES));
        if (tilesToDisplay.size() < tiles.size()) {
            messages.add("That's a lot of results, I had to cut off a few of them.");
        }

        return responseOf(messages.toString(), tilesToDisplay);
    }

    private HandlerResponse responseOf(CharSequence text) {
        MessageEmbed embed = new EmbedBuilder().setTitle(buildTitle(), userApiQuery)
            .setDescription(text)
            .setColor(AMBIENT_COLOR)
            .build();

        return new HandlerResponse(List.of(embed), List.of());
    }

    private HandlerResponse responseOf(CharSequence text,
            Collection<? extends BufferedImage> tiles) {
        List<MessageEmbed> embeds = new ArrayList<>();
        embeds.add(new EmbedBuilder().setTitle(buildTitle(), userApiQuery)
            .setDescription(text)
            .setColor(AMBIENT_COLOR)
            .build());

        List<Attachment> attachments = new ArrayList<>(tiles.size());

        int i = 0;
        for (BufferedImage tile : tiles) {
            String tileTitle = "result%d.%s".formatted(i, WolframAlphaImages.IMAGE_FORMAT);

            attachments.add(new Attachment(tileTitle, WolframAlphaImages.imageToBytes(tile)));
            embeds.add(new EmbedBuilder().setColor(AMBIENT_COLOR)
                .setImage("attachment://" + tileTitle)
                .build());

            i++;
        }

        return new HandlerResponse(embeds, attachments);
    }

    private String buildTitle() {
        return query + " - " + SERVICE_NAME;
    }

    record HandlerResponse(List<MessageEmbed> embeds, List<Attachment> attachments) {
    }

    record Attachment(String name, byte[] data) {
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Attachment that = (Attachment) o;
            return name.equals(that.name) && Arrays.equals(data, that.data);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(name);
            result = 31 * result + Arrays.hashCode(data);
            return result;
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", Attachment.class.getSimpleName() + "[", "]")
                .add("name='" + name + "'")
                .add("data=" + Arrays.toString(data))
                .toString();
        }
    }
}
