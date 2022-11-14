package org.togetherjava.tjbot.commands.basic;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Modal;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.commands.CommandVisibility;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.utils.MessageUtils;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.SuggestionsConfig;

import java.awt.*;
import java.util.List;
import java.util.Optional;

/**
 * implements the /suggest command which is used to give suggestion about the server.
 */
public final class SuggestCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(SuggestCommand.class);
    private static final String COMMAND_NAME = "suggest";
    private static final String TITLE_INPUT_ID = "title";
    private static final String DESCRIPTION_INPUT_ID = "description";
    private static final String BENEFITS_INPUT_ID = "benefits";
    private static final Color AMBIENT_COLOR = new Color(120, 255, 220);
    private static final Emoji FALLBACK_UP_VOTE = Emoji.fromUnicode("⬆");
    private static final Emoji FALLBACK_DOWN_VOTE = Emoji.fromUnicode("⬇");

    private final SuggestionsConfig config;

    /**
     * Creates a new Instance.
     *
     * @param config the config to get the suggestion config from
     */
    public SuggestCommand(Config config) {
        super(COMMAND_NAME, "Give suggestions for this server", CommandVisibility.GUILD);

        this.config = config.getSuggestions();
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        TextInput titleInput = TextInput.create(TITLE_INPUT_ID, "Title", TextInputStyle.SHORT)
            .setPlaceholder("Short title describing the suggestion...")
            .setRequiredRange(1, Channel.MAX_NAME_LENGTH)
            .build();
        TextInput descriptionInput =
                TextInput.create(DESCRIPTION_INPUT_ID, "Description", TextInputStyle.PARAGRAPH)
                    .setPlaceholder("Long description of the suggestion...")
                    .setRequiredRange(1, MessageEmbed.TEXT_MAX_LENGTH)
                    .build();
        TextInput benefitsInput =
                TextInput.create(BENEFITS_INPUT_ID, "Benefits", TextInputStyle.PARAGRAPH)
                    .setPlaceholder("Benefits of this suggestion...")
                    .setMaxLength(MessageEmbed.TEXT_MAX_LENGTH)
                    .setRequired(false)
                    .build();

        Modal suggestModal = Modal.create(generateComponentId(), "Suggest")
            .addActionRows(ActionRow.of(titleInput), ActionRow.of(descriptionInput),
                    ActionRow.of(benefitsInput))
            .build();

        event.replyModal(suggestModal).queue();
    }

    @Override
    public void onModalSubmitted(ModalInteractionEvent event, List<String> args) {
        String title = event.getValue(TITLE_INPUT_ID).getAsString();
        String description = event.getValue(DESCRIPTION_INPUT_ID).getAsString();
        String benefits = event.getValue(BENEFITS_INPUT_ID).getAsString();

        User author = event.getUser();

        EmbedBuilder suggestionEmbed = new EmbedBuilder().setTitle(title)
            .setDescription(description)
            .setColor(AMBIENT_COLOR)
            .setAuthor(author.getAsTag(), null, author.getAvatarUrl());

        if (!benefits.isEmpty()) {
            suggestionEmbed.addField("Benefits", benefits, false);
        }

        Guild guild = event.getGuild();

        Optional<TextChannel> maybeSuggestionChannel = guild.getTextChannels()
            .stream()
            .filter(channel -> channel.getName().matches(config.getChannelPattern()))
            .findAny();

        if (maybeSuggestionChannel.isEmpty()) {
            logger.error("Can't find suggestion channel in guild {}", guild.getId());
            event.reply("Can't find the suggestion channel").queue();
            return;
        }

        TextChannel suggestionChannel = maybeSuggestionChannel.get();

        suggestionChannel.sendMessageEmbeds(suggestionEmbed.build()).queue(message -> {
            message.createThreadChannel(title).queue();

            reactWith(config.getDownVoteEmoteName(), FALLBACK_UP_VOTE, guild, message);
            reactWith(config.getDownVoteEmoteName(), FALLBACK_DOWN_VOTE, guild, message);
        });

        event
            .reply("Thanks for the suggestion, it has been sent in %s"
                .formatted(MessageUtils.mentionChannelById(suggestionChannel.getIdLong())))
            .queue();
    }

    private static void reactWith(String emojiName, Emoji fallbackEmoji, Guild guild,
            Message message) {
        getEmojiByName(emojiName, guild).map(message::addReaction).orElseGet(() -> {
            logger.warn(
                    "Unable to vote on a suggestion with the configured emoji ('{}'), using fallback instead.",
                    emojiName);
            return message.addReaction(fallbackEmoji);
        }).queue();
    }

    private static Optional<RichCustomEmoji> getEmojiByName(String name, Guild guild) {
        return guild.getEmojisByName(name, false).stream().findAny();
    }
}
