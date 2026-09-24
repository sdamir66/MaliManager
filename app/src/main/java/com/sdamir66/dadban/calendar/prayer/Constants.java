package com.sdamir66.dadban.calendar.prayer;

public class Constants {

    // ═══ Time indexes ═══
    public static final int TIMES_IMSAK = 0;
    public static final int TIMES_FAJR = 1;
    public static final int TIMES_SUNRISE = 2;
    public static final int TIMES_ZAWAL = 3;
    public static final int TIMES_DHUHR = 4;
    public static final int TIMES_ASR_SHAFII = 5;
    public static final int TIMES_ASR_HANAFI = 6;
    public static final int TIMES_ASR = 7;
    public static final int TIMES_SUNSET = 8;
    public static final int TIMES_MAGHRIB = 9;
    public static final int TIMES_ISHA = 10;
    public static final int TIMES_MIDNIGHT = 11;

    // ═══ Juristic methods ═══
    public static final int JURISTIC_STANDARD = 0;  // Shafii (ضریب سایه = 1)
    public static final int JURISTIC_HANAFI = 1;    // Hanafi (ضریب سایه = 2)

    // ═══ High latitude adjustments ═══
    public static final int HIGHLAT_NONE = 0;
    public static final int HIGHLAT_NIGHTMIDDLE = 1;
    public static final int HIGHLAT_ONESEVENTH = 2;
    public static final int HIGHLAT_ANGLEBASED = 3;

    // ═══ Midnight modes ═══
    public static final int MIDNIGHT_STANDARD = 0;  // وسط غروب تا طلوع
    public static final int MIDNIGHT_JAFARI = 1;    // وسط غروب تا فجر (شیعه)
}
