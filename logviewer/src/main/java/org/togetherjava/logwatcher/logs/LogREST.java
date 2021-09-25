package org.togetherjava.logwatcher.logs;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.togetherjava.logwatcher.entities.LogDTO;
import org.togetherjava.logwatcher.watcher.StreamWatcher;

@RestController
public class LogREST {

    private final LogRepository logs;

    public LogREST(LogRepository logs) {
        this.logs = logs;
    }

    @PostMapping(path = "/rest/api/logs", consumes = "application/json")
    public ResponseEntity<Void> logEvent(@RequestBody final LogDTO body) {
        logs.save(body.toLogEvent());
        StreamWatcher.notifyOfEvent();
        return ResponseEntity.ok().build();
    }
}
