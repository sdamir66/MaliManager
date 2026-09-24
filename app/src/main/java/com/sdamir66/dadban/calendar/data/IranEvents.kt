package com.sdamir66.dadban.calendar.data

object IranEvents {

    val events: List<Event> = listOf(

        // ═══════════════════════════════════════════════════════
        // تعطیلات رسمی — ملی (جلالی)
        // ═══════════════════════════════════════════════════════
        Event(title = "جشن نوروز/جشن سال نو", calendarType = CalendarType.JALALI, month = 1, day = 1, isHoliday = true, category = EventCategory.NATIONAL),
        Event(title = "عیدنوروز", calendarType = CalendarType.JALALI, month = 1, day = 2, isHoliday = true, category = EventCategory.NATIONAL),
        Event(title = "عیدنوروز", calendarType = CalendarType.JALALI, month = 1, day = 3, isHoliday = true, category = EventCategory.NATIONAL),
        Event(title = "عیدنوروز", calendarType = CalendarType.JALALI, month = 1, day = 4, isHoliday = true, category = EventCategory.NATIONAL),
        Event(title = "روز جمهوری اسلامی", calendarType = CalendarType.JALALI, month = 1, day = 12, isHoliday = true, category = EventCategory.NATIONAL),
        Event(title = "جشن سیزده به در", calendarType = CalendarType.JALALI, month = 1, day = 13, isHoliday = true, category = EventCategory.NATIONAL),
        Event(title = "رحلت حضرت امام خمینی", calendarType = CalendarType.JALALI, month = 3, day = 14, isHoliday = true, category = EventCategory.NATIONAL),
        Event(title = "قیام 15 خرداد", calendarType = CalendarType.JALALI, month = 3, day = 15, isHoliday = true, category = EventCategory.NATIONAL),
        Event(title = "پیروزی انقلاب اسلامی", calendarType = CalendarType.JALALI, month = 11, day = 22, isHoliday = true, category = EventCategory.NATIONAL),
        Event(title = "روز ملی شدن صنعت نفت ایران", calendarType = CalendarType.JALALI, month = 12, day = 29, isHoliday = true, category = EventCategory.NATIONAL),

        // ═══════════════════════════════════════════════════════
        // تعطیلات رسمی — مذهبی (قمری) — فقط تعطیلات واقعی
        // ═══════════════════════════════════════════════════════
        // محرم
        Event(title = "تاسوعای حسینی", calendarType = CalendarType.HIJRI, month = 1, day = 9, isHoliday = true, category = EventCategory.RELIGIOUS),
        Event(title = "عاشورای حسینی", calendarType = CalendarType.HIJRI, month = 1, day = 10, isHoliday = true, category = EventCategory.RELIGIOUS),
        // صفر
        Event(title = "اربعین حسینی", calendarType = CalendarType.HIJRI, month = 2, day = 20, isHoliday = true, category = EventCategory.RELIGIOUS),
        Event(title = "رحلت رسول اکرم؛ شهادت امام حسن مجتبی (ع)", calendarType = CalendarType.HIJRI, month = 2, day = 28, isHoliday = true, category = EventCategory.RELIGIOUS),
        Event(title = "شهادت امام رضا (ع)", calendarType = CalendarType.HIJRI, month = 2, day = 30, isHoliday = true, category = EventCategory.RELIGIOUS),
        // ربیع‌الاول
        Event(title = "شهادت امام حسن عسکری (ع)", calendarType = CalendarType.HIJRI, month = 3, day = 8, isHoliday = true, category = EventCategory.RELIGIOUS),
        Event(title = "میلاد رسول اکرم (ص) و امام جعفر صادق (ع)", calendarType = CalendarType.HIJRI, month = 3, day = 17, isHoliday = true, category = EventCategory.RELIGIOUS),
        // جمادی‌الثانی
        Event(title = "شهادت حضرت فاطمه زهرا (س)", calendarType = CalendarType.HIJRI, month = 6, day = 3, isHoliday = true, category = EventCategory.RELIGIOUS),
        // رجب
        Event(title = "ولادت امام علی (ع) و روز پدر", calendarType = CalendarType.HIJRI, month = 7, day = 13, isHoliday = true, category = EventCategory.RELIGIOUS),
        Event(title = "مبعث رسول اکرم (ص)", calendarType = CalendarType.HIJRI, month = 7, day = 27, isHoliday = true, category = EventCategory.RELIGIOUS),
        // شعبان
        Event(title = "ولادت حضرت قائم (عج) و جشن نیمه شعبان", calendarType = CalendarType.HIJRI, month = 8, day = 15, isHoliday = true, category = EventCategory.RELIGIOUS),
        // رمضان
        Event(title = "شهادت حضرت علی (ع)", calendarType = CalendarType.HIJRI, month = 9, day = 21, isHoliday = true, category = EventCategory.RELIGIOUS),
        // شوال
        Event(title = "عید سعید فطر", calendarType = CalendarType.HIJRI, month = 10, day = 1, isHoliday = true, category = EventCategory.RELIGIOUS),
        Event(title = "تعطیل به مناسبت عید سعید فطر", calendarType = CalendarType.HIJRI, month = 10, day = 2, isHoliday = true, category = EventCategory.RELIGIOUS),
        // ذی‌القعده
        Event(title = "شهادت امام محمد تقی (ع)", calendarType = CalendarType.HIJRI, month = 11, day = 30, isHoliday = false, category = EventCategory.RELIGIOUS),
        // ذی‌الحجه
        Event(title = "عید سعید قربان", calendarType = CalendarType.HIJRI, month = 12, day = 10, isHoliday = true, category = EventCategory.RELIGIOUS),
        Event(title = "عید سعید غدیر خم", calendarType = CalendarType.HIJRI, month = 12, day = 18, isHoliday = true, category = EventCategory.RELIGIOUS),

        // ═══════════════════════════════════════════════════════
        // رویدادهای مذهبی (غیرتعطیل)
        // ═══════════════════════════════════════════════════════
        Event(title = "ولادت حضرت معصومه (س)، روز دختران", calendarType = CalendarType.HIJRI, month = 11, day = 1, isHoliday = false, category = EventCategory.RELIGIOUS),
        Event(title = "ولادت امام رضا (ع)", calendarType = CalendarType.HIJRI, month = 11, day = 11, isHoliday = false, category = EventCategory.RELIGIOUS),
        Event(title = "ولادت امام علی النقی (ع)", calendarType = CalendarType.HIJRI, month = 12, day = 15, isHoliday = false, category = EventCategory.RELIGIOUS),
        Event(title = "ولادت امام موسی کاظم (ع)", calendarType = CalendarType.HIJRI, month = 12, day = 20, isHoliday = false, category = EventCategory.RELIGIOUS),
        Event(title = "روز عرفه", calendarType = CalendarType.HIJRI, month = 12, day = 9, isHoliday = false, category = EventCategory.RELIGIOUS),
        Event(title = "شهادت امام زین العابدین (ع)", calendarType = CalendarType.HIJRI, month = 1, day = 12, isHoliday = false, category = EventCategory.RELIGIOUS),
        Event(title = "هجرت پیامبر اکرم از مکه به مدینه", calendarType = CalendarType.HIJRI, month = 3, day = 1, isHoliday = false, category = EventCategory.RELIGIOUS),
        Event(title = "میلاد رسول اکرم به روایت اهل سنت", calendarType = CalendarType.HIJRI, month = 3, day = 12, isHoliday = false, category = EventCategory.RELIGIOUS),
        Event(title = "ولادت امام حسن عسکری (ع)", calendarType = CalendarType.HIJRI, month = 4, day = 8, isHoliday = false, category = EventCategory.RELIGIOUS),
        Event(title = "وفات حضرت معصومه (س)", calendarType = CalendarType.HIJRI, month = 4, day = 10, isHoliday = false, category = EventCategory.RELIGIOUS),
        Event(title = "ولادت حضرت زینب (س) و روز پرستار", calendarType = CalendarType.HIJRI, month = 5, day = 5, isHoliday = false, category = EventCategory.RELIGIOUS),
        Event(title = "ولادت حضرت فاطمه زهرا (س) و روز مادر", calendarType = CalendarType.HIJRI, month = 6, day = 20, isHoliday = false, category = EventCategory.RELIGIOUS),
        Event(title = "ولادت امام محمد باقر (ع)", calendarType = CalendarType.HIJRI, month = 7, day = 1, isHoliday = false, category = EventCategory.RELIGIOUS),
        Event(title = "شهادت امام علی النقی (ع)", calendarType = CalendarType.HIJRI, month = 7, day = 3, isHoliday = false, category = EventCategory.RELIGIOUS),
        Event(title = "ولادت امام محمد تقی (ع)", calendarType = CalendarType.HIJRI, month = 7, day = 10, isHoliday = false, category = EventCategory.RELIGIOUS),
        Event(title = "وفات حضرت زینب (س)", calendarType = CalendarType.HIJRI, month = 7, day = 15, isHoliday = false, category = EventCategory.RELIGIOUS),
        Event(title = "شهادت امام موسی کاظم (ع)", calendarType = CalendarType.HIJRI, month = 7, day = 25, isHoliday = false, category = EventCategory.RELIGIOUS),
        Event(title = "ولادت امام حسین (ع) و روز پاسدار", calendarType = CalendarType.HIJRI, month = 8, day = 3, isHoliday = false, category = EventCategory.RELIGIOUS),
        Event(title = "ولادت حضرت ابوالفضل العباس (ع) و روز جانباز", calendarType = CalendarType.HIJRI, month = 8, day = 4, isHoliday = false, category = EventCategory.RELIGIOUS),
        Event(title = "ولادت امام زین العابدین (ع)", calendarType = CalendarType.HIJRI, month = 8, day = 5, isHoliday = false, category = EventCategory.RELIGIOUS),
        Event(title = "ولادت حضرت علی اکبر (ع) و روز جوان", calendarType = CalendarType.HIJRI, month = 8, day = 11, isHoliday = false, category = EventCategory.RELIGIOUS),
        Event(title = "ولادت امام حسن مجتبی (ع)", calendarType = CalendarType.HIJRI, month = 9, day = 15, isHoliday = false, category = EventCategory.RELIGIOUS),
        Event(title = "شب قدر", calendarType = CalendarType.HIJRI, month = 9, day = 18, isHoliday = false, category = EventCategory.RELIGIOUS),
        Event(title = "ضربت خوردن حضرت علی (ع)", calendarType = CalendarType.HIJRI, month = 9, day = 19, isHoliday = false, category = EventCategory.RELIGIOUS),
        Event(title = "شب قدر", calendarType = CalendarType.HIJRI, month = 9, day = 22, isHoliday = false, category = EventCategory.RELIGIOUS),

        // ═══════════════════════════════════════════════════════
        // رویدادهای ملی (غیرتعطیل)
        // ═══════════════════════════════════════════════════════
        Event(title = "روز امید، روز شادباش نویسی", calendarType = CalendarType.JALALI, month = 1, day = 6, isHoliday = false, category = EventCategory.NATIONAL),
        Event(title = "زادروز اَشو زرتشت", calendarType = CalendarType.JALALI, month = 1, day = 6, isHoliday = false, category = EventCategory.NATIONAL),
        Event(title = "روز بزرگداشت عطار نیشابوری", calendarType = CalendarType.JALALI, month = 1, day = 25, isHoliday = false, category = EventCategory.NATIONAL),
        Event(title = "روز ارتش جمهوری اسلامی ایران", calendarType = CalendarType.JALALI, month = 1, day = 29, isHoliday = false, category = EventCategory.NATIONAL),
        Event(title = "روز بزرگداشت سعدی", calendarType = CalendarType.JALALI, month = 2, day = 1, isHoliday = false, category = EventCategory.NATIONAL),
        Event(title = "روز معلم", calendarType = CalendarType.JALALI, month = 2, day = 12, isHoliday = false, category = EventCategory.NATIONAL),
        Event(title = "روز بزرگداشت فردوسی", calendarType = CalendarType.JALALI, month = 2, day = 25, isHoliday = false, category = EventCategory.NATIONAL),
        Event(title = "روز بزرگداشت حکیم عمر خیام", calendarType = CalendarType.JALALI, month = 2, day = 28, isHoliday = false, category = EventCategory.NATIONAL),
        Event(title = "فتح خرمشهر در عملیات بیت المقدس", calendarType = CalendarType.JALALI, month = 3, day = 3, isHoliday = false, category = EventCategory.NATIONAL),
        Event(title = "زادروز کوروش بزرگ", calendarType = CalendarType.JALALI, month = 6, day = 4, isHoliday = false, category = EventCategory.NATIONAL),
        Event(title = "روز بزرگداشت ابوعلی سینا و روز پزشک", calendarType = CalendarType.JALALI, month = 6, day = 1, isHoliday = false, category = EventCategory.NATIONAL),
        Event(title = "روز بزرگداشت ابوریحان بیرونی", calendarType = CalendarType.JALALI, month = 6, day = 13, isHoliday = false, category = EventCategory.NATIONAL),
        Event(title = "روز شعر و ادب پارسی و بزرگداشت شهریار", calendarType = CalendarType.JALALI, month = 6, day = 27, isHoliday = false, category = EventCategory.NATIONAL),
        Event(title = "روز بزرگداشت مولوی", calendarType = CalendarType.JALALI, month = 7, day = 8, isHoliday = false, category = EventCategory.NATIONAL),
        Event(title = "روز بزرگداشت حافظ", calendarType = CalendarType.JALALI, month = 7, day = 20, isHoliday = false, category = EventCategory.NATIONAL),
        Event(title = "روز کتاب و کتابخوانی", calendarType = CalendarType.JALALI, month = 8, day = 24, isHoliday = false, category = EventCategory.NATIONAL),
        Event(title = "روز بسیج مستضعفان", calendarType = CalendarType.JALALI, month = 9, day = 5, isHoliday = false, category = EventCategory.NATIONAL),
        Event(title = "روز حسابدار", calendarType = CalendarType.JALALI, month = 9, day = 15, isHoliday = false, category = EventCategory.NATIONAL),
        Event(title = "جشن شب یلدا، شب چلّه", calendarType = CalendarType.JALALI, month = 9, day = 30, isHoliday = false, category = EventCategory.NATIONAL),
        Event(title = "روز حافظ", calendarType = CalendarType.JALALI, month = 10, day = 12, isHoliday = false, category = EventCategory.NATIONAL),
        Event(title = "شهادت سردار حاج قاسم سلیمانی", calendarType = CalendarType.JALALI, month = 10, day = 13, isHoliday = false, category = EventCategory.NATIONAL),
        Event(title = "زادروز فردوسی", calendarType = CalendarType.JALALI, month = 11, day = 1, isHoliday = false, category = EventCategory.NATIONAL),
        Event(title = "بازگشت امام خمینی (ره) به ایران", calendarType = CalendarType.JALALI, month = 11, day = 12, isHoliday = false, category = EventCategory.NATIONAL),
        Event(title = "جشن سپندارمذگان و روز عشق", calendarType = CalendarType.JALALI, month = 11, day = 29, isHoliday = false, category = EventCategory.NATIONAL),
        Event(title = "روز بزرگداشت خواجه نصیر الدین طوسی و روز مهندس", calendarType = CalendarType.JALALI, month = 12, day = 5, isHoliday = false, category = EventCategory.NATIONAL),
        Event(title = "روز درختکاری", calendarType = CalendarType.JALALI, month = 12, day = 15, isHoliday = false, category = EventCategory.NATIONAL),
        Event(title = "روز بزرگداشت پروین اعتصامی", calendarType = CalendarType.JALALI, month = 12, day = 25, isHoliday = false, category = EventCategory.NATIONAL),

        // ═══════════════════════════════════════════════════════
        // رویدادهای جهانی (غیرتعطیل)
        // ═══════════════════════════════════════════════════════
        Event(title = "روز جهانی نوروز", calendarType = CalendarType.GREGORIAN, month = 3, day = 21, isHoliday = false, category = EventCategory.GLOBAL),
        Event(title = "روز جهانی بهداشت", calendarType = CalendarType.GREGORIAN, month = 4, day = 7, isHoliday = false, category = EventCategory.GLOBAL),
        Event(title = "روز جهانی کارگر", calendarType = CalendarType.GREGORIAN, month = 5, day = 1, isHoliday = false, category = EventCategory.GLOBAL),
        Event(title = "روز جهانی پرستار", calendarType = CalendarType.GREGORIAN, month = 5, day = 12, isHoliday = false, category = EventCategory.GLOBAL),
        Event(title = "روز جهانی محیط زیست", calendarType = CalendarType.GREGORIAN, month = 6, day = 5, isHoliday = false, category = EventCategory.GLOBAL),
        Event(title = "روز جهانی صلح", calendarType = CalendarType.GREGORIAN, month = 9, day = 21, isHoliday = false, category = EventCategory.GLOBAL),
        Event(title = "روز جهانی معلم", calendarType = CalendarType.GREGORIAN, month = 10, day = 5, isHoliday = false, category = EventCategory.GLOBAL),
        Event(title = "روز جهانی زنان", calendarType = CalendarType.GREGORIAN, month = 3, day = 8, isHoliday = false, category = EventCategory.GLOBAL),
        Event(title = "جشن کریسمس", calendarType = CalendarType.GREGORIAN, month = 12, day = 25, isHoliday = false, category = EventCategory.GLOBAL),
        Event(title = "جشن آغاز سال نو میلادی", calendarType = CalendarType.GREGORIAN, month = 1, day = 1, isHoliday = false, category = EventCategory.GLOBAL)
    )
}
