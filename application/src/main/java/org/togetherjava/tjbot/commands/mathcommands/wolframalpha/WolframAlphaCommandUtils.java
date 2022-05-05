package org.togetherjava.tjbot.commands.mathcommands.wolframalpha;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageUpdateAction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.mathcommands.wolframalpha.api.*;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

enum WolframAlphaCommandUtils {
    ;

    private static final Logger LOGGER = LoggerFactory.getLogger(WolframAlphaCommandUtils.class);

    static byte @NotNull [] imageToBytes(@NotNull RenderedImage img) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    static @NotNull BufferedImage combineImages(@NotNull Collection<? extends BufferedImage> images,
            int height) {
        int width =
                images.stream().mapToInt(BufferedImage::getWidth).max().orElse(Integer.MAX_VALUE);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
        Graphics imgGraphics = image.getGraphics();
        imgGraphics.setColor(Color.WHITE);
        imgGraphics.fillRect(0, 0, width, height);
        int resultHeight = 0;
        for (BufferedImage img : images) {
            imgGraphics.drawImage(img, 0, resultHeight, Constants.IMAGE_OBSERVER);
            resultHeight += img.getHeight(Constants.IMAGE_OBSERVER);
        }
        return image;
    }

    static boolean compareImages(@NotNull BufferedImage first, @NotNull BufferedImage second) {
        int firstWidth = first.getWidth();
        int firstHeight = first.getHeight();
        return firstWidth == second.getWidth() && firstHeight == second.getHeight()
                && IntStream.range(0, firstWidth)
                    .mapToObj(x -> IntStream.range(0, firstHeight)
                        .anyMatch(y -> first.getRGB(x, y) != second.getRGB(x, y)))
                    .noneMatch(x -> x);
    }

    static int getWidth(@NotNull String header) {
        return (int) Constants.AMBIENT_FONT
            .getStringBounds(header, new FontRenderContext(new AffineTransform(), true, true))
            .getWidth();
    }

    static @NotNull String handleMisunderstoodQuery(@NotNull QueryResult result) {
        Collection<String> output = new ArrayList<>();
        output
            .add("The Wolfram|Alpha API was unable to produce a successful result. Visit the URI");
        Tips tips = result.getTips();
        if (tips != null && tips.getCount() != 0) {
            if (tips.getCount() == 1) {
                output.add("Here is a tip: " + tips.getTips().get(0).getText());
            } else {
                output.add("Here are some tips: \n" + tips.getTips()
                    .stream()
                    .map(Tip::getText)
                    .map(text -> "• " + text)
                    .collect(Collectors.joining("\n")));
            }
        }
        FutureTopic futureTopic = result.getFutureTopic();
        if (futureTopic != null) {
            output.add(
                    "Your query is regarding The topic \"%s\" which might be supported by Wolfram Alpha in the future"
                        .formatted(futureTopic.getTopic()));
        }
        LanguageMessage languageMsg = result.getLanguageMessage();
        if (languageMsg != null) {
            output.add(languageMsg.getEnglish() + "\n" + languageMsg.getOther());
        }
        DidYouMeans didYouMeans = result.getDidYouMeans();
        if (didYouMeans != null && didYouMeans.getCount() != 0) {
            if (didYouMeans.getCount() == 1) {
                output.add("Did you mean: " + didYouMeans.getDidYouMeans().get(0).getMessage());
            } else {
                output.add("Did you mean \n" + didYouMeans.getDidYouMeans()
                    .stream()
                    .map(DidYouMean::getMessage)
                    .map(text -> "• " + text)
                    .collect(Collectors.joining("\n")));
            }
        }
        RelatedExamples relatedExamples = result.getRelatedExamples();
        if (relatedExamples != null && relatedExamples.getCount() != 0) {
            if (relatedExamples.getCount() == 1) {
                output.add("Here is a related example: "
                        + relatedExamples.getRelatedExamples().get(0).getCategoryThumb());
            } else {
                output
                    .add("Here are some related examples \n" + relatedExamples.getRelatedExamples()
                        .stream()
                        .map(RelatedExample::getCategoryThumb)
                        .map(text -> "• " + text)
                        .collect(Collectors.joining("\n")));
            }
        }
        return String.join("\n", output);
    }

