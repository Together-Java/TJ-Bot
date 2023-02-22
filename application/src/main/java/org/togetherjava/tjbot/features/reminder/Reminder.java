package org.togetherjava.tjbot.features.reminder;

import java.time.temporal.TemporalAccessor;

public record Reminder(int id, TemporalAccessor createdAt,long guildId,long channelId,long authorId,TemporalAccessor remindAt,String content,int failureAttempts) {
}
