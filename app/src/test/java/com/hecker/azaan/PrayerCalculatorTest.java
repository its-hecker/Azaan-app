package com.hecker.azaan;

import org.junit.Test;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import static org.junit.Assert.*;

public class PrayerCalculatorTest {
    // Independently produced by the upstream PrayTimes Python v2 reference,
    // with three iterations, no high-latitude substitution, and zero offsets.
    // Expected values are local decimal hours BEFORE the app's upward rounding.
    private void reference(String date, double lat, double lon, String zone,
            PrayerCalculator.Method method, double fajr, double dhuhr, double maghrib) {
        LocalDate day = LocalDate.parse(date);
        Map<Prayer, Instant> result = PrayerCalculator.calculate(day, lat, lon, method, new int[3]);
        double[] hours = {fajr, dhuhr, maghrib};
        assertEquals(3, result.size());
        for (Prayer p : Prayer.values()) {
            long expectedMinutes = (long) Math.ceil(hours[p.ordinal()] * 60 - 1e-5);
            ZonedDateTime expected = day.atStartOfDay(ZoneId.of(zone)).plusMinutes(expectedMinutes);
            assertEquals(p.name() + " at " + date, expected.toInstant(), result.get(p));
            assertEquals(day, result.get(p).atZone(ZoneId.of(zone)).toLocalDate());
        }
    }
    @Test public void pakistanAcrossSeasons() {
        reference("2026-10-29",24.8607,67.0011,"Asia/Karachi",PrayerCalculator.Method.JAFARI,5.4852831,12.2619527,18.1369065);
        reference("2026-06-21",31.5204,74.3587,"Asia/Karachi",PrayerCalculator.Method.JAFARI,3.5252433,12.0724536,19.4599246);
        reference("2026-12-21",33.6844,73.0479,"Asia/Karachi",PrayerCalculator.Method.JAFARI,5.8181795,12.0961593,17.3384041);
    }
    @Test public void iraqAndIranMethods() {
        reference("2026-03-20",31.9958,44.3148,"Asia/Baghdad",PrayerCalculator.Method.JAFARI,4.9169717,12.1706625,18.4844598);
        reference("2026-10-29",35.6892,51.3890,"Asia/Tehran",PrayerCalculator.Method.TEHRAN,4.9803742,11.8027113,17.5194812);
    }
    @Test public void londonDstTransitionUsesEventOffset() {
        // The reference is in local post-transition time, not midnight's UTC offset.
        LocalDate day = LocalDate.parse("2026-03-29");
        Map<Prayer,Instant> result = PrayerCalculator.calculate(day,51.5074,-0.1278,PrayerCalculator.Method.JAFARI,new int[3]);
        assertEquals("05:01", result.get(Prayer.FAJR).atZone(ZoneId.of("Europe/London")).toLocalTime().toString());
        assertEquals("13:06", result.get(Prayer.DHUHR).atZone(ZoneId.of("Europe/London")).toLocalTime().toString());
        assertEquals("19:50", result.get(Prayer.MAGHRIB).atZone(ZoneId.of("Europe/London")).toLocalTime().toString());
    }
    @Test public void southernHemisphereDstAndDateLine() {
        Map<Prayer,Instant> sydney = PrayerCalculator.calculate(LocalDate.parse("2026-10-04"),-33.8688,151.2093,PrayerCalculator.Method.JAFARI,new int[3]);
        assertEquals("05:15", sydney.get(Prayer.FAJR).atZone(ZoneId.of("Australia/Sydney")).toLocalTime().toString());
        reference("2026-10-29",-36.8485,174.7633,"Pacific/Auckland",PrayerCalculator.Method.JAFARI,4.9422369,13.0781483,20.1190420);
    }
    @Test public void adjustmentsAreAppliedExactly() {
        LocalDate day = LocalDate.of(2026,10,29);
        Map<Prayer,Instant> base = PrayerCalculator.calculate(day,24.8607,67.0011,PrayerCalculator.Method.JAFARI,new int[3]);
        Map<Prayer,Instant> adjusted = PrayerCalculator.calculate(day,24.8607,67.0011,PrayerCalculator.Method.JAFARI,new int[]{-5,1,7});
        assertEquals(-5, Duration.between(base.get(Prayer.FAJR),adjusted.get(Prayer.FAJR)).toMinutes());
        assertEquals(1, Duration.between(base.get(Prayer.DHUHR),adjusted.get(Prayer.DHUHR)).toMinutes());
        assertEquals(7, Duration.between(base.get(Prayer.MAGHRIB),adjusted.get(Prayer.MAGHRIB)).toMinutes());
    }
    @Test public void unavailablePolarCrossingsAreNotFabricated() {
        Map<Prayer,Instant> summer = PrayerCalculator.calculate(LocalDate.of(2026,6,21),90,0,PrayerCalculator.Method.JAFARI,new int[3]);
        assertFalse(summer.containsKey(Prayer.FAJR));
        assertFalse(summer.containsKey(Prayer.MAGHRIB));
        assertTrue(summer.containsKey(Prayer.DHUHR));
    }
    @Test public void allYearTimesRemainOrderedInPakistan() {
        LocalDate first = LocalDate.of(2026,1,1);
        for (int day = 0; day < 365; day++) {
            Map<Prayer,Instant> result = PrayerCalculator.calculate(first.plusDays(day),33.6844,73.0479,PrayerCalculator.Method.JAFARI,new int[3]);
            assertEquals(3,result.size());
            assertTrue(result.get(Prayer.FAJR).isBefore(result.get(Prayer.DHUHR)));
            assertTrue(result.get(Prayer.DHUHR).isBefore(result.get(Prayer.MAGHRIB)));
        }
    }
    @Test public void rejectsInvalidInput() {
        double[][] invalid = {{91,0},{0,181},{Double.NaN,0},{0,Double.POSITIVE_INFINITY}};
        for (double[] point : invalid) assertThrows(IllegalArgumentException.class, () -> PrayerCalculator.calculate(LocalDate.now(),point[0],point[1],PrayerCalculator.Method.JAFARI,new int[3]));
        assertThrows(IllegalArgumentException.class, () -> PrayerCalculator.calculate(LocalDate.now(),0,0,PrayerCalculator.Method.JAFARI,new int[]{0,61,0}));
        assertThrows(IllegalArgumentException.class, () -> PrayerCalculator.calculate(LocalDate.now(),0,0,PrayerCalculator.Method.JAFARI,new int[0]));
    }
}
