package com.scriptpro.utils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class DateFormatter {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    private DateFormatter() {
    }

    public static String format(Instant instant) {
        return instant == null ? "" : DATE_TIME.format(instant);
    }

    public static String duration(long millis) {
        if (millis < 1000) {
            return millis + " ms";
        }
        return String.format("%.1f s", millis / 1000.0);
    }
}
