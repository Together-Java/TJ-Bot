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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class TeXCommand extends SlashCommandAdapter {

	public static final String RENDERING_ERROR = "There was an error generating the image";
	public static final String INVALID_LATEX = "That is an invalid latex";
	public static final float DEFAULT_IMAGE_SIZE = 40F;
	public static final int BACKGROUND_HEX_CODE = 0x4396BE;
	public static final Color BACKGROUND_COLOR = new Color(BACKGROUND_HEX_CODE);
	public static final int FOREGROUND_HEX_CODE = 0x01EC09;
	public static final Color FOREGROUND_COLOR = new Color(FOREGROUND_HEX_CODE);
	public static final Logger logger = LoggerFactory.getLogger(TeXCommand.class);

	public TeXCommand() {
		super("tex", "This command accepts a latex expression and generates an image corresponding to it.",
				SlashCommandVisibility.GUILD);
		getData().addOption(OptionType.STRING, "latex", "The latex which is rendered as an image");
	}

	@Override
	public void onSlashCommand(@NotNull final SlashCommandEvent event) {
		TeXFormula formula;
		try {
			formula = new TeXFormula(event.getOption("latex")
					.getAsString());
		} catch (ParseException pe) {
			event.reply(INVALID_LATEX)
					.setEphemeral(true)
					.queue();
			return;
		}
		BufferedImage image =
				(BufferedImage) formula.createBufferedImage(TeXConstants.STYLE_DISPLAY, DEFAULT_IMAGE_SIZE,
						FOREGROUND_COLOR, BACKGROUND_COLOR);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		try {
			ImageIO.write(image, "png", baos);
		} catch (IOException ioe) {
			event.reply(RENDERING_ERROR)
					.setEphemeral(true)
					.queue();
			return;
		}
		InputStream is = new ByteArrayInputStream(baos.toByteArray());
		event.getChannel()
				.sendFile(is, "unknown.png")
				.queue();
	}
}
