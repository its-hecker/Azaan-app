package com.hecker.azaan;

import org.junit.Test;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import static org.junit.Assert.*;

public class AlarmPlannerTest {
    private List<AlarmPlanner.Event> events(String now, boolean[] enabled) {
        return AlarmPlanner.upcoming(Instant.parse(now), ZoneId.of("Asia/Karachi"),24.8607,67.0011,PrayerCalculator.Method.JAFARI,new int[]{0,1,0},enabled);
    }
    @Test public void threeSessionsHaveNoSeparateAsrOrIshaAlarm() {
        List<AlarmPlanner.Event> events = events("2026-10-28T19:00:00Z",new boolean[]{true,true,true});
        assertEquals(Prayer.FAJR,events.get(0).prayer);
        assertEquals(Prayer.DHUHR,events.get(1).prayer);
        assertEquals(Prayer.MAGHRIB,events.get(2).prayer);
        assertEquals(3,Prayer.values().length);
    }
    @Test public void passedPrayersRollToTomorrow() {
        List<AlarmPlanner.Event> events = events("2026-10-29T14:00:00Z",new boolean[]{true,true,true});
        assertEquals(Prayer.FAJR,events.get(0).prayer);
        assertEquals("2026-10-30",events.get(0).time.atZone(ZoneId.of("Asia/Karachi")).toLocalDate().toString());
        for (AlarmPlanner.Event e : events) assertTrue(e.time.isAfter(Instant.parse("2026-10-29T14:00:00Z")));
    }
    @Test public void disabledPrayersAreNeverPlanned() {
        List<AlarmPlanner.Event> events = events("2026-10-28T19:00:00Z",new boolean[]{false,true,false});
        assertFalse(events.isEmpty());
        for (AlarmPlanner.Event e : events) assertEquals(Prayer.DHUHR,e.prayer);
        assertTrue(events("2026-10-28T19:00:00Z",new boolean[3]).isEmpty());
    }
    @Test public void staleDuplicateDisabledAndEarlyAlarmsAreRejected() {
        long at = 10_000_000;
        assertTrue(AlarmPlanner.shouldPlay(at,at,true,"fajr:today","fajr:yesterday"));
        assertTrue(AlarmPlanner.shouldPlay(at+300_000,at,true,"fajr:today",""));
        assertFalse(AlarmPlanner.shouldPlay(at+300_001,at,true,"fajr:today",""));
        assertFalse(AlarmPlanner.shouldPlay(at-1,at,true,"fajr:today",""));
        assertFalse(AlarmPlanner.shouldPlay(at,at,false,"fajr:today",""));
        assertFalse(AlarmPlanner.shouldPlay(at,at,true,"fajr:today","fajr:today"));
    }
    @Test public void plannerStaysSortedAcrossDst() {
        Instant now = Instant.parse("2026-03-28T23:30:00Z");
        List<AlarmPlanner.Event> events = AlarmPlanner.upcoming(now,ZoneId.of("Europe/London"),51.5074,-0.1278,PrayerCalculator.Method.JAFARI,new int[3],new boolean[]{true,true,true});
        Instant previous = now;
        for (AlarmPlanner.Event e : events) { assertTrue(e.time.isAfter(previous)); previous = e.time; }
    }
}
