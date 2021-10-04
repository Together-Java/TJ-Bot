package org.togetherjava.logwatcher.logs;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.togetherjava.logwatcher.entities.LogDTO;
import org.togetherjava.logwatcher.watcher.StreamWatcher;
import org.togetherjava.tjbot.db.generated.tables.pojos.Logevents;

import java.time.LocalDateTime;
import java.time.ZoneId;

@RestController
public class LogREST {

    private final ILogRepository logs;

    public LogREST(final ILogRepository logs) {
        this.logs = logs;
    }

    @PostMapping(path = "/rest/api/logs", consumes = "application/json")
    public ResponseEntity<Void> logEvent(@RequestBody final LogDTO body) {
        this.logs.save(mapToLogevents(body));
        StreamWatcher.notifyOfEvent();
        return ResponseEntity.ok().build();
    }

    private Logevents mapToLogevents(final LogDTO body) {
        return new Logevents(Integer.MIN_VALUE,
                LocalDateTime.ofInstant(body.getInstant(), ZoneId.systemDefault()),
                body.getThread(), body.getLevel(), body.getLoggerName(), body.getMessage(),
                body.getEndOfBatch(), body.getLoggerFqcn(), body.getThreadId(),
                body.getThreadPriority());
    }
}
