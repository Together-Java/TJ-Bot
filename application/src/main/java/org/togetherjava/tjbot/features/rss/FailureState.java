package org.togetherjava.tjbot.features.rss;

import java.time.ZonedDateTime;

record FailureState(int count, ZonedDateTime lastFailure) {
}
