package org.togetherjava.tjbot.commands.mathcommands.wolframalpha;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WolframAlphaCommandTest {
    private static final Logger logger = LoggerFactory.getLogger(
            org.togetherjava.tjbot.commands.mathcommands.wolframalpha.WolframAlphaCommandTest.class);

    @Test
    void compareImagesTest() {
        BufferedImage image1 = new BufferedImage(100, 100, 6);
        BufferedImage image2 = new BufferedImage(100, 100, 6);
        image1.getGraphics().setColor(Color.RED);
        image2.getGraphics().setColor(Color.YELLOW);
        assertFalse(WolframAlphaCommandUtils.compareImages(image1, image2));
    }

    @Test
    void combineImagesTest() throws IOException {
        BufferedImage image1 = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB_PRE);
        BufferedImage image2 = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB_PRE);
        BufferedImage image3 = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB_PRE);
        BufferedImage mergedImage = new BufferedImage(100, 300, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics g1 = image1.getGraphics();
        g1.setColor(Color.RED);
        g1.fillRect(0, 0, 100, 100);
        File img1 = Path
            .of("C:\\Users\\Abc\\IdeaProjects\\TJ-Bot-baseRepo\\application\\src\\test\\java\\org\\togetherjava\\tjbot\\commands\\mathcommands\\wolframalpha\\img1.png")
            .toFile();
        ImageIO.write(image1, "png", img1);
        logger.info("image 1 successfully written");
        Graphics g2 = image2.getGraphics();
        g2.setColor(Color.BLUE);
        g2.fillRect(0, 0, 100, 100);
        File img2 = Path
            .of("C:\\Users\\Abc\\IdeaProjects\\TJ-Bot-baseRepo\\application\\src\\test\\java\\org\\togetherjava\\tjbot\\commands\\mathcommands\\wolframalpha\\img2.png")
            .toFile();
        ImageIO.write(image2, "png", img2);
        logger.info("image 2 successfully written");
        Graphics g3 = image3.getGraphics();
        g3.setColor(Color.YELLOW);
        g3.fillRect(0, 0, 100, 100);
        ImageIO.write(image3, "png", new File(
                "C:\\Users\\Abc\\IdeaProjects\\TJ-Bot-baseRepo\\application\\src\\test\\java\\org\\togetherjava\\tjbot\\commands\\mathcommands\\wolframalpha\\img3.png"));
        logger.info("image 3 successfully written");
        Graphics g4 = mergedImage.getGraphics();
        g4.setColor(Color.RED);
        g4.fillRect(0, 0, 100, 100);
        g4.setColor(Color.BLUE);
        g4.fillRect(0, 100, 100, 100);
        g4.setColor(Color.YELLOW);
        g4.fillRect(0, 200, 100, 100);
        ImageIO.write(mergedImage, "png", new File(
                "C:\\Users\\Abc\\IdeaProjects\\TJ-Bot-baseRepo\\application\\src\\test\\java\\org\\togetherjava\\tjbot\\commands\\mathcommands\\wolframalpha\\manuallyMergedImg.png"));
        BufferedImage mergedByMethod =
                WolframAlphaCommandUtils.combineImages(List.of(image1, image2, image3), 300);
        ImageIO.write(mergedByMethod, "png", new File(
                "C:\\Users\\Abc\\IdeaProjects\\TJ-Bot-baseRepo\\application\\src\\test\\java\\org\\togetherjava\\tjbot\\commands\\mathcommands\\wolframalpha\\methodMergedimage.png"));
        assertTrue(WolframAlphaCommandUtils.compareImages(mergedImage, mergedByMethod));
    }
}

