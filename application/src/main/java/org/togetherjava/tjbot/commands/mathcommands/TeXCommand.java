package org.togetherjava.tjbot.commands.mathcommands;

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
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
import java.util.Objects;

/**
 * Implementation of a tex command which takes asks a string and renders an image corresponding to the Mathematical
 * expression in that string
 * <p>
 * The implemented command is {@code /tex}. This has a single option called {@code latex} which is a string. If it is an
 * invalid latex or there is an error in rendering the image, it displays an error message
 * </p>
 */

public class TeXCommand extends SlashCommandAdapter {

	public static final String LATEX = "LATEX";
	public static final float DEFAULT_IMAGE_SIZE = 40F;
	public static final Color BACKGROUND_COLOR = new Color(0x4396BE);
	public static final Color FOREGROUND_COLOR = new Color(0x01EC09);
	public static final Logger logger = LoggerFactory.getLogger(TeXCommand.class);

	/**
	 * creates the new TeXCommand
	 */
	public TeXCommand() {
		super("tex", "This command accepts a latex expression and generates an image corresponding to it.",
				SlashCommandVisibility.GUILD);
		getData().addOption(OptionType.STRING, LATEX, "The latex which is rendered as an image", true);
	}

	@Override
	public void onSlashCommand(@NotNull final SlashCommandEvent event) {
		TeXFormula formula;
		try {
			formula = new TeXFormula(Objects.requireNonNull(event.getOption(LATEX))
					.getAsString());
		} catch (ParseException e) {
			event.reply("That is an invalid latex")
					.setEphemeral(true)
					.queue();
			return;
		}
		event.deferReply()
				.queue();
		BufferedImage image =
				(BufferedImage) formula.createBufferedImage(TeXConstants.STYLE_DISPLAY, DEFAULT_IMAGE_SIZE,
						FOREGROUND_COLOR, BACKGROUND_COLOR);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		try {
			ImageIO.write(image, "png", baos);
		} catch (IOException e) {
			event.getHook()
					.editOriginal("There was an error generating the image")
					.queue();
			return;
		}
		event.getHook()
				.editOriginal(baos.toByteArray(), "tex.png")
				.queue();
	}
}
