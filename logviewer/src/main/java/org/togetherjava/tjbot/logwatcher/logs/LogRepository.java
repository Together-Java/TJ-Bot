package org.togetherjava.tjbot.logwatcher.logs;

import org.togetherjava.tjbot.db.generated.tables.pojos.Logevents;

import java.util.Collection;
import java.util.List;

public interface LogRepository {

    /**
     * Saves the given event to the DB, does not update or merge
     *
     * @param event Event to Insert
     */
    void save(Logevents event);

    /**
     * Fetches all Events from the DB
     *
     * @return List of LogEvents
     */
    List<Logevents> findAll();

    /**
     * Fetches all Events, which LogLevel matches the given Collection, from the DB
     *
     * @return List of LogEvents
     */
    List<Logevents> findWithLevelMatching(Collection<String> logLevels);

}
