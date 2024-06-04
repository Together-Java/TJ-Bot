package org.togetherjava.tjbot.features.bookmarks;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ComponentInteraction;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

import org.togetherjava.tjbot.db.generated.tables.records.BookmarksRecord;
import org.togetherjava.tjbot.features.utils.MessageUtils;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class BookmarksListRemoveHandler {

    private static final int ENTRIES_PER_PAGE = 10;
    private static final Emoji BUTTON_PREV_EMOJI = Emoji.fromUnicode("⬅");
    private static final String BUTTON_PREV_NAME = "button-previous";
    private static final Emoji BUTTON_NEXT_EMOJI = Emoji.fromUnicode("➡");
    private static final String BUTTON_NEXT_NAME = "button-next";
    private static final String BUTTON_REMOVE_LABEL = "Delete selected bookmarks";
    private static final String BUTTON_REMOVE_NAME = "button-remove";
    private static final String SELECT_MENU_REMOVE_NAME = "select-menu-remove";

    private static final MessageEmbed NO_BOOKMARKS_EMBED =
            BookmarksSystem.createFailureEmbed("You don't have any bookmarks yet.");

    private final BookmarksSystem bookmarksSystem;
    private final Function<String[], String> generateComponentId;

    BookmarksListRemoveHandler(BookmarksSystem bookmarksSystem,
            Function<String[], String> generateComponentId) {
        this.bookmarksSystem = bookmarksSystem;
        this.generateComponentId = generateComponentId;
    }

    void handleListRequest(GenericCommandInteractionEvent event) {
        handlePaginatedRequest(event, RequestType.LIST);
    }

    void handleRemoveRequest(GenericCommandInteractionEvent event) {
        handlePaginatedRequest(event, RequestType.REMOVE);
    }

    private void handlePaginatedRequest(GenericCommandInteractionEvent event,
            RequestType requestType) {
        JDA jda = event.getJDA();
        long userID = event.getUser().getIdLong();

        List<BookmarksRecord> bookmarks = bookmarksSystem.getUsersBookmarks(userID);
        if (bookmarks.isEmpty()) {
            event.replyEmbeds(NO_BOOKMARKS_EMBED).setEphemeral(true).queue();
            return;
        }

        MessageEmbed pageEmbed = generatePageEmbed(bookmarks, requestType, 0);

        Collection<LayoutComponent> components = new ArrayList<>();
        components.add(generateNavigationComponent(requestType, 0));

        if (requestType == RequestType.REMOVE) {
            components
                .addAll(generateRemoveComponents(jda, bookmarks, RequestType.REMOVE, 0, Set.of()));
        }

        event.replyEmbeds(pageEmbed).setComponents(components).setEphemeral(true).queue();
    }

    private static MessageEmbed generatePageEmbed(List<BookmarksRecord> bookmarks,
            RequestType requestType, int currentPageIndex) {
        int lastPageIndex = getLastPageIndex(bookmarks);

        String title;
        Color color;
        switch (requestType) {
            case LIST -> {
                title = "Bookmarks List";
                color = BookmarksSystem.COLOR_SUCCESS;
            }
            case REMOVE -> {
                title = "Remove Bookmarks";
                color = BookmarksSystem.COLOR_WARNING;
            }
            default -> throw new IllegalArgumentException("Unknown request type: " + requestType);
        }

        StringJoiner descriptionJoiner = new StringJoiner("\n\n");

        getPageEntries(bookmarks, currentPageIndex).forEach(pageEntry -> {
            int bookmarkNumber = pageEntry.bookmarkNumber;
            long channelID = pageEntry.bookmark.getChannelId();
            String note = pageEntry.bookmark.getNote();

            StringJoiner entryJoiner = new StringJoiner("\n");

            entryJoiner.add(
                    "**%d.** %s".formatted(bookmarkNumber, MessageUtils.mentionChannel(channelID)));
            if (note != null && requestType != RequestType.REMOVE) {
                entryJoiner.add("*%s*".formatted(note));
            }

            descriptionJoiner.add(entryJoiner.toString());
        });

        String description = descriptionJoiner.toString();
        String footer = "Page %d/%d".formatted(currentPageIndex + 1, lastPageIndex + 1);

        return new EmbedBuilder().setTitle(title)
            .setDescription(description)
            .setFooter(footer)
            .setColor(color)
            .build();
    }

    void onButtonClick(ButtonInteractionEvent event, List<String> args) {
        Request request = Request.fromArgs(args);
        long userID = event.getUser().getIdLong();

        List<BookmarksRecord> bookmarks = bookmarksSystem.getUsersBookmarks(userID);

        int nextPageIndex = switch (request.componentName) {
            case BUTTON_PREV_NAME -> clampPageIndex(bookmarks, request.pageToDisplayIndex - 1);
            case BUTTON_NEXT_NAME -> clampPageIndex(bookmarks, request.pageToDisplayIndex + 1);
            case BUTTON_REMOVE_NAME -> {
                removeSelectedBookmarks(bookmarks, event, request);
                yield clampPageIndex(bookmarks, request.pageToDisplayIndex);
            }
            default ->
                throw new IllegalArgumentException("Unknown button: " + request.componentName);
        };

        updatePagination(event, request.atPage(nextPageIndex), bookmarks);
    }

    private void removeSelectedBookmarks(List<BookmarksRecord> bookmarks,
            ComponentInteraction event, Request request) {
        long userID = event.getUser().getIdLong();

        Predicate<BookmarksRecord> isBookmarkSelectedForRemoval =
                bookmark -> request.bookmarkIdsToRemove.contains(bookmark.getChannelId());

        bookmarks.removeIf(isBookmarkSelectedForRemoval);
        bookmarksSystem.removeBookmarks(userID, request.bookmarkIdsToRemove);
    }

    void onSelectMenuSelection(StringSelectInteractionEvent event, List<String> args) {
        Request request = Request.fromArgs(args);

        if (request.type != RequestType.REMOVE) {
            throw new IllegalArgumentException(
                    "Only remove requests must have a menu, but got " + request.type);
        }
        if (!request.componentName.equals(SELECT_MENU_REMOVE_NAME)) {
            throw new IllegalArgumentException(
                    "There should only be a single menu, but got " + request.componentName);
        }

        Set<Long> selectedBookmarkIdsToRemove = event.getSelectedOptions()
            .stream()
            .map(SelectOption::getValue)
            .map(Long::parseLong)
            .collect(Collectors.toSet());
        request = request.withSelectedBookmarksToRemove(selectedBookmarkIdsToRemove);

        List<BookmarksRecord> bookmarks =
                bookmarksSystem.getUsersBookmarks(event.getUser().getIdLong());
        int updatedPageIndex = clampPageIndex(bookmarks, request.pageToDisplayIndex);

        updatePagination(event, request.atPage(updatedPageIndex), bookmarks);
    }

    private void updatePagination(ComponentInteraction event, Request request,
            List<BookmarksRecord> bookmarks) {
        if (bookmarks.isEmpty()) {
            event.editMessageEmbeds(NO_BOOKMARKS_EMBED).setComponents().queue();
            return;
        }

        MessageEmbed pageEmbed =
                generatePageEmbed(bookmarks, request.type, request.pageToDisplayIndex);

        Collection<LayoutComponent> components = new ArrayList<>();
        components.add(generateNavigationComponent(request.type, request.pageToDisplayIndex));
        if (request.type == RequestType.REMOVE) {
            components.addAll(generateRemoveComponents(event.getJDA(), bookmarks, request.type,
                    request.pageToDisplayIndex, request.bookmarkIdsToRemove));
        }

        event.editMessageEmbeds(pageEmbed).setComponents(components).queue();
    }

    private LayoutComponent generateNavigationComponent(RequestType requestType,
            int pageToDisplayIndex) {
        UnaryOperator<String> generateNavigationComponentId = name -> {
            Request request = new Request(requestType, name, pageToDisplayIndex, Set.of());

            return generateComponentId.apply(request.toArray());
        };

        String buttonPrevId = generateNavigationComponentId.apply(BUTTON_PREV_NAME);
        Button buttonPrev = Button.primary(buttonPrevId, BUTTON_PREV_EMOJI);

        String buttonNextId = generateNavigationComponentId.apply(BUTTON_NEXT_NAME);
        Button buttonNext = Button.primary(buttonNextId, BUTTON_NEXT_EMOJI);

        return ActionRow.of(buttonPrev, buttonNext);
    }

    private List<LayoutComponent> generateRemoveComponents(JDA jda,
            List<? extends BookmarksRecord> bookmarks, RequestType requestType,
            int pageToDisplayIndex, Set<Long> bookmarksToRemoveChannelIDs) {
        List<PageEntry> pageEntries = getPageEntries(bookmarks, pageToDisplayIndex);

        UnaryOperator<String> generateRemoveComponentId = name -> {
            Request request =
                    new Request(requestType, name, pageToDisplayIndex, bookmarksToRemoveChannelIDs);

            return generateComponentId.apply(request.toArray());
        };

        List<SelectOption> selectMenuRemoveOptions = pageEntries.stream().map(pageEntry -> {
            ThreadChannel channel = jda.getThreadChannelById(pageEntry.bookmark.getChannelId());
            String channelIDString = String.valueOf(pageEntry.bookmark.getChannelId());
            int bookmarkNumber = pageEntry.bookmarkNumber;

            String label = channel != null ? "%d. %s".formatted(bookmarkNumber, channel.getName())
                    : "Delete bookmark %d".formatted(bookmarkNumber);

            return SelectOption.of(label, channelIDString);
        }).toList();

        String selectMenuRemoveId = generateRemoveComponentId.apply(SELECT_MENU_REMOVE_NAME);
        SelectMenu selectMenuRemove = StringSelectMenu.create(selectMenuRemoveId)
            .setPlaceholder("Select bookmarks to delete")
            .addOptions(selectMenuRemoveOptions)
            .setDefaultValues(bookmarksToRemoveChannelIDs.stream().map(String::valueOf).toList())
            .setRequiredRange(0, selectMenuRemoveOptions.size())
            .build();

        String buttonRemoveId = generateRemoveComponentId.apply(BUTTON_REMOVE_NAME);
        Button buttonRemove = Button.danger(buttonRemoveId, BUTTON_REMOVE_LABEL)
            .withDisabled(bookmarksToRemoveChannelIDs.isEmpty());

        return List.of(ActionRow.of(selectMenuRemove), ActionRow.of(buttonRemove));
    }

    private static List<PageEntry> getPageEntries(List<? extends BookmarksRecord> bookmarks,
            int pageIndex) {
        int indexStart = pageIndex * ENTRIES_PER_PAGE;
        int indexEndMax = bookmarks.size();
        int indexEnd = Math.min(indexStart + ENTRIES_PER_PAGE, indexEndMax);

        return bookmarks.subList(indexStart, indexEnd)
            .stream()
            .map(bookmark -> new PageEntry(bookmarks.indexOf(bookmark) + 1, bookmark))
            .toList();
    }

    private static int getLastPageIndex(List<BookmarksRecord> bookmarks) {
        if (bookmarks.isEmpty()) {
            return 0;
        }

        return getPageOfBookmark(bookmarks.size() - 1);
    }

    private static int getPageOfBookmark(int bookmarkIndex) {
        return Math.floorDiv(bookmarkIndex, ENTRIES_PER_PAGE);
    }

    private static int clampPageIndex(List<BookmarksRecord> bookmarks, int pageIndex) {
        int maxPageIndex = getLastPageIndex(bookmarks);

        return Math.clamp(pageIndex, 0, maxPageIndex);
    }

    private enum RequestType {
        LIST,
        REMOVE
    }

    private record PageEntry(int bookmarkNumber, BookmarksRecord bookmark) {
    }

    private record Request(RequestType type, String componentName, int pageToDisplayIndex,
            Set<Long> bookmarkIdsToRemove) {
        Request atPage(int pageIndex) {
            return new Request(type, componentName, pageIndex, bookmarkIdsToRemove);
        }

        Request withSelectedBookmarksToRemove(Set<Long> selectedBookmarkIdsToRemove) {
            return new Request(type, componentName, pageToDisplayIndex,
                    selectedBookmarkIdsToRemove);
        }

        static Request fromArgs(List<String> args) {
            RequestType requestType = RequestType.valueOf(args.getFirst());
            String componentName = args.get(1);
            int currentPageIndex = Integer.parseInt(args.get(2));

            Set<Long> bookmarkIdsToRemove = Set.of();
            if (args.size() > 3) {
                bookmarkIdsToRemove =
                        args.stream().skip(3).map(Long::parseLong).collect(Collectors.toSet());
            }

            return new Request(requestType, componentName, currentPageIndex, bookmarkIdsToRemove);
        }

        String[] toArray() {
            Stream<String> primaryArgs =
                    Stream.of(type.name(), componentName, Integer.toString(pageToDisplayIndex));
            Stream<String> secondaryArgs = bookmarkIdsToRemove.stream().map(String::valueOf);

            return Stream.concat(primaryArgs, secondaryArgs).toArray(String[]::new);
        }
    }
}
