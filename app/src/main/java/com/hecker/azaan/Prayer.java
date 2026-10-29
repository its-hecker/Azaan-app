package com.hecker.azaan;

public enum Prayer {
    FAJR("Fajr", "فجر", "Fajr"),
    DHUHR("Dhuhr · Asr", "ظہر اور عصر", "Dhuhr and Asr"),
    MAGHRIB("Maghrib · Isha", "مغرب اور عشاء", "Maghrib and Isha");

    public final String title;
    public final String urdu;
    public final String spoken;

    Prayer(String title, String urdu, String spoken) {
        this.title = title;
        this.urdu = urdu;
        this.spoken = spoken;
    }
}
