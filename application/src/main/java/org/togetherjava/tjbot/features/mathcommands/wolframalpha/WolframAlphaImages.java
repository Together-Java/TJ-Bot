package org.togetherjava.tjbot.features.mathcommands.wolframalpha;

import org.togetherjava.tjbot.features.mathcommands.wolframalpha.api.SubPod;
import org.togetherjava.tjbot.features.mathcommands.wolframalpha.api.WolframAlphaImage;

import javax.imageio.ImageIO;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Utility class to work with images returned by the Wolfram Alpha API. For example to render and
 * combine them.
 */
class WolframAlphaImages {
    static final String IMAGE_FORMAT = "png";
    private static final Color IMAGE_BACKGROUND = Color.WHITE;
    private static final int IMAGE_MARGIN_PX = 10;

    private static final FontRenderContext TITLE_RENDER_CONTEXT =
            new FontRenderContext(new AffineTransform(), true, true);
    private static final Color TITLE_COLOR = Color.decode("#3C3C3C");
    private static final Font TITLE_FONT = new Font("Arial", Font.BOLD, 15);

    private WolframAlphaImages() {
        throw new UnsupportedOperationException("Utility class, construction not supported");
    }

    static BufferedImage renderTitle(String title) {
        Rectangle2D titleBounds = TITLE_FONT.getStringBounds(title, TITLE_RENDER_CONTEXT);
        int widthPx = (int) Math.ceil(titleBounds.getWidth()) + 2 * IMAGE_MARGIN_PX;
        int heightPx = (int) Math.ceil(titleBounds.getHeight()) + IMAGE_MARGIN_PX;

        BufferedImage image = new BufferedImage(widthPx, heightPx, BufferedImage.TYPE_4BYTE_ABGR);
        Graphics graphics = image.getGraphics();

        graphics.setFont(TITLE_FONT);
        graphics.setColor(TITLE_COLOR);
        graphics.drawString(title, IMAGE_MARGIN_PX,
                IMAGE_MARGIN_PX + graphics.getFontMetrics().getAscent());

        return image;
    }

    static BufferedImage renderSubPod(SubPod subPod) throws IOException, URISyntaxException {
        WolframAlphaImage sourceImage = subPod.getImage();

        int widthPx = sourceImage.getWidth() + 2 * IMAGE_MARGIN_PX;
        int heightPx = sourceImage.getHeight() + IMAGE_MARGIN_PX;

        BufferedImage destinationImage =
                new BufferedImage(widthPx, heightPx, BufferedImage.TYPE_4BYTE_ABGR);
        Graphics graphics = destinationImage.getGraphics();

        graphics.drawImage(ImageIO.read(new URI(sourceImage.getSource()).toURL()), IMAGE_MARGIN_PX,
                IMAGE_MARGIN_PX, null);


        return destinationImage;
    }

    static BufferedImage renderFooter() {
        return new BufferedImage(1, IMAGE_MARGIN_PX, BufferedImage.TYPE_4BYTE_ABGR);
    }

    static List<BufferedImage> combineImagesIntoTiles(Collection<? extends BufferedImage> images,
            int maxTileHeight) {
        if (images.isEmpty()) {
            throw new IllegalArgumentException("Images must not be empty");
        }

        int widthPx = images.stream().mapToInt(BufferedImage::getWidth).max().orElseThrow();

        List<BufferedImage> destinationTiles = new ArrayList<>();

        Collection<BufferedImage> currentTile = new ArrayList<>();
        int currentTileHeight = 0;
        for (BufferedImage sourceImage : images) {
            int sourceImageHeight = sourceImage.getHeight();

            if (wouldTileBeTooLargeIfAddingImage(currentTileHeight, sourceImageHeight,
                    maxTileHeight)) {
                // Conclude tile and start the next
                destinationTiles.add(combineImages(currentTile, widthPx));

                currentTile.clear();
                currentTileHeight = 0;
            }

            // Add image to tile
            currentTile.add(sourceImage);
            currentTileHeight += sourceImageHeight;
        }

        // Conclude last tile
        destinationTiles.add(combineImages(currentTile, widthPx));

        return destinationTiles;
    }

    private static boolean wouldTileBeTooLargeIfAddingImage(int tileHeight, int heightOfImageToAdd,
            int maxTileHeight) {
        // Addition to empty tiles is always allowed, regardless of size.
        return tileHeight != 0 && tileHeight + heightOfImageToAdd > maxTileHeight;
    }

    private static BufferedImage combineImages(Collection<? extends BufferedImage> images,
            int widthPx) {
        if (images.isEmpty()) {
            throw new IllegalArgumentException("Images must not be empty");
        }

        int heightPx = images.stream().mapToInt(BufferedImage::getHeight).sum();

        BufferedImage destinationImage =
                new BufferedImage(widthPx, heightPx, BufferedImage.TYPE_4BYTE_ABGR);
        Graphics graphics = destinationImage.getGraphics();

        // Background
        graphics.setColor(IMAGE_BACKGROUND);
        graphics.fillRect(0, 0, widthPx, heightPx);

        // Combine
        int heightOffsetPx = 0;
        for (BufferedImage sourceImage : images) {
            graphics.drawImage(sourceImage, 0, heightOffsetPx, null);
            heightOffsetPx += sourceImage.getHeight(null);
        }

        return destinationImage;
    }

    static byte[] imageToBytes(RenderedImage img) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(img, IMAGE_FORMAT, outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
