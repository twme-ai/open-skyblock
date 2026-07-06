package io.github.openskyblock.calendar;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;

public final class CalendarService {
    private static final List<String> DEFAULT_MONTHS = List.of(
            "Early Spring",
            "Spring",
            "Late Spring",
            "Early Summer",
            "Summer",
            "Late Summer",
            "Early Autumn",
            "Autumn",
            "Late Autumn",
            "Early Winter",
            "Winter",
            "Late Winter"
    );

    private final ConfigService configService;
    private final TextService text;
    private final Map<String, CalendarEventDefinition> events = new HashMap<>();

    public CalendarService(ConfigService configService, TextService text) {
        this.configService = configService;
        this.text = text;
    }

    public void reload() {
        events.clear();
        ConfigurationSection section = configService.calendar().getConfigurationSection("events");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection event = section.getConfigurationSection(id);
            if (event == null) {
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            events.put(normalized, new CalendarEventDefinition(
                    normalized,
                    event.getString("display-name", normalized),
                    event.getString("description", ""),
                    Math.max(1, event.getInt("month", 1)),
                    Math.max(1, event.getInt("day", 1)),
                    Math.max(1, event.getInt("duration-days", 1)),
                    Math.max(0L, event.getLong("first-day", 0L)),
                    Math.max(0L, event.getLong("recurrence-days", 0L)),
                    event.getStringList("rewards")
            ));
        }
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.calendar", true);
    }

    public List<String> eventIds() {
        return events().stream().map(CalendarEventDefinition::id).toList();
    }

    public Optional<CalendarEventDefinition> event(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(events.get(id.toUpperCase(Locale.ROOT)));
    }

    public boolean eventActive(String id) {
        return event(id)
                .flatMap(definition -> activeOccurrence(definition, currentDay()))
                .isPresent();
    }

    public List<CalendarEventDefinition> events() {
        return events.values().stream()
                .sorted(Comparator.comparing(CalendarEventDefinition::id))
                .toList();
    }

    public SkyBlockDate currentDate() {
        return dateAt(currentDay());
    }

    public void sendCalendar(CommandSender sender) {
        if (!enabled()) {
            text.send(sender, "commands.calendar-disabled");
            return;
        }
        SkyBlockDate date = currentDate();
        CalendarEventOccurrence next = upcomingEvents().stream().findFirst().orElse(null);
        text.send(sender, "commands.calendar-summary", List.of(
                TextService.raw("date", formatDate(date)),
                TextService.raw("year", Integer.toString(date.year())),
                TextService.raw("month", date.monthName()),
                TextService.raw("day", Integer.toString(date.day())),
                TextService.raw("active_count", Integer.toString(activeEvents().size())),
                TextService.parsed("next_event", next == null ? text.rawMessage("calendar.no-event") : nextEventLine(next))
        ));
    }

    public void sendEvents(CommandSender sender) {
        if (!enabled()) {
            text.send(sender, "commands.calendar-disabled");
            return;
        }
        text.send(sender, "commands.calendar-events-header", List.of(TextService.raw("date", formatDate(currentDate()))));
        List<CalendarEventOccurrence> active = activeEvents();
        if (active.isEmpty()) {
            text.send(sender, "commands.calendar-active-empty");
        } else {
            for (CalendarEventOccurrence occurrence : active) {
                text.send(sender, "commands.calendar-active-line", eventPlaceholders(occurrence));
            }
        }
        for (CalendarEventOccurrence occurrence : upcomingEvents().stream().limit(upcomingLimit()).toList()) {
            text.send(sender, "commands.calendar-upcoming-line", eventPlaceholders(occurrence));
        }
    }

    public void sendEvent(CommandSender sender, String eventId) {
        if (!enabled()) {
            text.send(sender, "commands.calendar-disabled");
            return;
        }
        CalendarEventDefinition definition = event(eventId).orElse(null);
        if (definition == null) {
            text.send(sender, "commands.calendar-event-unknown", List.of(TextService.raw("event", eventId == null ? "" : eventId)));
            return;
        }
        CalendarEventOccurrence occurrence = activeOccurrence(definition, currentDay())
                .orElseGet(() -> nextOccurrence(definition, currentDay()));
        text.send(sender, "commands.calendar-event-detail", eventPlaceholders(occurrence));
    }

    private List<CalendarEventOccurrence> activeEvents() {
        long day = currentDay();
        List<CalendarEventOccurrence> active = new ArrayList<>();
        for (CalendarEventDefinition definition : events()) {
            activeOccurrence(definition, day).ifPresent(active::add);
        }
        return active.stream()
                .sorted(Comparator.comparingLong(CalendarEventOccurrence::startDay).thenComparing(occurrence -> occurrence.definition().id()))
                .toList();
    }

    private List<CalendarEventOccurrence> upcomingEvents() {
        long day = currentDay();
        return events().stream()
                .map(definition -> nextOccurrence(definition, day))
                .sorted(Comparator.comparingLong(CalendarEventOccurrence::startDay).thenComparing(occurrence -> occurrence.definition().id()))
                .toList();
    }