    static @NotNull String handleError(@NotNull QueryResult result) {
        org.togetherjava.tjbot.commands.mathcommands.wolframalpha.api.Error error =
                result.getErrorTag();
        LOGGER.error(
                "Error getting response from Wolfram Alpha API: Error Code: {} Error Message: {}",
                error.getCode(), error.getMessage());
        return "An error occurred while getting response from the Wolfram|Alpha API. Check the URI";
    }

    static @NotNull Optional<QueryResult> parseQuery(@NotNull HttpResponse<String> response,
            @NotNull WebhookMessageUpdateAction<Message> action) {
        try {
            return Optional.of(Constants.XML.readValue(response.body(), QueryResult.class));
        } catch (IOException e) {
            action.setContent("Unexpected response from WolframAlpha API").queue();
            LOGGER.error("Unable to deserialize the class ", e);
            return Optional.empty();
        }
    }

    static @NotNull String handleSuccessfulResult(@NotNull QueryResult result,
            WebhookMessageUpdateAction<Message> action, MessageEmbed embed) {
        int filesAttached = 0;
        int resultHeight = 0;
        EmbedBuilder embedBuilder = new EmbedBuilder();
        Collection<MessageEmbed> embeds = new ArrayList<>();
        embeds.add(embed);
        Collection<BufferedImage> images = new ArrayList<>();
        List<Pod> pods = result.getPods();
        for (Pod pod : pods) {
            List<SubPod> subPods = pod.getSubPods();
            boolean firstSubPod = true;
            for (SubPod subPod : subPods) {
                WolframAlphaImage image = subPod.getImage();
                try {
                    String source = image.getSource();
                    String header = pod.getTitle();
                    int width = (firstSubPod ? Math.max(getWidth(header), image.getWidth())
                            : image.getWidth()) + Constants.IMAGE_MARGIN_WIDTH;
                    int height = image.getHeight();
                    if (firstSubPod) {
                        height += Constants.AMBIENT_TEXT_HEIGHT;
                    }
                    BufferedImage readImage =
                            new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
                    Graphics graphics = readImage.getGraphics();
                    if (firstSubPod) {
                        graphics.setFont(Constants.AMBIENT_FONT);
                        graphics.setColor(Color.WHITE);
                        graphics.setColor(Constants.AMBIENT_COLOR);
                        graphics.drawString(header, Constants.IMAGE_MARGIN_WIDTH,
                                Constants.IMAGE_MARGIN_HEIGHT);
                    }
                    graphics.drawImage(ImageIO.read(new URL(source)), Constants.IMAGE_MARGIN_WIDTH,
                            firstSubPod ? Constants.AMBIENT_TEXT_HEIGHT : 0,
                            Constants.IMAGE_OBSERVER);

                    if (filesAttached == Constants.MAX_EMBEDS) {
                        // noinspection ResultOfMethodCallIgnored
                        action.setEmbeds(embeds);
                        return "Too many images. Visit the URI";
                    }

                    if (resultHeight + image.getHeight() > Constants.MAX_IMAGE_HEIGHT_PX) {
                        BufferedImage combinedImage =
                                WolframAlphaCommandUtils.combineImages(images, resultHeight);
                        images.clear();
                        filesAttached++;
                        action = action.addFile(
                                WolframAlphaCommandUtils.imageToBytes(combinedImage),
                                "result%d.png".formatted(filesAttached));
                        resultHeight = 0;
                        embeds.add(embedBuilder
                            .setImage("attachment://result%d.png".formatted(filesAttached))
                            .build());
                    } else if (pod == pods.get(pods.size() - 1)
                            && subPod == subPods.get(subPods.size() - 1) && !images.isEmpty()) {
                        filesAttached++;
                        action = action.addFile(
                                WolframAlphaCommandUtils.imageToBytes(WolframAlphaCommandUtils
                                    .combineImages(images, resultHeight + image.getHeight())),
                                "result%d.png".formatted(filesAttached));
                        images.clear();
                        embeds.add(embedBuilder
                            .setImage("attachment://result%d.png".formatted(filesAttached))
                            .build());
                    }
                    resultHeight += readImage.getHeight();
                    images.add(readImage);
                    firstSubPod = false;
                } catch (IOException e) {
                    LOGGER.error("Failed to read image {} from the WolframAlpha response", image,
                            e);
                    return "Unable to generate message based on the WolframAlpha response";
                }
            }
        }
        // noinspection ResultOfMethodCallIgnored
        // FIXME This wont work at all if it got reassigned or similar, has to be returned or sth
        action.setEmbeds(embeds);
        return "";
    }
}
