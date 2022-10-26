package org.togetherjava.tjbot.commands.help;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ComponentInteraction;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;

import org.togetherjava.tjbot.commands.UserInteractionType;
import org.togetherjava.tjbot.commands.UserInteractor;
import org.togetherjava.tjbot.commands.componentids.ComponentIdGenerator;
import org.togetherjava.tjbot.commands.componentids.ComponentIdInteractor;
import org.togetherjava.tjbot.commands.utils.Arraylizable;
import org.togetherjava.tjbot.db.generated.tables.records.BookmarksRecord;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public final class BookmarkPaginatorInteractor implements UserInteractor {

    private static final int ENTRIES_PER_PAGE = 10;
    private static final Emoji BUTTON_PREV_EMOJI = Emoji.fromUnicode("⬅");
    private static final String BUTTON_PREV_NAME = "button-prevoius";
    private static final Emoji BUTTON_NEXT_EMOJI = Emoji.fromUnicode("➡");
    private static final String BUTTON_NEXT_NAME = "button-next";
    private static final String BUTTON_REMOVE_LABEL = "Delete selected bookmarks";
    private static final String BUTTON_REMOVE_NAME = "button-remove";
    private static final String SELECTMENU_REMOVE_NAME = "selectmenu-remove";

    private static final MessageEmbed NO_BOOKMARKS_EMBED = BookmarksSystem.createTinyEmbed("""
            You don't have any bookmarks!
            """, BookmarksSystem.COLOR_FAILURE);

    private final BookmarksSystem bookmarksSystem;
    private final ComponentIdInteractor componentIdInteractor;

    /**
     * Creates a new instance.
     *
     * @param bookmarksSystem The bookmarks system to use
     */
    public BookmarkPaginatorInteractor(BookmarksSystem bookmarksSystem) {
        this.bookmarksSystem = bookmarksSystem;
        this.componentIdInteractor = new ComponentIdInteractor(getInteractionType(), getName());

        bookmarksSystem.acceptPaginationConsumers(this::onListPaginationRequest,
                this::onRemovePaginationRequest);
    }

    @Override
    public String getName() {
        return "bookmarks-paginator";
    }

    @Override
    public UserInteractionType getInteractionType() {
        return UserInteractionType.OTHER;
    }

    @Override
    public void acceptComponentIdGenerator(ComponentIdGenerator generator) {
        componentIdInteractor.acceptComponentIdGenerator(generator);
    }

    /**
     * Generates a component id with the arguments the {@link Arraylizable} class provides.
     *
     * @param args The {@link Arraylizable} class to get the arguments from
     * @return The generated component ID
     */
    private String generateComponentId(Arraylizable<String> args) {
        return componentIdInteractor.generateComponentId(args.toArray());
    }

    @Override
    public void onModalSubmitted(ModalInteractionEvent event, List<String> stringArgs) {
        throw new UnsupportedOperationException("Not used");
    }

    @Override
    public void onButtonClick(ButtonInteractionEvent event, List<String> stringArgs) {
        ComponentArguments args = parseStringArgs(stringArgs);
        long userID = event.getUser().getIdLong();
        List<BookmarksRecord> bookmarks = bookmarksSystem.getUsersBookmarks(userID);

        args.currentPageIndex = switch (args.componentName) {
            case BUTTON_PREV_NAME -> ensurePageIndex(bookmarks, args.currentPageIndex - 1);
            case BUTTON_NEXT_NAME -> ensurePageIndex(bookmarks, args.currentPageIndex + 1);
            case BUTTON_REMOVE_NAME -> {
                RemoveComponentArguments removeArgs = (RemoveComponentArguments) args;

                removeSelectedBookmarks(bookmarks, event, removeArgs);

                yield ensurePageIndex(bookmarks, args.currentPageIndex);
            }
            default -> throw new IllegalArgumentException("Unknown button");
        };

        updatePagination(event, args, bookmarks);
    }

    @Override
    public void onSelectMenuSelection(SelectMenuInteractionEvent event, List<String> stringArgs) {
        List<SelectOption> selected = event.getSelectedOptions();
        ComponentArguments args = parseStringArgs(stringArgs);
        long userID = event.getUser().getIdLong();
        List<BookmarksRecord> bookmarks = bookmarksSystem.getUsersBookmarks(userID);

        if (args.paginationType == PaginationType.REMOVE) {
            RemoveComponentArguments removeArgs = (RemoveComponentArguments) args;

            if (removeArgs.componentName.equals(SELECTMENU_REMOVE_NAME)) {
                List<Long> bookmarksToRemoveChannelIDs =
                        selected.stream().map(o -> Long.parseLong(o.getValue())).toList();

                args = new RemoveComponentArguments(args.paginationType, args.componentName,
                        args.currentPageIndex, bookmarksToRemoveChannelIDs);
            } else {
                throw new IllegalArgumentException(
                        "Pagination type REMOVE should only have a single menu");
            }
        } else {
            throw new IllegalArgumentException("Only pagination type REMOVE should have a menu");
        }

        updatePagination(event, args, bookmarks);
    }

    /**
     * Gets called when {@link BookmarksCommand} requests a list pagination.
     * 
     * @param event The command interaction event
     */
    private void onListPaginationRequest(GenericCommandInteractionEvent event) {
        onPaginationRequest(event, PaginationType.LIST);
    }

    /**
     * Gets called when {@link BookmarksCommand} requests a remove pagination.
     * 
     * @param event The command interaction event
     */
    private void onRemovePaginationRequest(GenericCommandInteractionEvent event) {
        onPaginationRequest(event, PaginationType.REMOVE);
    }

    private void onPaginationRequest(GenericCommandInteractionEvent event,
            PaginationType paginationType) {
        JDA jda = event.getJDA();
        long userID = event.getUser().getIdLong();
        List<BookmarksRecord> bookmarks = bookmarksSystem.getUsersBookmarks(userID);

        if (bookmarks.isEmpty()) {
            event.replyEmbeds(NO_BOOKMARKS_EMBED).setEphemeral(true).queue();
            return;
        }

        MessageEmbed pageEmbed = generatePageEmbed(bookmarks, paginationType, 0);

        List<LayoutComponent> components = new ArrayList<>();
        components.add(generateNavigationComponent(paginationType, 0));
        if (paginationType == PaginationType.REMOVE) {
            components
                .addAll(generateRemoveComponents(jda, bookmarks, paginationType, 0, List.of()));
        }

        event.replyEmbeds(pageEmbed).setComponents(components).setEphemeral(true).queue();
    }

    private void updatePagination(ComponentInteraction event, ComponentArguments args,
            List<BookmarksRecord> bookmarks) {
        JDA jda = event.getJDA();

        if (bookmarks.isEmpty()) {
            event.editMessageEmbeds(NO_BOOKMARKS_EMBED).setComponents().queue();
            return;
        }

        MessageEmbed pageEmbed = generatePageEmbed(bookmarks, args);

        List<LayoutComponent> components = new ArrayList<>();

        components.add(generateNavigationComponent(args.paginationType, args.currentPageIndex));

        if (args.paginationType == PaginationType.REMOVE) {
            RemoveComponentArguments removeArgs = (RemoveComponentArguments) args;

            components.addAll(generateRemoveComponents(jda, bookmarks, removeArgs.paginationType,
                    removeArgs.currentPageIndex, removeArgs.bookmarksToRemoveChannelIDs));
        }

        event.editMessageEmbeds(pageEmbed).setComponents(components).queue();
    }

    private static MessageEmbed generatePageEmbed(List<BookmarksRecord> bookmarks,
            ComponentArguments args) {
        return generatePageEmbed(bookmarks, args.paginationType, args.currentPageIndex);
    }

    private static MessageEmbed generatePageEmbed(List<BookmarksRecord> bookmarks,
            PaginationType paginationType, int currentPageIndex) {
        int highestPageIndex = getLastPageIndex(bookmarks);

        String title;
        Color color;

        switch (paginationType) {
            case LIST -> {
                title = "Bookmarks List";
                color = BookmarksSystem.COLOR_SUCCESS;
            }
            case REMOVE -> {
                title = "Remove Bookmarks";
                color = BookmarksSystem.COLOR_WARNING;
            }
            default -> throw new IllegalArgumentException("Unknown pagination type");
        }


        StringJoiner descriptionJoiner = new StringJoiner("\n\n");

        getPageEntries(bookmarks, currentPageIndex).forEach(pageEntry -> {
            int bookmarkNumber = pageEntry.bookmarkNumber;
            long channelID = pageEntry.bookmark.getChannelId();
            String note = pageEntry.bookmark.getNote();

            StringJoiner entryJoiner = new StringJoiner("\n");

            entryJoiner.add("**%d.** <#%d>".formatted(bookmarkNumber, channelID)); // #. [Channel]
            if (note != null && paginationType != PaginationType.REMOVE) {
                entryJoiner.add("*%s*".formatted(note)); // Note
            }

            descriptionJoiner.add(entryJoiner.toString());
        });

        String description = descriptionJoiner.toString();
        String footer = "Page %d/%d".formatted(currentPageIndex + 1, highestPageIndex + 1);

        return new EmbedBuilder().setTitle(title)
            .setDescription(description)
            .setFooter(footer)
            .setColor(color)
            .build();
    }

    private LayoutComponent generateNavigationComponent(PaginationType paginationType,
            int currentPageIndex) {
        UnaryOperator<String> generateNavigationComponentId = name -> {
            ComponentArguments args =
                    new ComponentArguments(paginationType, name, currentPageIndex);

            return generateComponentId(args);
        };

        String buttonPrevId = generateNavigationComponentId.apply(BUTTON_PREV_NAME);
        Button buttonPrev = Button.primary(buttonPrevId, BUTTON_PREV_EMOJI);

        String buttonNextId = generateNavigationComponentId.apply(BUTTON_NEXT_NAME);
        Button buttonNext = Button.primary(buttonNextId, BUTTON_NEXT_EMOJI);

        return ActionRow.of(buttonPrev, buttonNext);
    }

    private List<LayoutComponent> generateRemoveComponents(JDA jda, List<BookmarksRecord> bookmarks,
            PaginationType paginationType, int currentPageIndex,
            List<Long> bookmarksToRemoveChannelIDs) {
        List<PageEntry> pageEntries = getPageEntries(bookmarks, currentPageIndex);

        UnaryOperator<String> generateRemoveComponentId = name -> {
            RemoveComponentArguments args = new RemoveComponentArguments(paginationType, name,
                    currentPageIndex, bookmarksToRemoveChannelIDs);

            return generateComponentId(args);
        };

        List<SelectOption> selectMenuRemoveOptions = pageEntries.stream().map(pageEntry -> {
            ThreadChannel channel = jda.getThreadChannelById(pageEntry.bookmark.getChannelId());
            String channelIDString = String.valueOf(pageEntry.bookmark.getChannelId());
            int bookmarkNumber = pageEntry.bookmarkNumber;

            String label = channel != null ? "%d. %s".formatted(bookmarkNumber, channel.getName())
                    : "Delete bookmark %d".formatted(bookmarkNumber);

            return SelectOption.of(label, channelIDString);
        }).toList();

        String selectMenuRemoveId = generateRemoveComponentId.apply(SELECTMENU_REMOVE_NAME);
        SelectMenu selectMenuRemove = SelectMenu.create(selectMenuRemoveId)
            .setPlaceholder("Select bookmarks to delete")
            .addOptions(selectMenuRemoveOptions)
            .setDefaultValues(parseListToStringList(bookmarksToRemoveChannelIDs))
            .setRequiredRange(0, selectMenuRemoveOptions.size())
            .build();

        String buttonRemoveId = generateRemoveComponentId.apply(BUTTON_REMOVE_NAME);
        Button buttonRemove = Button.danger(buttonRemoveId, BUTTON_REMOVE_LABEL)
            .withDisabled(bookmarksToRemoveChannelIDs.isEmpty());

        return List.of(ActionRow.of(selectMenuRemove), ActionRow.of(buttonRemove));
    }

    /**
     * Removed the selected bookmarks from the bookmarks list and the database.
     *
     * @param bookmarks The bookmarks list
     * @param event The component interaction event
     * @param args The remove component arguments
     */
    private void removeSelectedBookmarks(List<BookmarksRecord> bookmarks,
            ComponentInteraction event, RemoveComponentArguments args) {
        long userID = event.getUser().getIdLong();

        Predicate<BookmarksRecord> removePredicate =
                bookmark -> args.bookmarksToRemoveChannelIDs.contains(bookmark.getChannelId());

        bookmarks.removeIf(removePredicate);
        bookmarksSystem.removeBookmarks(userID, args.bookmarksToRemoveChannelIDs);
    }

    /**
     * Calculates which bookmarks to display as page entries.
     *
     * @param bookmarks The bookmarks list
     * @param pageIndex The page index the entries should be for
     * @return A list of entries to be displayed on the requested page
     */
    private static List<PageEntry> getPageEntries(List<BookmarksRecord> bookmarks, int pageIndex) {
        int indexStart = pageIndex * ENTRIES_PER_PAGE;
        int indexEndMax = bookmarks.size();
        int indexEnd = Math.min(indexStart + ENTRIES_PER_PAGE, indexEndMax);

        return bookmarks.subList(indexStart, indexEnd)
            .stream()
            .map(b -> new PageEntry(bookmarks.indexOf(b) + 1, b))
            .toList();
    }

    /**
     * Calculates the last page index for displaying all bookmarks.
     *
     * @param bookmarks The bookmarks list
     * @return The index of the last page
     */
    private static int getLastPageIndex(List<BookmarksRecord> bookmarks) {
        int highestBookmarkIndex = bookmarks.size() - 1;

        return Math.floorDiv(highestBookmarkIndex, ENTRIES_PER_PAGE);
    }

    /**
     * Makes sure that the page index never is negative or larger than the index of the last page.
     *
     * @param bookmarks The bookmarks list
     * @param pageIndex The page possibly invalid index
     * @return A valid page index
     */
    private static int ensurePageIndex(List<BookmarksRecord> bookmarks, int pageIndex) {
        // If the bookmarks list is empty the maxBookmarkIndex would be -1. This causes the
        // maxPageIndex to also return -1, resulting in other errors.
        if (bookmarks.isEmpty()) {
            return 0;
        }

        int maxBookmarkIndex = bookmarks.size() - 1;
        int maxPageIndex = Math.floorDiv(maxBookmarkIndex, ENTRIES_PER_PAGE);

        pageIndex = Math.max(pageIndex, 0);
        pageIndex = Math.min(pageIndex, maxPageIndex);

        return pageIndex;
    }

    private static ComponentArguments parseStringArgs(List<String> stringArgs) {
        return stringArgs.get(0).equals(PaginationType.REMOVE.name())
                ? RemoveComponentArguments.fromStringArgs(stringArgs)
                : ComponentArguments.fromStringArgs(stringArgs);
    }

    private static List<String> parseListToStringList(List<?> list) {
        return list.stream().map(String::valueOf).toList();
    }

    private enum PaginationType {
        LIST,
        REMOVE
    }


    private record PageEntry(int bookmarkNumber, BookmarksRecord bookmark) {
    }


    private static class ComponentArguments implements Arraylizable<String> {

        final PaginationType paginationType;
        final String componentName;
        int currentPageIndex;

        public ComponentArguments(PaginationType paginationType, String componentName,
                int currentPageIndex) {
            this.paginationType = paginationType;
            this.currentPageIndex = currentPageIndex;
            this.componentName = componentName;
        }

        @Override
        public String[] toArray() {
            return new String[] {paginationType.name(), componentName,
                    String.valueOf(currentPageIndex)};
        }

        public static ComponentArguments fromStringArgs(List<String> stringArgs) {
            PaginationType paginationType = PaginationType.valueOf(stringArgs.get(0));
            String componentName = stringArgs.get(1);
            int currentPageIndex = Integer.parseInt(stringArgs.get(2));

            return new ComponentArguments(paginationType, componentName, currentPageIndex);
        }

    }


    private static class RemoveComponentArguments extends ComponentArguments {

        List<Long> bookmarksToRemoveChannelIDs;

        public RemoveComponentArguments(PaginationType paginationType, String componentName,
                int currentPageIndex, List<Long> bookmarksToRemoveChannelIDs) {
            super(paginationType, componentName, currentPageIndex);
            this.bookmarksToRemoveChannelIDs =
                    Collections.unmodifiableList(bookmarksToRemoveChannelIDs);
        }

        public static RemoveComponentArguments fromStringArgs(List<String> stringArgs) {
            PaginationType paginationType = PaginationType.valueOf(stringArgs.get(0));
            String componentName = stringArgs.get(1);
            int currentPageIndex = Integer.parseInt(stringArgs.get(2));

            List<String> remainingStringArgs = stringArgs.subList(3, stringArgs.size());
            List<Long> bookmarksToRemoveChannelIDs = parseStringListToLongList(remainingStringArgs);

            return new RemoveComponentArguments(paginationType, componentName, currentPageIndex,
                    bookmarksToRemoveChannelIDs);
        }

        @Override
        public String[] toArray() {
            List<String> argsList = new ArrayList<>();

            argsList.add(paginationType.name());
            argsList.add(componentName);
            argsList.add(String.valueOf(currentPageIndex));
            argsList.addAll(parseListToStringList(bookmarksToRemoveChannelIDs));

            return argsList.toArray(String[]::new);
        }

        private static List<Long> parseStringListToLongList(List<String> stringList) {
            return stringList.stream().map(Long::parseLong).toList();
        }

    }

}
