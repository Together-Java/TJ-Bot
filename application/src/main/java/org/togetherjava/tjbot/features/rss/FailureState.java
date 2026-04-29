package org.togetherjava.tjbot.features.rss;

import java.time.Instant;

record FailureState(int count, Instant lastFailure) {
}
