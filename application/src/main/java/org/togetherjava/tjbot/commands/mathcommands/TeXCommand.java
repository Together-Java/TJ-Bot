package org.togetherjava.tjbot.commands.mathcommands;

import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.interactions.components.ButtonStyle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of a tex command which takes a string and renders an image corresponding to the
 * mathematical expression in that string.
 * <p>
 * The implemented command is {@code /tex}. This has a single option called {@code latex} which is a
 * string. If it is invalid latex or there is an error in rendering the image, it displays an error
 * message.
 */

public class TeXCommand extends SlashCommandAdapter {

    private static final String LATEX_OPTION = "latex";
    // Matches regions between two dollars, like '$foo$'.
    private static final String MATH_REGION = "(\\$[^$]+\\$)";
    private static final String TEXT_REGION = "([^$]+)";
    private static final Pattern INLINE_LATEX_REPLACEMENT =
            Pattern.compile(MATH_REGION + "|" + TEXT_REGION);
    private static final String RENDERING_ERROR = "There was an error generating the image";
    private static final float DEFAULT_IMAGE_SIZE = 40F;
    private static final Color BACKGROUND_COLOR = Color.decode("#36393F");
    private static final Color FOREGROUND_COLOR = Color.decode("#FFFFFF");
    private static final Logger logger = LoggerFactory.getLogger(TeXCommand.class);

    /**
     * Creates a new Instance.
     */
    public TeXCommand() {
        super("tex", "Renders LaTeX, also supports inline $-regions like 'see this $frac[x}{2}$'.",
                SlashCommandVisibility.GUILD);
        getData().addOption(OptionType.STRING, LATEX_OPTION,
                "The latex which is rendered as an image", true);
    }

    @Override
    public void onSlashCommand(@NotNull final SlashCommandEvent event) {
        String latex = Objects.requireNonNull(event.getOption(LATEX_OPTION)).getAsString();
        String userID = (Objects.requireNonNull(event.getMember()).getId());
        TeXFormula formula;
        try {
            if (latex.contains("$")) {
                latex = convertInlineLatexToFull(latex);
            }
            formula = new TeXFormula(latex);
        } catch (ParseException e) {
            event.reply("That is an invalid latex: " + e.getMessage()).setEphemeral(true).queue();
            return;
        }
        event.deferReply().queue();
        Image image = formula.createBufferedImage(TeXConstants.STYLE_DISPLAY, DEFAULT_IMAGE_SIZE,
                FOREGROUND_COLOR, BACKGROUND_COLOR);
        if (image.getWidth(null) == -1 || image.getHeight(null) == -1) {
            event.getHook().setEphemeral(true).editOriginal(RENDERING_ERROR).queue();
            logger.warn(
                    "Unable to render latex, image does not have an accessible width or height. Formula was {}",
                    latex);
            return;
        }
        BufferedImage renderedTextImage = new BufferedImage(image.getWidth(null),
                image.getHeight(null), BufferedImage.TYPE_4BYTE_ABGR);
        renderedTextImage.getGraphics().drawImage(image, 0, 0, null);
        ByteArrayOutputStream renderedTextImageStream = new ByteArrayOutputStream();

        try {
            ImageIO.write(renderedTextImage, "png", renderedTextImageStream);
        } catch (IOException e) {
            event.getHook().setEphemeral(true).editOriginal(RENDERING_ERROR).queue();
            logger.warn(
                    "Unable to render latex, could not convert the image into an attachable form. Formula was {}",
                    latex, e);
            return;
        }
        event.getHook()
            .editOriginal(renderedTextImageStream.toByteArray(), "tex.png")
            .setActionRow(Button.of(ButtonStyle.DANGER, generateComponentId(userID), "Delete"))
            .queue();
    }

    /**
     *
     * Converts inline latex like: {@code hello $\frac{x}{2}$ world} to full latex
     * {@code \text{hello}\frac{x}{2}\text{ world}}.
     *
     */
    @NotNull
    private String convertInlineLatexToFull(@NotNull String latex) {
        if (isInvalidInlineFormat(latex)) {
            throw new ParseException("One dollar sign is invalid, wrap whole expression with it. ");
        }
        Matcher matcher = INLINE_LATEX_REPLACEMENT.matcher(latex);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            boolean isInsideMathRegion = matcher.group(1) != null;
            if (isInsideMathRegion) {
                sb.append(matcher.group(1).replace("$", ""));
            } else {
                sb.append("\\text{").append(matcher.group(2)).append("}");
            }
        }

        return sb.toString();
    }

    private boolean isInvalidInlineFormat(String latex) {
        return latex.chars().filter(ch -> ch == '$').count() % 2 == 1;
    }

    @Override
    public void onButtonClick(@NotNull final ButtonClickEvent event,
            @NotNull final List<String> args) {
        if (!args.get(0).equals(Objects.requireNonNull(event.getMember()).getId())) {
            event.reply("You are not the person who executed the command, you cannot do that")
                .setEphemeral(true)
                .queue();
            return;
        }
        event.getMessage().delete().queue();
    }
}
