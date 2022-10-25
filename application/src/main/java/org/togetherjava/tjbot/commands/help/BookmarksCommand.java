package org.togetherjava.tjbot.commands.help;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import org.togetherjava.tjbot.commands.CommandVisibility;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;

public final class BookmarksCommand extends SlashCommandAdapter {

    public static final String COMMAND_NAME = "bookmarks";
    public static final String SUBCOMMAND_ADD = "add";
    public static final String SUBCOMMAND_LIST = "list";
    public static final String SUBCOMMAND_REMOVE = "remove";
    public static final String ADD_OPTION_NOTE = "note";

    private static final MessageEmbed NOT_A_HELP_THREAD_EMBED = BookmarksSystem.simpleEmbed("""
            This command can only be run in help threads!
            """, BookmarksSystem.COLOR_FAILURE);

    private static final MessageEmbed ALREADY_BOOKMARKED_EMBED = BookmarksSystem.simpleEmbed("""
            You already bookmarked this channel!
            """, BookmarksSystem.COLOR_FAILURE);

    private static final MessageEmbed BOOKMARK_ADDED_EMBED = BookmarksSystem.simpleEmbed("""
            Bookmark added!
            """, BookmarksSystem.COLOR_SUCCESS);


    private final BookmarksSystem bookmarksSystem;

    /**
     * Creates a new instance and registers every sub command
     *
     * @param bookmarksSystem The bookmarks system to use
     */
    public BookmarksCommand(BookmarksSystem bookmarksSystem) {
        super(COMMAND_NAME, "Bookmark help threads", CommandVisibility.GLOBAL);
        this.bookmarksSystem = bookmarksSystem;

        SubcommandData addSubCommand = new SubcommandData(SUBCOMMAND_ADD,
                "Bookmark this help thread, so that you can easily look it up again").addOption(
                        OptionType.STRING, ADD_OPTION_NOTE, "A note for this bookmark", false);

        SubcommandData listSubCommand = new SubcommandData(SUBCOMMAND_LIST, "List your bookmarks");

        SubcommandData removeSubCommand =
                new SubcommandData(SUBCOMMAND_REMOVE, "Remove some of your bookmarks");

        getData().addSubcommands(addSubCommand, listSubCommand, removeSubCommand);
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        String subCommandName = event.getSubcommandName();
        if (subCommandName == null) {
            throw new AssertionError("No subcommand was provided. This should not happen");
        }

        switch (subCommandName) {
            case SUBCOMMAND_ADD -> addBookmark(event);
            case SUBCOMMAND_LIST -> bookmarksSystem.requestListPagination(event);
            case SUBCOMMAND_REMOVE -> bookmarksSystem.requestRemovePagination(event);
            default -> throw new IllegalArgumentException("Unknown subcommand");
        }
    }

    private void addBookmark(SlashCommandInteractionEvent event) {
        MessageChannelUnion channel = event.getChannel();
        long channelID = channel.getIdLong();
        long userID = event.getUser().getIdLong();
        String note = event.getOption(ADD_OPTION_NOTE, OptionMapping::getAsString);

        if (!bookmarksSystem.isHelpThread(channel)) {
            event.replyEmbeds(NOT_A_HELP_THREAD_EMBED).setEphemeral(true).queue();
            return;
        }

        if (bookmarksSystem.didUserBookmarkChannel(userID, channelID)) {
            event.replyEmbeds(ALREADY_BOOKMARKED_EMBED).setEphemeral(true).queue();
            return;
        }

        bookmarksSystem.addBookmark(userID, channelID, note);

        event.replyEmbeds(BOOKMARK_ADDED_EMBED).setEphemeral(true).queue();
    }

}