    private Optional<CalendarEventOccurrence> activeOccurrence(CalendarEventDefinition definition, long day) {
        if (definition.recurring()) {
            long offset = Math.floorMod(day - definition.firstDay(), definition.recurrenceDays());
            if (offset < definition.durationDays()) {
                long start = day - offset;
                return Optional.of(new CalendarEventOccurrence(definition, start, start + definition.durationDays()));
            }
            return Optional.empty();
        }
        long yearStart = Math.floorDiv(day, daysPerYear()) * daysPerYear();
        for (long candidateYearStart : List.of(yearStart - daysPerYear(), yearStart)) {
            long start = candidateYearStart + eventDayOfYear(definition);
            if (day >= start && day < start + definition.durationDays()) {
                return Optional.of(new CalendarEventOccurrence(definition, start, start + definition.durationDays()));
            }
        }
        return Optional.empty();
    }

    private CalendarEventOccurrence nextOccurrence(CalendarEventDefinition definition, long day) {
        if (definition.recurring()) {
            long offset = Math.floorMod(day - definition.firstDay(), definition.recurrenceDays());
            long start = day - offset;
            if (start <= day) {
                start += definition.recurrenceDays();
            }
            return new CalendarEventOccurrence(definition, start, start + definition.durationDays());
        }
        long yearStart = Math.floorDiv(day, daysPerYear()) * daysPerYear();
        long start = yearStart + eventDayOfYear(definition);
        if (start <= day) {
            start += daysPerYear();
        }
        return new CalendarEventOccurrence(definition, start, start + definition.durationDays());
    }

    private List<TextService.TextPlaceholder> eventPlaceholders(CalendarEventOccurrence occurrence) {
        CalendarEventDefinition definition = occurrence.definition();
        long day = currentDay();
        boolean active = day >= occurrence.startDay() && day < occurrence.endDay();
        return List.of(
                TextService.raw("id", definition.id()),
                TextService.parsed("event", definition.displayName()),
                TextService.raw("description", definition.description()),
                TextService.parsed("status", text.rawMessage(active ? "calendar.status-active" : "calendar.status-upcoming")),
                TextService.raw("starts", formatDate(dateAt(occurrence.startDay()))),
                TextService.raw("ends", formatDate(dateAt(occurrence.endDay() - 1L))),
                TextService.raw("until_start", formatRelative(Math.max(0L, occurrence.startDay() - day))),
                TextService.raw("remaining", formatRelative(Math.max(0L, occurrence.endDay() - day))),
                TextService.parsed("rewards", rewards(definition))
        );
    }

    private String nextEventLine(CalendarEventOccurrence occurrence) {
        return text.rawMessage("calendar.next-event")
                .replace("<event>", occurrence.definition().displayName())
                .replace("<until_start>", formatRelative(Math.max(0L, occurrence.startDay() - currentDay())));
    }

    private String rewards(CalendarEventDefinition definition) {
        if (definition.rewards().isEmpty()) {
            return text.rawMessage("calendar.no-rewards");
        }
        return String.join("<gray>,</gray> ", definition.rewards());
    }

    private String formatDate(SkyBlockDate date) {
        return date.monthName() + " " + date.day() + ", Year " + date.year();
    }

    private String formatRelative(long skyBlockDays) {
        if (skyBlockDays <= 0L) {
            return text.rawMessage("calendar.now");
        }
        long realSeconds = skyBlockDays * daySeconds();
        return skyBlockDays + "d SB (" + formatRealDuration(realSeconds) + ")";
    }

    private String formatRealDuration(long seconds) {
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    private SkyBlockDate dateAt(long absoluteDay) {
        int daysPerYear = daysPerYear();
        int year = (int) Math.floorDiv(absoluteDay, daysPerYear) + 1;
        int dayOfYear = (int) Math.floorMod(absoluteDay, daysPerYear);
        int monthIndex = Math.min(months().size() - 1, dayOfYear / daysPerMonth());
        int day = dayOfYear % daysPerMonth() + 1;
        return new SkyBlockDate(absoluteDay, year, monthIndex + 1, months().get(monthIndex), day);
    }

    private long currentDay() {
        long elapsedMillis = Math.max(0L, System.currentTimeMillis() - epochMillis());
        return elapsedMillis / (daySeconds() * 1000L);
    }

    private long epochMillis() {
        return Math.max(0L, configService.calendar().getLong("settings.epoch-millis", 0L));
    }

    private long daySeconds() {
        return Math.max(60L, configService.calendar().getLong("settings.day-seconds", 1200L));
    }

    private int daysPerMonth() {
        return Math.max(1, configService.calendar().getInt("settings.days-per-month", 31));
    }

    private int daysPerYear() {
        return months().size() * daysPerMonth();
    }

    private int eventDayOfYear(CalendarEventDefinition definition) {
        int month = Math.max(1, Math.min(months().size(), definition.month()));
        int day = Math.max(1, Math.min(daysPerMonth(), definition.day()));
        return (month - 1) * daysPerMonth() + day - 1;
    }

    private List<String> months() {
        List<String> configured = configService.calendar().getStringList("settings.months");
        return configured.isEmpty() ? DEFAULT_MONTHS : configured;
    }

    private int upcomingLimit() {
        return Math.max(1, Math.min(20, configService.calendar().getInt("settings.upcoming-limit", 5)));
    }
}
