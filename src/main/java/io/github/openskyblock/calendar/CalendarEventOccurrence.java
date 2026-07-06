package io.github.openskyblock.calendar;

public record CalendarEventOccurrence(
        CalendarEventDefinition definition,
        long startDay,
        long endDay
) {
}
