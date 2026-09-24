package com.sdamir66.dadban.calendar.prayer;

import java.util.TimeZone;

public class Parameters {

    // ═══ روش‌های محاسبه ═══
    public enum Method {
        JAFARI,     // شیعه اثناعشری (فجر 16، عشا 14)
        KARACHI,    // دانشگاه علوم اسلامی کراچی (فجر 18، عشا 18)
        ISNA,       // انجمن اسلامی آمریکای شمالی (فجر 15، عشا 15)
        MWL,        // اتحادیه جهانی مسلمانان (فجر 18، عشا 17)
        MAKKAH,     // دانشگاه ام‌القری مکه (فجر 18.5، عشا 90 دقیقه بعد از مغرب)
        EGYPT,      // هیئت عمومی مصر (فجر 19.5، عشا 17.5)
        TEHRAN,     // مؤسسه ژئوفیزیک دانشگاه تهران (فجر 17.7، عشا 14، مغرب 4.5)
        CUSTOM      // سفارشی
    }

    public double imsak = 10;              // دقیقه قبل از فجر (پیش‌فرض 10)
    public boolean imsakMin = true;        // بر حسب دقیقه؟

    public double fajr = 18;               // زاویه فجر

    public double dhuhr = 0;               // دقیقه بعد از زوال (پیش‌فرض 0)

    public double maghrib = 4.5;           // زاویه مغرب (برای تهران)
    public boolean maghribMin = false;     // بر حسب دقیقه؟

    public double isha = 14;               // زاویه عشا
    public boolean ishaMin = false;        // بر حسب دقیقه؟

    public int highLats = Constants.HIGHLAT_NONE;   // تنظیم عرض بالا
    public int midnight = Constants.MIDNIGHT_JAFARI; // نیمه‌شب جعفری
    public int asrJuristic = Constants.JURISTIC_STANDARD; // اسر شافعی

    public TimeZone timeZone = TimeZone.getDefault();

    public double[] tune = new double[12];  // تنظیم دقیقه‌ای

    public Parameters() {
        setMethod(Method.TEHRAN);  // پیش‌فرض: تهران
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
                maghrib = 4.5;       // زاویه مغرب (ذهاب حمره مشرقیه)
                maghribMin = false;  // بر حسب درجه
                ishaMin = false;
                midnight = Constants.MIDNIGHT_JAFARI;
                break;
            case CUSTOM:
                // کاربر خودش تنظیم می‌کند
                break;
        }
    }
}
