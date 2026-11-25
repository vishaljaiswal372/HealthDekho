package com.healthDekho.backend.support;

import java.time.*;

/** Test clock: follows the system clock until a test pins it to a date. */
public class MutableClock extends Clock {

    private final ZoneId zone;
    private volatile Instant pinned;

    public MutableClock(ZoneId zone) {
        this.zone = zone;
    }

    public void setDate(LocalDate date) {
        pinned = date.atTime(LocalTime.NOON).atZone(zone).toInstant();
    }

    public void reset() {
        pinned = null;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(zone);
    }

    @Override
    public Instant instant() {
        Instant p = pinned;
        return p != null ? p : Instant.now();
    }
}
