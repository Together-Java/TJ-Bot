package org.togetherjava.tjbot.commands.help;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ComponentInteraction;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import org.togetherjava.tjbot.db.generated.tables.records.BookmarksRecord;

import javax.annotation.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BookmarksPaginator {

    public static final long EXPIRE_DELAY_SECONDS = 10L * 60; // 10 Minutes

    private static final MessageEmbed EMBED_NO_BOOKMARKS = new EmbedBuilder()
        .setTitle("No bookmarks!")
        .setDescription(
                "You don't have any bookmarks to view. Add a bookmark with `/bookmarks add [note]`.")
        .setColor(0xFF8484)
        .build();


    private final List<BookmarksRecord> bookmarks;

    private Button btnPrev;
    private Button btnNext;
    private Button btnDelete;
    private Button btnRenew;

    private int page = 0;
    private Instant lastUpdated = Instant.now();


    /**
     * Creates a new instance of the bookmarks paginator, a tool to create pages with messages
     */
    public BookmarksPaginator(SlashCommandInteractionEvent slashEvent,
            List<BookmarksRecord> bookmarks, Button btnPrev, Button btnNext, Button btnDelete,
            Button btnRenew) {
        this.bookmarks = new ArrayList<>(bookmarks);
        this.btnNext = btnNext;
        this.btnPrev = btnPrev;
        this.btnDelete = btnDelete;
        this.btnRenew = btnRenew;

        updateButtons(); // Update the disabled state

        // Send the initial message
        List<LayoutComponent> components = new ArrayList<>();

        if (!bookmarks.isEmpty()) {
            // Use updated buttons
            components.add(ActionRow.of(this.btnPrev, this.btnNext, this.btnDelete, this.btnRenew));
        }

        slashEvent.replyEmbeds(generatePageEmbed())
            .setComponents(components)
            .setEphemeral(true)
            .queue();
    }

    /**
     * This method gets called by {@link BookmarksCommand} when a button is pressed
     */
    public void onButtonClick(ButtonInteractionEvent event, BookmarksCommand.ViewAction action) {
        switch (action) {
            case PREV -> prevPage();
            case NEXT -> nextPage();
            case DELETE -> deleteBookmark();
            case RENEW -> renewBookmark();
        }
        updateButtons();
        update(event);
    }

    /**
     * Updates the button's disabled state
     */
    private void updateButtons() {
        btnPrev = btnPrev.withDisabled(page == 0 || bookmarks.isEmpty());
        btnNext = btnNext.withDisabled(page == bookmarks.size() - 1 || bookmarks.isEmpty());
        btnDelete = btnDelete.withDisabled(bookmarks.isEmpty());
        btnRenew = btnRenew.withDisabled(bookmarks.isEmpty());
    }

    /**
     * Updates the message with the new content and buttons Also resets {@link #lastUpdated}
     */
    private void update(ComponentInteraction componentInteraction) {
        List<LayoutComponent> components = new ArrayList<>();

        if (!bookmarks.isEmpty())
            components.add(ActionRow.of(btnPrev, btnNext, btnDelete, btnRenew));

        componentInteraction.editMessageEmbeds(generatePageEmbed())
            .setComponents(components)
            .queue();

        lastUpdated = Instant.now();
    }

    /**
     * Generates the embed for the current page
     */
    private MessageEmbed generatePageEmbed() {
        BookmarksRecord currentBookmark = getCurrentBookmark();
        if (currentBookmark != null) {

            String note = currentBookmark.getNote() != null ? currentBookmark.getNote() : "No note";

            String description = """
                    <#%s>

                    Note: %s

                    Expires <t:%d:R>
                    """.formatted(currentBookmark.getChannelId(), note,
                    currentBookmark.getLastRenewedAt().getEpochSecond()
                            + BookmarksCommand.EXPIRE_DELAY_SECONDS);

            return new EmbedBuilder().setTitle(currentBookmark.getOriginalTitle())
                .setDescription(description)
                .setFooter("Bookmark %d/%d".formatted(page + 1, bookmarks.size()))
                .setColor(0x98fc03)
                .build();

        } else
            return EMBED_NO_BOOKMARKS;
    }

    public @Nullable BookmarksRecord getCurrentBookmark() {
        if (!bookmarks.isEmpty())
            return bookmarks.get(page);
        else
            return null;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    private void nextPage() {
        if (page < bookmarks.size() - 1)
            page++;
    }

    private void prevPage() {
        if (page > 0)
            page--;
    }

    private void deleteBookmark() {
        bookmarks.remove(page);

        if (!bookmarks.isEmpty()) {
            if (page >= bookmarks.size())
                page--;
        } else
            page = 0;
    }

    private void renewBookmark() {
        BookmarksRecord currentBookmark = getCurrentBookmark();

        if (currentBookmark != null)
            currentBookmark.setLastRenewedAt(Instant.now());
    }

    /**
     * Generates a UUID of the format [timestamp]_[uuid] The timestamp is making it practically
     * impossible to generate the same UUID twice
     */
    public static String generateUUID() {
        return "%d_%s".formatted(Instant.now().toEpochMilli(), UUID.randomUUID());
    }

}
