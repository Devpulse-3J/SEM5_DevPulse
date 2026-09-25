package com.devpulse.metrics.dto;

/** Result of {@code POST /metrics/dora/snapshots/rebuild}. */
public record RebuildSnapshotsResponse(int snapshotsRebuilt) {
}
