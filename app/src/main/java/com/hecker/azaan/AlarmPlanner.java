package com.hecker.azaan;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Pure scheduling policy; reused by the receiver and the next-prayer display. */
public final class AlarmPlanner {
    private AlarmPlanner() {}
    public static final class Event {
        public final Prayer prayer;
        public final Instant time;
        public Event(Prayer prayer, Instant time) { this.prayer = prayer; this.time = time; }
    }
    public static List<Event> upcoming(Instant now, ZoneId zone, double lat, double lon,
            PrayerCalculator.Method method, int[] offsets, boolean[] enabled) {
        if (enabled == null || enabled.length != 3) throw new IllegalArgumentException("Three prayer switches are required.");
        List<Event> events = new ArrayList<>();
        LocalDate today = now.atZone(zone).toLocalDate();
        // Include yesterday: offsets or a timezone far from the coordinates can cross midnight.
        for (int day = -1; day <= 3; day++) {
            Map<Prayer, Instant> times = PrayerCalculator.calculate(today.plusDays(day), lat, lon, method, offsets);
            for (Prayer p : Prayer.values()) {
                Instant t = times.get(p);
                if (enabled[p.ordinal()] && t != null && t.isAfter(now)) events.add(new Event(p, t));
            }
        }
        events.sort(Comparator.comparing(event -> event.time));
        return events;
    }
    public static boolean shouldPlay(long now, long scheduled, boolean enabled,
                                     String eventKey, String lastEventKey) {
        long age = now - scheduled;
        return enabled && age >= 0 && age <= 5 * 60_000L && !eventKey.equals(lastEventKey);
    }
}
