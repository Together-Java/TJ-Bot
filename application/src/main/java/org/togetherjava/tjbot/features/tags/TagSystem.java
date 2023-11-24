package org.togetherjava.tjbot.features.tags;

import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;

import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.Tags;
import org.togetherjava.tjbot.db.generated.tables.records.TagsRecord;
import org.togetherjava.tjbot.features.utils.StringDistances;

import java.awt.Color;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The core of the tag system. Provides methods to read and create tags, directly tied to the
 * underlying database.
 */
public final class TagSystem {
    /**
     * The ambient color to use for tag system related messages.
     */
    static final Color AMBIENT_COLOR = Color.decode("#FA8072");

    private final Database database;

    /**
     * Creates an instance.
     *
     * @param database the database to store and retrieve tags from
     */
    public TagSystem(Database database) {
        this.database = database;
    }

    /**
     * Creates a delete button with the given component id that can be used in message dialogs. For
     * example to delete a message.
     *
     * @param componentId the component id to use for the button
     * @return the created delete button
     */
    @SuppressWarnings("StaticMethodOnlyUsedInOneClass")
    static Button createDeleteButton(String componentId) {
        return Button.of(ButtonStyle.DANGER, componentId, "Delete", Emoji.fromUnicode("🗑"));
    }

    /**
     * Returns whether the given tag is unknown to the system.
     * <p>
     * If it is unknown, it sends an error message to the user.
     *
     * @param id the id of the tag to check
     * @param event the event to send messages with
     * @return whether the given tag is unknown to the system
     */
    boolean handleIsUnknownTag(String id, IReplyCallback event) {
        if (hasTag(id)) {
            return false;
        }
        String suggestionText = StringDistances.closestMatch(id, getAllIds())
            .map(", did you perhaps mean '%s'?"::formatted)
            .orElse(".");

        event.reply("Could not find any tag with id '%s'%s".formatted(id, suggestionText))
            .setEphemeral(true)
            .queue();
        return true;
    }

    /**
     * Checks if the given tag is known to the tag system.
     *
     * @param id the id of the tag to check
     * @return whether the tag is known to the tag system
     */
    boolean hasTag(String id) {
        return database.readTransaction(context -> context.selectFrom(Tags.TAGS)
            .where(Tags.TAGS.ID.eq(id))
            .fetchOne() != null);
    }

    /**
     * Deletes a tag from the tag system.
     *
     * @param id the id of the tag to delete
     * @throws IllegalArgumentException if the tag is unknown to the system, see
     *         {@link #hasTag(String)}
     */
    void deleteTag(String id) {
        int deletedRecords = database.writeAndProvide(
                context -> context.deleteFrom(Tags.TAGS).where(Tags.TAGS.ID.eq(id)).execute());
        if (deletedRecords == 0) {
            throw new IllegalArgumentException(
                    "Unable to delete the tag '%s', it is unknown to the system".formatted(id));
        }
    }

    /**
     * Inserts or replaces the tag with the given data into the system.
     *
     * @param id the id of the tag to put
     * @param content the content of the tag to put
     */
    void putTag(String id, String content) {
        database.writeTransaction(
                context -> context.insertInto(Tags.TAGS, Tags.TAGS.ID, Tags.TAGS.CONTENT)
                    .values(id, content)
                    .onDuplicateKeyUpdate()
                    .set(Tags.TAGS.CONTENT, content)
                    .execute());
    }

    /**
     * Retrieves the content of the given tag, if it is known to the system (see
     * {@link #hasTag(String)}).
     *
     * @param id the id of the tag to get
     * @return the content of the tag, if the tag is known to the system
     */
    Optional<String> getTag(String id) {
        return database.readTransaction(context -> Optional
            .ofNullable(context.selectFrom(Tags.TAGS).where(Tags.TAGS.ID.eq(id)).fetchOne())
            .map(TagsRecord::getContent));
    }

    /**
     * Gets the ids of all tags known to the system.
     *
     * @return a set of all ids known to the system, not backed
     */
    Set<String> getAllIds() {
        return database.readTransaction(context -> context.select(Tags.TAGS.ID)
            .from(Tags.TAGS)
            .fetch()
            .stream()
            .map(dbRecord -> dbRecord.getValue(Tags.TAGS.ID))
            .collect(Collectors.toSet()));
    }
}
