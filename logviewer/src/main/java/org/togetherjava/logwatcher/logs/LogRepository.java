package org.togetherjava.logwatcher.logs;

import org.springframework.data.jpa.repository.JpaRepository;
import org.togetherjava.logwatcher.entities.LogEvent;

public interface LogRepository extends JpaRepository<LogEvent, Integer> {
}
