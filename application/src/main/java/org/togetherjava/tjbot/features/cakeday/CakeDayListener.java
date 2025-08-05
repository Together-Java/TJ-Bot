package org.togetherjava.tjbot.features.cakeday;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import org.togetherjava.tjbot.db.generated.tables.records.CakeDaysRecord;
import org.togetherjava.tjbot.features.EventReceiver;

import java.util.Optional;

/**
 * A listener class responsible for handling cake day related events.
 */
public class CakeDayListener extends ListenerAdapter implements EventReceiver {

    private final CakeDayService cakeDayService;

    /**
     * Constructs a new CakeDayListener with the given {@link CakeDayService}.
     *
     * @param cakeDayService the {@link CakeDayService} to be used by this listener
     */
    public CakeDayListener(CakeDayService cakeDayService) {
        this.cakeDayService = cakeDayService;
    }

    /**
     * Handles the event of a message being received in a guild.
     * <p>
     * It caches the user's cake day and inserts the member's cake day into the database if not
     * already present.
     *
     * @param event the {@link MessageReceivedEvent} representing the message received
     */
    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        User author = event.getAuthor();
        Member member = event.getMember();
        long authorId = author.getIdLong();
        long guildId = event.getGuild().getIdLong();

        if (member == null || author.isBot() || author.isSystem()) {
            return;
        }


        if (cakeDayService.hasMemberCakeDayToday(member)) {
            cakeDayService.addCakeDayRole(member);
            return;
        }

        if (cakeDayService.isUserCached(author)) {
            return;
        }

        cakeDayService.addToCache(author);
        Optional<CakeDaysRecord> cakeDaysRecord =
                cakeDayService.findUserCakeDayFromDatabase(authorId);
        if (cakeDaysRecord.isPresent()) {
            return;
        }

        cakeDayService.insertMemberCakeDayToDatabase(member, guildId);
    }

    /**
     * Handles the event of a guild member being removed from the guild. It removes the user's cake
     * day information from the database if present.
     *
     * @param event the {@link GuildMemberRemoveEvent} representing the member removal event
     */
    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        User user = event.getUser();
        Guild guild = event.getGuild();

        cakeDayService.handleUserLeft(user, guild);
    }
}
