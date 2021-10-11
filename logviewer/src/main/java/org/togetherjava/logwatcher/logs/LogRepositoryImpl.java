package org.togetherjava.logwatcher.logs;

import org.springframework.stereotype.Repository;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.pojos.Logevents;
import org.togetherjava.tjbot.db.generated.tables.records.LogeventsRecord;

import java.util.List;

import static org.togetherjava.tjbot.db.generated.tables.Logevents.LOGEVENTS;

@Repository
public class LogRepositoryImpl implements LogRepository {

    private final Database db;

    public LogRepositoryImpl(final Database db) {
        this.db = db;
    }


    @Override
    public void save(Logevents event) {
        this.db.writeTransaction(ctx -> {
            LogeventsRecord toInsert = ctx.newRecord(LOGEVENTS)
                .setEndofbatch(event.getEndofbatch())
                .setLevel(event.getLevel())
                .setLoggername(event.getLoggername())
                .setLoggerfqcn(event.getLoggerfqcn())
                .setMessage(event.getMessage())
                .setTime(event.getTime())
                .setThread(event.getThread())
                .setThreadid(event.getThreadid())
                .setThreadpriority(event.getThreadpriority());

            if (event.getId() != Integer.MIN_VALUE) {
                toInsert.setId(event.getId());
            }

            // No merge or Update here, Logs are not supposed to be updated
            toInsert.insert();
        });
    }

    @Override
    @SuppressWarnings("java:S1602") // Curly Braces are necessary here
    public List<Logevents> findAll() {
        return this.db.read(ctx -> {
            return ctx.selectFrom(LOGEVENTS).fetch(this::recordToPojo);
        });
    }

    private Logevents recordToPojo(final LogeventsRecord logRecord) {
        return new Logevents(logRecord.getId(), logRecord.getTime(), logRecord.getThread(),
                logRecord.getLevel(), logRecord.getLoggername(), logRecord.getMessage(),
                logRecord.getEndofbatch(), logRecord.getLoggerfqcn(), logRecord.getThreadid(),
                logRecord.getThreadpriority());
    }


}
