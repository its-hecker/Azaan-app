package com.hecker.azaan;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;

/** Solar equations documented at https://praytimes.org/docs/calculation.
 * All intermediate times are UTC, so DST is applied at the event, not at noon.
 * No substitute times are invented when a twilight crossing does not exist.
 */
public final class PrayerCalculator {
    private PrayerCalculator() {}
    public enum Method {
        JAFARI("Ja‘fari · Qom", 16.0, 4.0),
        TEHRAN("Tehran", 17.7, 4.5);
        public final String title;
        final double fajrAngle, maghribAngle;
        Method(String title, double fajrAngle, double maghribAngle) {
            this.title = title; this.fajrAngle = fajrAngle; this.maghribAngle = maghribAngle;
        }
    }

    public static Map<Prayer, Instant> calculate(LocalDate date, double latitude,
            double longitude, Method method, int[] offsets) {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180)
            throw new IllegalArgumentException("Coordinates are outside their valid range.");
        if (method == null || offsets == null || offsets.length != 3)
            throw new IllegalArgumentException("A method and three offsets are required.");
        for (int offset : offsets) if (offset < -60 || offset > 60)
            throw new IllegalArgumentException("Offsets must be between -60 and 60 minutes.");
        double jd = date.toEpochDay() + 2440587.5 - longitude / 360.0;
        double fajr = 5, noon = 12, maghrib = 18;
        // Refine solar coordinates at each event, expressed as solar-day hours.
        for (int i = 0; i < 3; i++) {
            fajr = crossing(jd, latitude, method.fajrAngle, fajr, true);
            noon = 12 - sun(jd + noon / 24.0)[1];
            maghrib = crossing(jd, latitude, method.maghribAngle, maghrib, false);
        }
        double[] hours = {fajr, noon, maghrib};
        Map<Prayer, Instant> result = new EnumMap<>(Prayer.class);
        for (Prayer prayer : Prayer.values()) {
            double value = hours[prayer.ordinal()] - longitude / 15.0;
            if (!Double.isFinite(value)) continue;
            // Round up, never before the calculated crossing.
            long minutes = (long) Math.ceil(value * 60.0 - 1e-9) + offsets[prayer.ordinal()];
            result.put(prayer, date.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(minutes * 60));
        }
        return result;
    }

    private static double crossing(double jd, double lat, double angle, double guess, boolean morning) {
        if (!Double.isFinite(guess)) return Double.NaN;
        double[] s = sun(jd + guess / 24.0);
        double divisor = cos(lat) * cos(s[0]);
        if (Math.abs(divisor) < 1e-12) return Double.NaN;
        double c = (-sin(angle) - sin(lat) * sin(s[0])) / divisor;
        if (c < -1 || c > 1) return Double.NaN;
        double h = Math.toDegrees(Math.acos(c)) / 15.0;
        return 12 - s[1] + (morning ? -h : h);
    }

    /** Returns declination in degrees and equation of time in hours. */
    private static double[] sun(double jd) {
        double d = jd - 2451545;
        double g = normalize(357.529 + 0.98560028 * d, 360);
        double q = normalize(280.459 + 0.98564736 * d, 360);
        double l = normalize(q + 1.915 * sin(g) + 0.020 * sin(2 * g), 360);
        double e = 23.439 - 0.00000036 * d;
        double ra = normalize(Math.toDegrees(Math.atan2(cos(e) * sin(l), cos(l))) / 15, 24);
        double equation = q / 15 - ra;
        if (equation > 12) equation -= 24;
        if (equation < -12) equation += 24;
        return new double[]{Math.toDegrees(Math.asin(sin(e) * sin(l))), equation};
    }
    private static double sin(double deg) { return Math.sin(Math.toRadians(deg)); }
    private static double cos(double deg) { return Math.cos(Math.toRadians(deg)); }
    private static double normalize(double value, double cycle) { return value - cycle * Math.floor(value / cycle); }
}
