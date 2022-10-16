package org.togetherjava.tjbot.commands.help;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import org.togetherjava.tjbot.commands.CommandVisibility;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.records.BookmarksRecord;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BookmarksCommand extends SlashCommandAdapter {

    public static final long EXPIRE_DELAY_SECONDS = 3L * 24 * 60 * 60; // 3 Days


    private static final MessageEmbed EMBED_NOT_IN_DM =
            new EmbedBuilder().setTitle("This command cannot be used in a DM!")
                .setColor(0xFF8484)
                .build();

    private static final MessageEmbed EMBED_NOT_IN_HELP_THREAD =
            new EmbedBuilder().setTitle("This command can only be used in help threads!")
                .setColor(0xFF8484)
                .build();

    private static final MessageEmbed EMBED_ALREADY_BOOKMARKED =
            new EmbedBuilder().setTitle("You have already bookmarked this thread!")
                .setColor(0xFF8484)
                .build();

    private static final MessageEmbed EMBED_BOOKMARK_REMOVED =
            new EmbedBuilder().setTitle("The bookmark was removed!").setColor(0xFFA500).build();


    private final Database database;
    private static final Map<String, BookmarksPaginator> bookmarksPaginators = new HashMap<>();


    /**
     * Creates a new instance and registers the commands with options
     */
    public BookmarksCommand(Database database) {
        super("bookmarks", "Add or view help thread bookmarks", CommandVisibility.GLOBAL);
        this.database = database;


        SubcommandData addSubCommand = new SubcommandData("add", "Adds a new boomark.")
            .addOption(OptionType.STRING, "note", "Add a note to your bookmark.", false);

        SubcommandData viewSubCommand = new SubcommandData("view", "Displays your bookmarks.");

        getData().addSubcommands(addSubCommand, viewSubCommand);
    }

    /*
     * Slash command handler section
     */

    /**
     * Handler for all slash commands
     */
    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        String subcommand = event.getSubcommandName();

        if (subcommand == null)
            throw new IllegalStateException("Bookmarks subcommand was null");

        BookmarksHelper.cleanupUserBookmarks(database, event.getUser().getIdLong());

        switch (subcommand) {
            case "add" -> onAddCommand(event);
            case "view" -> onViewCommand(event);
            default -> throw new IllegalStateException(
                    "Unknown bookmarks subcommand: " + subcommand);
        }
    }

    /**
     * Handler for the add subcommand
     */
    private void onAddCommand(SlashCommandInteractionEvent event) {

        // Has to be run in guild
        if (event.getGuild() == null) {
            event.replyEmbeds(EMBED_NOT_IN_DM).setEphemeral(true).queue();
            return;
        }

        long channelId = event.getChannel().getIdLong();
        long authorId = event.getUser().getIdLong();
        String channelName = event.getChannel().getName();
        String channelMention = event.getChannel().getAsMention();

        // Has to be run in a help thread
        if (BookmarksHelper.getHelpThread(database, channelId).isEmpty()) {
            event.replyEmbeds(EMBED_NOT_IN_HELP_THREAD).setEphemeral(true).queue();
            return;
        }

        // Dont double-add the bookmark
        if (BookmarksHelper.getBookmark(database, authorId, channelId).isPresent()) {
            event.replyEmbeds(EMBED_ALREADY_BOOKMARKED).setEphemeral(true).queue();
            return;
        }

        String threadTitle = HelpSystemHelper.HelpThreadName.ofChannelName(channelName).title();

        OptionMapping noteOption = event.getOption("note");
        String note = noteOption != null ? noteOption.getAsString() : null; // I like kotlin

        BookmarksHelper.addBookmark(database, authorId, channelId, threadTitle, note);

        event
            .replyEmbeds(new EmbedBuilder().setTitle("Bookmark added")
                .setDescription("Created bookmark for channel %s!".formatted(channelMention))
                .setColor(0x51d66c)
                .build())
            .addActionRow(Button.danger(generateAddComponentId(authorId, channelId),
                    Emoji.fromUnicode("U+1F5D1")))
            .setEphemeral(true)
            .queue();
    }

    /**
     * Handler for the view subcommand
     */
    private void onViewCommand(SlashCommandInteractionEvent event) {
        List<BookmarksRecord> bookmarks =
                BookmarksHelper.getUserBookmarks(database, event.getUser().getIdLong());

        String paginatorUUID = BookmarksPaginator.generateUUID();

        Button btnPrev = Button.primary(generateViewComponentId(ViewAction.PREV, paginatorUUID),
                Emoji.fromUnicode("U+2B05"));

        Button btnNext = Button.primary(generateViewComponentId(ViewAction.NEXT, paginatorUUID),
                Emoji.fromUnicode("U+27A1"));

        Button btnDelete = Button.danger(generateViewComponentId(ViewAction.DELETE, paginatorUUID),
                Emoji.fromUnicode("U+1F5D1"));

        Button btnRenew = Button.success(generateViewComponentId(ViewAction.RENEW, paginatorUUID),
                Emoji.fromUnicode("U+267B"));

        BookmarksPaginator bookmarksPaginator =
                new BookmarksPaginator(event, bookmarks, btnPrev, btnNext, btnDelete, btnRenew);

        bookmarksPaginators.put(paginatorUUID, bookmarksPaginator);
    }

    /*
     * Click handler section
     */

    @Override
    public void onButtonClick(ButtonInteractionEvent event, List<String> args) {
        ComponentIdType componentIdType = ComponentIdType.valueOf(args.get(0));

        if (componentIdType == ComponentIdType.ADD) {
            AddComponentIdArguments acida = AddComponentIdArguments.fromList(args);
            onAddButtonClick(event, acida);
        }

        else if (componentIdType == ComponentIdType.VIEW) {
            ViewComponentIdArguments vcida = ViewComponentIdArguments.fromList(args);
            onViewButtonClick(event, vcida);
        }
    }

    /**
     * Handler for the add message buttons
     */
    private void onAddButtonClick(ButtonInteractionEvent event, AddComponentIdArguments acida) {
        BookmarksHelper.removeBookmark(database, acida.authorId, acida.channelId);

        event.editMessageEmbeds(EMBED_BOOKMARK_REMOVED).setComponents().queue();
    }

    /**
     * Handler for the view message buttons
     */
    private void onViewButtonClick(ButtonInteractionEvent event, ViewComponentIdArguments vcida) {
        BookmarksPaginator bookmarksPaginator = bookmarksPaginators.get(vcida.paginatorUUID);

        if (bookmarksPaginator != null) {

            // Handle bookmark related actions
            BookmarksRecord currentBookmark = bookmarksPaginator.getCurrentBookmark();
            if (currentBookmark != null) {
                if (vcida.action == ViewAction.DELETE)
                    BookmarksHelper.removeBookmark(database, currentBookmark.getCreatorId(),
                            currentBookmark.getChannelId());
                else if (vcida.action == ViewAction.RENEW)
                    BookmarksHelper.renewBookmark(database, currentBookmark.getCreatorId(),
                            currentBookmark.getChannelId());
            }


            bookmarksPaginator.onButtonClick(event, vcida.action);
        }
    }

    /*
     * Component argument section
     *
     * This section provides records for the ComponentIdType arguments
     */

    private enum ComponentIdType {
        ADD,
        VIEW
    }

    private record AddComponentIdArguments(long authorId, long channelId) {

        static AddComponentIdArguments fromList(List<String> args) {
            long authorId = Long.parseLong(args.get(1));
            long channelId = Long.parseLong(args.get(2));

            return new AddComponentIdArguments(authorId, channelId);
        }

        String[] toArray() {
            return new String[] {ComponentIdType.ADD.name(), Long.toString(authorId),
                    Long.toString(channelId)};
        }

    }

    private String generateAddComponentId(long authorId, long channelId) {
        return generateComponentId(new AddComponentIdArguments(authorId, channelId).toArray());
    }

    protected enum ViewAction {
        NEXT,
        PREV,
        DELETE,
        RENEW
    }

    private record ViewComponentIdArguments(ViewAction action, String paginatorUUID) {

        static ViewComponentIdArguments fromList(List<String> args) {
            ViewAction action = ViewAction.valueOf(args.get(1));
            String paginatorUUID = args.get(2);

            return new ViewComponentIdArguments(action, paginatorUUID);
        }

        String[] toArray() {
            return new String[] {ComponentIdType.VIEW.name(), action.name(), paginatorUUID};
        }

    }

    private String generateViewComponentId(ViewAction action, String paginatorUUID) {
        return generateComponentId(new ViewComponentIdArguments(action, paginatorUUID).toArray());
    }


    public static Map<String, BookmarksPaginator> getBookmarksPaginators() {
        return bookmarksPaginators;
    }
}
