package org.togetherjava.tjbot.features.basic;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.Routine;
import org.togetherjava.tjbot.features.SlashCommandAdapter;

import javax.imageio.ImageIO;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public final class GamePoc extends SlashCommandAdapter implements Routine {
    private record Pos(int x, int y) {
    }

    private static final int WIDTH = 25;
    private static final int HEIGHT = 15;
    private static final int TILE_SIZE = 50;
    private static final String IMAGE_FORMAT = "png";

    private GameMessageId gameMessageId = null;

    private Pos apple = null;
    private Pos snakeHead = null;
    private List<Pos> snakeBody = null;

    public GamePoc() {
        super("game", "Start a game", CommandVisibility.GUILD);
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        apple = new Pos(ThreadLocalRandom.current().nextInt(WIDTH),
                ThreadLocalRandom.current().nextInt(HEIGHT));
        snakeHead = new Pos(ThreadLocalRandom.current().nextInt(WIDTH),
                ThreadLocalRandom.current().nextInt(HEIGHT));
        snakeBody = List.of();

        event.reply("Started a game").queue();

        List<Button> first = Stream.of("⬆", "⬅", "➡", "⬇")
            .map(input -> Button.of(ButtonStyle.SECONDARY, generateComponentId(input), input))
            .toList();

        event.getChannel().sendMessage("Game").addActionRow(first).queue(this::recordGameMessage);
    }

    @Override
    public void onButtonClick(ButtonInteractionEvent event, List<String> args) {
        Pos change = switch (args.get(0)) {
            case "⬆" -> new Pos(0, -1);
            case "⬅" -> new Pos(-1, 0);
            case "➡" -> new Pos(1, 0);
            case "⬇" -> new Pos(0, 1);
            default -> throw new AssertionError("unknown");
        };

        int x = snakeHead.x + change.x;
        x = Math.max(0, Math.min(WIDTH, x));
        int y = snakeHead.y + change.y;
        y = Math.max(0, Math.min(HEIGHT, y));

        snakeHead = new Pos(x, y);
        event.deferEdit().queue();
    }

    @Override
    public Schedule createSchedule() {
        return new Schedule(ScheduleMode.FIXED_RATE, 3, 3, TimeUnit.SECONDS);
    }

    private void recordGameMessage(Message gameMessage) {
        gameMessageId = GameMessageId.fromMessage(gameMessage);
    }

    private record GameMessageId(long channelId, long messageId) {
        static GameMessageId fromMessage(Message message) {
            return new GameMessageId(message.getChannel().getIdLong(), message.getIdLong());
        }
    }

    @Override
    public void runRoutine(JDA jda) {
        if (gameMessageId == null) {
            return;
        }

        byte[] data = imageToBytes(render());
        MessageEditData message = new MessageEditBuilder()
            .setAttachments(FileUpload.fromData(data, "game." + IMAGE_FORMAT))
            .build();

        // String data = imageToText(render());
        // MessageEditData message = new MessageEditBuilder().setContent("```\n" + data +
        // "\n```").build();

        TextChannel channel = jda.getTextChannelById(gameMessageId.channelId);

        channel.editMessageById(gameMessageId.messageId, message).queue();
    }

    private BufferedImage render() {
        BufferedImage img = new BufferedImage(WIDTH * TILE_SIZE, HEIGHT * TILE_SIZE,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();

        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                Pos current = new Pos(x, y);

                Color color;
                if (current.equals(apple)) {
                    color = Color.RED;
                } else if (current.equals(snakeHead)) {
                    color = Color.BLACK;
                } else if (snakeBody.contains(current)) {
                    color = Color.GRAY;
                } else {
                    color = Color.WHITE;
                }
                g.setColor(color);

                g.fillRect(x * TILE_SIZE, y * TILE_SIZE, TILE_SIZE, TILE_SIZE);
            }
        }

        return img;
    }

    static byte[] imageToBytes(RenderedImage img) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(img, IMAGE_FORMAT, outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String imageToText(final BufferedImage image) {
        StringBuilder sb = new StringBuilder((image.getWidth() + 1) * image.getHeight());
        for (int y = 0; y < image.getHeight(); y++) {
            if (sb.length() != 0)
                sb.append("\n");
            for (int x = 0; x < image.getWidth(); x++) {
                Color pixelColor = new Color(image.getRGB(x, y));
                double gValue = (double) pixelColor.getRed() * 0.2989
                        + (double) pixelColor.getBlue() * 0.5870
                        + (double) pixelColor.getGreen() * 0.1140;
                final char s = returnStrPos(gValue);
                sb.append(s);
            }
        }
        return sb.toString();
    }

    /**
     * Create a new string and assign to it a string based on the grayscale value. If the grayscale
     * value is very high, the pixel is very bright and assign characters such as . and , that do
     * not appear very dark. If the grayscale value is very lowm the pixel is very dark, assign
     * characters such as # and @ which appear very dark.
     *
     * @param g grayscale
     * @return char
     */
    private char returnStrPos(double g) {
        final char str;
        if (g >= 230.0) {
            str = ' ';
        } else if (g >= 200.0) {
            str = '.';
        } else if (g >= 180.0) {
            str = '*';
        } else if (g >= 160.0) {
            str = ':';
        } else if (g >= 130.0) {
            str = 'o';
        } else if (g >= 100.0) {
            str = '&';
        } else if (g >= 70.0) {
            str = '8';
        } else if (g >= 50.0) {
            str = '#';
        } else {
            str = '@';
        }
        return str;
    }
}
