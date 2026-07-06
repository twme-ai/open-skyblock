package io.github.openskyblock.calendar;

import java.util.List;

public record CalendarEventDefinition(
        String id,
        String displayName,
        String description,
        int month,
        int day,
        int durationDays,
        long firstDay,
        long recurrenceDays,
        List<String> rewards
) {
    public boolean recurring() {
        return recurrenceDays > 0L;
    }
}
