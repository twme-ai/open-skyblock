package io.github.openskyblock.calendar;

public record SkyBlockDate(
        long absoluteDay,
        int year,
        int month,
        String monthName,
        int day
) {
}
