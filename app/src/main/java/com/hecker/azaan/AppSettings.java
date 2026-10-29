package com.hecker.azaan;

import android.content.Context;
import android.content.SharedPreferences;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public final class AppSettings {
    final SharedPreferences prefs;
    public AppSettings(Context context) { prefs = context.getSharedPreferences("azaan", Context.MODE_PRIVATE); }
    public boolean configured() { return prefs.getBoolean("configured", false); }
    public String city() { return prefs.getString("city", "Choose your location"); }
    public double latitude() { return Double.longBitsToDouble(prefs.getLong("lat", Double.doubleToLongBits(0))); }
    public double longitude() { return Double.longBitsToDouble(prefs.getLong("lon", Double.doubleToLongBits(0))); }
    public ZoneId zone() {
        try { return ZoneId.of(prefs.getString("zone", ZoneId.systemDefault().getId())); }
        catch (Exception e) { return ZoneId.systemDefault(); }
    }
    public void location(String city, double lat, double lon, ZoneId zone) {
        if (!Double.isFinite(lat) || lat < -90 || lat > 90 || !Double.isFinite(lon) || lon < -180 || lon > 180)
            throw new IllegalArgumentException("Enter a valid latitude and longitude.");
        prefs.edit().putString("city", city.trim().isEmpty() ? "My location" : city.trim())
            .putLong("lat", Double.doubleToLongBits(lat)).putLong("lon", Double.doubleToLongBits(lon))
            .putString("zone", zone.getId()).putBoolean("configured", true).apply();
    }
    public boolean active() { return prefs.getBoolean("active", false); }
    public void active(boolean value) { prefs.edit().putBoolean("active", value).apply(); }
    public boolean prayerEnabled(Prayer p) { return prefs.getBoolean("prayer_" + p.name(), true); }
    public void prayerEnabled(Prayer p, boolean value) { prefs.edit().putBoolean("prayer_" + p.name(), value).apply(); }
    public boolean[] enabled() { return new boolean[]{prayerEnabled(Prayer.FAJR), prayerEnabled(Prayer.DHUHR), prayerEnabled(Prayer.MAGHRIB)}; }
    public int[] offsets() { return new int[]{offset(Prayer.FAJR), offset(Prayer.DHUHR), offset(Prayer.MAGHRIB)}; }
    public int offset(Prayer p) { return prefs.getInt("offset_" + p.name(), p == Prayer.DHUHR ? 1 : 0); }
    public void offsets(int[] offsets) {
        SharedPreferences.Editor e = prefs.edit();
        for (Prayer p : Prayer.values()) e.putInt("offset_" + p.name(), offsets[p.ordinal()]);
        e.apply();
    }
    public PrayerCalculator.Method method() {
        try { return PrayerCalculator.Method.valueOf(prefs.getString("method", "JAFARI")); }
        catch (IllegalArgumentException e) { return PrayerCalculator.Method.JAFARI; }
    }
    public void method(PrayerCalculator.Method m) { prefs.edit().putString("method", m.name()).apply(); }
    public boolean wilayah() { return prefs.getBoolean("wilayah", true); }
    public void wilayah(boolean value) { prefs.edit().putBoolean("wilayah", value).apply(); }
    public boolean language(AzaanText.Language l) { return l == AzaanText.Language.ARABIC || prefs.getBoolean("lang_" + l.name(), true); }
    public void language(AzaanText.Language l, boolean value) { prefs.edit().putBoolean("lang_" + l.name(), value).apply(); }
    public List<AzaanText.Language> languages() {
        List<AzaanText.Language> result = new ArrayList<>();
        for (AzaanText.Language l : AzaanText.Language.values()) if (language(l)) result.add(l);
        return result;
    }
    public String recording(AzaanText.Language l) { return prefs.getString("audio_" + l.name(), ""); }
    public String recordingName(AzaanText.Language l) { return prefs.getString("audio_name_" + l.name(), "Imported recording"); }
    public void recording(AzaanText.Language l, String uri, String name) {
        prefs.edit().putString("audio_" + l.name(), uri).putString("audio_name_" + l.name(), name).apply();
    }
    public float volume() { return prefs.getInt("volume", 90) / 100f; }
    public void volume(int percent) { prefs.edit().putInt("volume", Math.max(0, Math.min(100, percent))).apply(); }
    public String status() { return prefs.getString("status", "Ready for your first preview"); }
    public void status(String value) { prefs.edit().putString("status", value).apply(); }
}
