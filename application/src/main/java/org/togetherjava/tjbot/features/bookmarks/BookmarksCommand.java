package org.togetherjava.tjbot.features.bookmarks;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;

import java.util.List;
import java.util.Objects;

/**
 * The bookmarks command is used for managing and viewing bookmarks. A bookmark is a link to a help
 * thread that can have a note so you can easily remember why you bookmarked a help thread. Writing
 * to the database and showing the list/remove messages is not done by this class, that is handled
 * by the {@link BookmarksSystem}. This class only checks if you are able to add a bookmark in the
 * current channel and tells the {@link BookmarksSystem} to do the rest.
 * <p>
 * Usage:
 * 
 * <pre>
 * /bookmarks add [note]
 * /bookmarks list
 * /bookmarks remove
 * </pre>
 */
public final class BookmarksCommand extends SlashCommandAdapter {

    private static final Logger logger = LoggerFactory.getLogger(BookmarksCommand.class);

    public static final String COMMAND_NAME = "bookmarks";
    public static final String SUBCOMMAND_ADD = "add";
    public static final String SUBCOMMAND_LIST = "list";
    public static final String SUBCOMMAND_REMOVE = "remove";
    public static final String ADD_BOOKMARK_NOTE_OPTION = "note";

    private static final MessageEmbed NOT_A_HELP_THREAD_EMBED =
            BookmarksSystem.createFailureEmbed("You can only bookmark help threads.");

    private static final MessageEmbed ALREADY_BOOKMARKED_EMBED =
            BookmarksSystem.createFailureEmbed("You have already bookmarked this channel.");

    private static final MessageEmbed BOOKMARK_ADDED_EMBED =
            BookmarksSystem.createSuccessEmbed("Your bookmark was added.");

    private static final MessageEmbed BOOKMARK_LIMIT_USER_EMBED = BookmarksSystem
        .createFailureEmbed(
                "You have exceeded your bookmarks limit of `%d`. Please delete some of your other bookmarks."
                    .formatted(BookmarksSystem.MAX_BOOKMARK_COUNT_PER_USER));

    private static final MessageEmbed BOOKMARK_LIMIT_TOTAL_EMBED = BookmarksSystem
        .createWarningEmbed(
                """
                        You cannot add a bookmark right now because the total amount of bookmarks has exceeded its limit.
                        Please wait a bit until some of them have been deleted or contact a moderator.
                        Sorry for the inconvenience.
                        """);

    private final BookmarksSystem bookmarksSystem;
    private final BookmarksListRemoveHandler listRemoveHandler;

    /**
     * Creates a new instance and registers every sub command.
     *
     * @param bookmarksSystem The {@link BookmarksSystem} to request pagination and manage bookmarks
     */
    public BookmarksCommand(BookmarksSystem bookmarksSystem) {
        super(COMMAND_NAME, "Bookmark help threads so that you can easily look them up again",
                CommandVisibility.GLOBAL);
        this.bookmarksSystem = bookmarksSystem;
        listRemoveHandler =
                new BookmarksListRemoveHandler(bookmarksSystem, this::generateComponentId);

        OptionData addNoteOption = new OptionData(OptionType.STRING, ADD_BOOKMARK_NOTE_OPTION,
                "Your personal comment on this bookmark")
            .setMaxLength(BookmarksSystem.MAX_NOTE_LENGTH)
            .setRequired(false);

        SubcommandData addSubCommand = new SubcommandData(SUBCOMMAND_ADD,
                "Bookmark this help thread, so that you can easily look it up again")
            .addOptions(addNoteOption);

        SubcommandData listSubCommand =
                new SubcommandData(SUBCOMMAND_LIST, "List all of your bookmarks");

        SubcommandData removeSubCommand =
                new SubcommandData(SUBCOMMAND_REMOVE, "Remove some of your bookmarks");

        getData().addSubcommands(addSubCommand, listSubCommand, removeSubCommand);
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        String subCommandName = Objects.requireNonNull(event.getSubcommandName());

        switch (subCommandName) {
            case SUBCOMMAND_ADD -> addBookmark(event);
            case SUBCOMMAND_LIST -> listRemoveHandler.handleListRequest(event);
            case SUBCOMMAND_REMOVE -> listRemoveHandler.handleRemoveRequest(event);
            default -> throw new IllegalArgumentException("Unknown subcommand");
        }
    }

    @Override
    public void onButtonClick(ButtonInteractionEvent event, List<String> args) {
        listRemoveHandler.onButtonClick(event, args);
    }

    @Override
    public void onStringSelectSelection(StringSelectInteractionEvent event, List<String> args) {
        listRemoveHandler.onSelectMenuSelection(event, args);
    }

    private void addBookmark(SlashCommandInteractionEvent event) {
        long userID = event.getUser().getIdLong();
        long channelID = event.getChannel().getIdLong();
        String note = event.getOption(ADD_BOOKMARK_NOTE_OPTION, OptionMapping::getAsString);

        if (!handleCanAddBookmark(event)) {
            return;
        }

        bookmarksSystem.addBookmark(userID, channelID, note);

        sendResponse(event, BOOKMARK_ADDED_EMBED);
    }

    private boolean handleCanAddBookmark(SlashCommandInteractionEvent event) {
        MessageChannelUnion channel = event.getChannel();
        long channelID = channel.getIdLong();
        long userID = event.getUser().getIdLong();

        if (!bookmarksSystem.isHelpThread(channel)) {
            sendResponse(event, NOT_A_HELP_THREAD_EMBED);
            return false;
        }

        if (bookmarksSystem.didUserBookmarkChannel(userID, channelID)) {
            sendResponse(event, ALREADY_BOOKMARKED_EMBED);
            return false;
        }

        long bookmarkCountTotal = bookmarksSystem.getTotalBookmarkCount();
        if (bookmarkCountTotal == BookmarksSystem.WARN_BOOKMARK_COUNT_TOTAL) {
            logger.warn("""
                    The bookmark limit will be reached soon (`{}/{}` bookmarks)!
                    If the limit is reached no new bookmarks can be added!
                    Please delete some bookmarks!
                    """, BookmarksSystem.WARN_BOOKMARK_COUNT_TOTAL,
                    BookmarksSystem.MAX_BOOKMARK_COUNT_TOTAL);
        }
        if (bookmarkCountTotal == BookmarksSystem.MAX_BOOKMARK_COUNT_TOTAL) {
            logger.error("""
                    The bookmark limit of `{}` has been reached!
                    No new bookmarks can be added anymore!
                    Please delete some bookmarks!
                    """, BookmarksSystem.MAX_BOOKMARK_COUNT_TOTAL);
        }
        if (bookmarkCountTotal > BookmarksSystem.MAX_BOOKMARK_COUNT_TOTAL) {
            sendResponse(event, BOOKMARK_LIMIT_TOTAL_EMBED);
            return false;
        }

        long bookmarkCountUser = bookmarksSystem.getUserBookmarkCount(userID);
        if (bookmarkCountUser >= BookmarksSystem.MAX_BOOKMARK_COUNT_PER_USER) {
            sendResponse(event, BOOKMARK_LIMIT_USER_EMBED);
            return false;
        }

        return true;
    }

    private void sendResponse(SlashCommandInteractionEvent event, MessageEmbed embed) {
        event.replyEmbeds(embed).setEphemeral(true).queue();
    }
}
