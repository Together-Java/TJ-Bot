package org.togetherjava.tjbot.commands.tag;

import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.Tags;
import org.togetherjava.tjbot.db.generated.tables.records.TagsRecord;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Tag system database utility.<br>
 * Has methods to store & retrieve tags from the database.
 *
 * @author illuminator3
 */
public final class TagSystem {
    private final Database database;

    public TagSystem(Database database) {
        this.database = database;
    }

    /**
     * Checks if a tag already exists in the database
     *
     * @param tag id tag to check
     * @return true if it exists, false if not
     * @author illuminator3
     */
    public boolean exists(String tag) {
        return database.readTransaction(
                ctx -> ctx.selectFrom(Tags.TAGS).where(Tags.TAGS.ID.eq(tag)).fetchOne() != null);
    }

    /**
     * Deletes a tag from the database
     *
     * @param tag tag to delete
     * @author illuminator3
     */
    public void delete(String tag) {
        database.writeTransaction(ctx -> {
            ctx.deleteFrom(Tags.TAGS).where(Tags.TAGS.ID.eq(tag)).execute();
        });
    }

    /**
     * Inserts/updates a (new) tag into the database
     *
     * @param tag tag id
     * @param content content of the tag
     * @author illuminator3
     */
    public void put(String tag, String content) {
        database.writeTransaction(ctx -> {
            ctx.insertInto(Tags.TAGS, Tags.TAGS.ID, Tags.TAGS.CONTENT)
                .values(tag, content)
                .onDuplicateKeyUpdate()
                .set(Tags.TAGS.CONTENT, content)
                .execute();
        });
    }

    /**
     * Retrieves the content of a tag from the database.<br>
     *
     * @param tag tag id
     * @return content of the tag, empty optional if the tag doesn't exist
     * @author illuminator3
     */
    public Optional<String> get(String tag) {
        return database.readTransaction(ctx -> {
            return Optional
                .ofNullable(ctx.selectFrom(Tags.TAGS).where(Tags.TAGS.ID.eq(tag)).fetchOne())
                .map(TagsRecord::getContent);
        });
    }

    /**
     * Retrieves all tags from the database
     *
     * @return all tags (id -> content)
     * @author illuminator3
     */
    public Map<String, String> retrieve() {
        return database.readTransaction(ctx -> {
            return ctx.selectFrom(Tags.TAGS)
                .fetch()
                .stream()
                .collect(Collectors.toMap(TagsRecord::getId, TagsRecord::getContent));
        });
    }

    /**
     * Retrieves all tag ids from the database.<br>
     * This method is defined like so:
     *
     * <pre>
     * retrieveIds() {
     *     return Collections.unmodifiableSet(retrieve().keySet());
     * }
     * </pre>
     *
     * @return ids of all tags; unmodifiable set
     * @author illuminator3
     */
    public Set<String> retrieveIds() {
        return Collections.unmodifiableSet(retrieve().keySet());
    }
}
