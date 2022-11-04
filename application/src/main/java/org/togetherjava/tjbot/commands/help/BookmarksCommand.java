package org.togetherjava.tjbot.commands.help;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.commands.CommandVisibility;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;

import java.util.Objects;

public final class BookmarksCommand extends SlashCommandAdapter {

    private static final Logger logger = LoggerFactory.getLogger(BookmarksCommand.class);

    public static final String COMMAND_NAME = "bookmarks";
    public static final String SUBCOMMAND_ADD = "add";
    public static final String SUBCOMMAND_LIST = "list";
    public static final String SUBCOMMAND_REMOVE = "remove";
    public static final String ADD_OPTION_NOTE = "note";

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

    /**
     * Creates a new instance and registers every sub command.
     *
     * @param bookmarksSystem The {@link BookmarksSystem} to request pagination and manage bookmarks
     */
    public BookmarksCommand(BookmarksSystem bookmarksSystem) {
        super(COMMAND_NAME, "Bookmark help threads so that you can easily look them up again",
                CommandVisibility.GLOBAL);
        this.bookmarksSystem = bookmarksSystem;

        OptionData addNoteOption = new OptionData(OptionType.STRING, ADD_OPTION_NOTE,
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
            case SUBCOMMAND_LIST -> bookmarksSystem.requestListPagination(event);
            case SUBCOMMAND_REMOVE -> bookmarksSystem.requestRemovePagination(event);
            default -> throw new IllegalArgumentException("Unknown subcommand");
        }
    }

    private void addBookmark(SlashCommandInteractionEvent event) {
        long userID = event.getUser().getIdLong();
        long channelID = event.getChannel().getIdLong();
        String note = event.getOption(ADD_OPTION_NOTE, OptionMapping::getAsString);

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
                    The bookmark limit of will be reached soon (`{}/{}` bookmarks)!
                    If the limit is reached no new bookmarks can be added!
                    Please delete some bookmarks!
                    """, bookmarkCountTotal, BookmarksSystem.MAX_BOOKMARK_COUNT_TOTAL);
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
