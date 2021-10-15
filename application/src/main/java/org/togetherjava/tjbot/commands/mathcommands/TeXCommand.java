package org.togetherjava.tjbot.commands.mathcommands;

import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.interactions.components.ButtonStyle;
import org.jetbrains.annotations.NotNull;
import org.scilab.forge.jlatexmath.ParseException;
import org.scilab.forge.jlatexmath.TeXConstants;
import org.scilab.forge.jlatexmath.TeXFormula;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Implementation of a tex command which takes a string and renders an image corresponding to the
 * mathematical expression in that string.
 * <p>
 * The implemented command is {@code /tex}. This has a single option called {@code latex} which is a
 * string. If it is invalid latex or there is an error in rendering the image, it displays an error
 * message.
 */

public class TeXCommand extends SlashCommandAdapter {

    public static final String LATEX_OPTION = "latex";
    public static final String RENDERING_ERROR = "There was an error generating the image";
    public static final float DEFAULT_IMAGE_SIZE = 40F;
    public static final Color BACKGROUND_COLOR = Color.decode("0x4396BE");
    public static final Color FOREGROUND_COLOR = Color.decode("0x01EC09");
    public static final Button EDIT_BUTTON = Button.of(ButtonStyle.SUCCESS, "Edit Button", "Edit");
    public static final Button DELETE_BUTTON =
            Button.of(ButtonStyle.DANGER, "Delete Button", "Delete");
    public static final Button VIEW_SOURCE_BUTTON =
            Button.of(ButtonStyle.PRIMARY, "View-Source Button", "View Source");
    public static final List<Button> BUTTON_LIST =
            List.of(EDIT_BUTTON, DELETE_BUTTON, VIEW_SOURCE_BUTTON);
    public static final Logger logger = LoggerFactory.getLogger(TeXCommand.class);

    /**
     * Creates a new Instance.
     */
    public TeXCommand() {
        super("tex",
                "This command accepts a latex expression and generates an image corresponding to it.",
                SlashCommandVisibility.GUILD);
        getData().addOption(OptionType.STRING, LATEX_OPTION,
                "The latex which is rendered as an image", true);
    }

    @Override public void onSlashCommand(@NotNull final SlashCommandEvent event) {
        String str = Objects.requireNonNull(event.getOption(LATEX_OPTION)).getAsString();
        TeXFormula formula;
        try {
            formula = new TeXFormula(str);
        } catch (ParseException e) {
            event.reply("That is an invalid latex")
                    .addActionRow(BUTTON_LIST)
                    .setEphemeral(true)
                    .queue();
            return;
        }
        event.deferReply().queue();
        Image image = formula.createBufferedImage(TeXConstants.STYLE_DISPLAY, DEFAULT_IMAGE_SIZE,
                FOREGROUND_COLOR, BACKGROUND_COLOR);
        if (image.getWidth(null) == -1 || image.getHeight(null) == -1) {
            event.getHook().editOriginal(RENDERING_ERROR).setActionRow(BUTTON_LIST).queue();
            logger.warn(
                    "Unable to render latex, image does not have an accessible width or height. Formula was {}",
                    str);
            return;
        }
        BufferedImage bi = new BufferedImage(image.getWidth(null), image.getHeight(null),
                BufferedImage.TYPE_4BYTE_ABGR);
        bi.getGraphics().drawImage(image, 0, 0, null);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            ImageIO.write(bi, "png", baos);
        } catch (IOException e) {
            event.getHook().editOriginal(RENDERING_ERROR).setActionRow(BUTTON_LIST).queue();
            logger.warn(
                    "Unable to render latex, could not convert the image into an attachable form. Formula was {}",
                    str, e);
            return;
        }
        event.getHook()
                .editOriginal(baos.toByteArray(), "tex.png")
                .setActionRow(BUTTON_LIST)
                .queue();
    }

    @Override public void onButtonClick(@NotNull final ButtonClickEvent event,
            @NotNull final List<String> args) {
        ButtonStyle buttonStyle = Objects.requireNonNull(event.getButton()).getStyle();
        switch (buttonStyle) {
            case DANGER:
                event.getHook().deleteOriginal().queue();
                return;
            case SUCCESS:
            case PRIMARY:
        }
        super.onButtonClick(event, args);
    }
}
