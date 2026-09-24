package com.sdamir66.dadban.calendar.prayer;

import java.util.TimeZone;

public class Parameters {

    public double imsak = 10;
    public boolean imsakMin = true;

    public double fajr = 18;

    public double dhuhr = 0;

    public double maghrib = 4.5;
    public boolean maghribMin = false;

    public double isha = 14;
    public boolean ishaMin = false;

    public int highLats = Constants.HIGHLAT_NONE;
    public int midnight = Constants.MIDNIGHT_JAFARI;
    public int asrJuristic = Constants.JURISTIC_STANDARD;

    public TimeZone timeZone = TimeZone.getDefault();

    public double[] tune = new double[12];

    public Parameters() {
        setMethod(Method.TEHRAN);
    }

    public void setMethod(Method method) {
        switch (method) {
            case JAFARI:
                fajr = 16;
                isha = 14;
                maghrib = 4.0;
                maghribMin = false;
                ishaMin = false;
                midnight = Constants.MIDNIGHT_JAFARI;
                break;
            case KARACHI:
                fajr = 18;
                isha = 18;
                maghrib = 0;
                maghribMin = false;
                ishaMin = false;
                midnight = Constants.MIDNIGHT_STANDARD;
                break;
            case ISNA:
                fajr = 15;
                isha = 15;
                maghrib = 0;
                maghribMin = false;
                ishaMin = false;
                midnight = Constants.MIDNIGHT_STANDARD;
                break;
            case MWL:
                fajr = 18;
                isha = 17;
                maghrib = 0;
                maghribMin = false;
                ishaMin = false;
                midnight = Constants.MIDNIGHT_STANDARD;
                break;
            case MAKKAH:
                fajr = 18.5;
                isha = 0;
                maghrib = 0;
                maghribMin = false;
                ishaMin = false;
                midnight = Constants.MIDNIGHT_STANDARD;
                break;
            case EGYPT:
                fajr = 19.5;
                isha = 17.5;
                maghrib = 0;
                maghribMin = false;
                ishaMin = false;
                midnight = Constants.MIDNIGHT_STANDARD;
                break;
            case TEHRAN:
                // ✅ مؤسسه ژئوفیزیک دانشگاه تهران
                fajr = 17.7;
                isha = 14;
                maghrib = 4.5;
                maghribMin = false;
                ishaMin = false;
                midnight = Constants.MIDNIGHT_JAFARI;
                break;
            case CUSTOM:
                break;
        }
    }
}
