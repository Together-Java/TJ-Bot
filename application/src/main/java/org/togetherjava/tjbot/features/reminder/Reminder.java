package org.togetherjava.tjbot.features.reminder;

import org.togetherjava.tjbot.db.generated.tables.records.PendingRemindersRecord;

public record Reminder(PendingRemindersRecord pendingReminders) {

    public static Reminder from(PendingRemindersRecord pendingReminders) {
        return new Reminder(pendingReminders);
    }


}
