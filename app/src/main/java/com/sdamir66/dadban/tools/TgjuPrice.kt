package com.sdamir66.dadban.tools

// ═══════════════════════════════════════════════════════════════
//  TgjuPrice
// ═══════════════════════════════════════════════════════════════

data class TgjuPrice(
    val key: String,
    val title: String,
    val price: Double,
    val change: Double,
    val changePercent: Double,
    val direction: String,
    val low: Double,
    val high: Double,
    val time: String
)

// ═══════════════════════════════════════════════════════════════
//  PriceCatalog
// ═══════════════════════════════════════════════════════════════

object PriceCatalog {

    // ═══════════════════════════════════════════════════════════
    //  رمزارزها — با پیشوند crypto_ که با item_idهای tgju قاطی نشن
    // ═══════════════════════════════════════════════════════════

    val CRYPTO_KEYS: Set<String> = setOf(
        "crypto_btc", "crypto_eth", "crypto_usdt", "crypto_bnb",
        "crypto_xrp", "crypto_ada", "crypto_doge", "crypto_sol",
        "crypto_trx", "crypto_dot", "crypto_matic", "crypto_shib",
        "crypto_ltc", "crypto_bch", "crypto_xlm", "crypto_xmr"
    )

    // ═══ mapping کلید رمزارز → نماد نوبیتکس ═══
    val CRYPTO_SYMBOLS: Map<String, String> = mapOf(
        "crypto_btc"   to "btc",
        "crypto_eth"   to "eth",
        "crypto_usdt"  to "usdt",
        "crypto_bnb"   to "bnb",
        "crypto_xrp"   to "xrp",
        "crypto_ada"   to "ada",
        "crypto_doge"  to "doge",
        "crypto_sol"   to "sol",
        "crypto_trx"   to "trx",
        "crypto_dot"   to "dot",
        "crypto_matic" to "matic",
        "crypto_shib"  to "shib",
        "crypto_ltc"   to "ltc",
        "crypto_bch"   to "bch",
        "crypto_xlm"   to "xlm",
        "crypto_xmr"   to "xmr"
    )

    // ═══ عنوان فارسی هر رمزارز ═══
    val CRYPTO_TITLES: Map<String, String> = mapOf(
        "crypto_btc"   to "بیت کوین",
        "crypto_eth"   to "اتریوم",
        "crypto_usdt"  to "تتر",
        "crypto_bnb"   to "بایننس کوین",
        "crypto_xrp"   to "ریپل",
        "crypto_ada"   to "کاردانو",
        "crypto_doge"  to "دوج کوین",
        "crypto_sol"   to "سولانا",
        "crypto_trx"   to "ترون",
        "crypto_dot"   to "پولکادات",
        "crypto_matic" to "پالیگان",
        "crypto_shib"  to "شیبا اینو",
        "crypto_ltc"   to "لایت کوین",
        "crypto_bch"   to "بیت کوین کش",
        "crypto_xlm"   to "استلار",
        "crypto_xmr"   to "مونرو"
    )

    // ═══════════════════════════════════════════════════════════
    //  ترتیب کامل لیست
    //  ۱) طلا
    //  ۲) سکه‌ها: امامی → بهار → نیم → ربع → گرمی → قدیم‌ها
    //  ۳) ارزها
    //  ۴) رمزارزها
    // ═══════════════════════════════════════════════════════════

    val ALL_ORDERED: List<String> = listOf(
        // ─── ۱. طلا ───
        "geram18",
        "geram24",
        "mesghal",
        "gold_ounce",
        "silver_ounce",

        // ─── ۲. سکه‌ها ───
        "sekee",       // سکه امامی
        "sekeb",       // سکه بهار آزادی
        "nim",         // نیم سکه
        "rob",         // ربع سکه
        "gerami",      // سکه گرمی
        "137142",      // تمام سکه (قبل ۸۶)
        "137143",      // نیم سکه (قبل ۸۶)
        "137144",      // ربع سکه (قبل ۸۶)

        // ─── ۳. ارزها ───
        "price_dollar_rl",
        "price_eur",
        "price_gbp",
        "price_aed",
        "price_try",
        "price_iqd",
        "price_rub",
        "price_kwd",

        // ─── ۴. رمزارزها ───
        "crypto_btc",
        "crypto_usdt",
        "crypto_eth",
        "crypto_bnb",
        "crypto_xrp",
        "crypto_sol",
        "crypto_ada",
        "crypto_doge",
        "crypto_trx",
        "crypto_dot",
        "crypto_matic",
        "crypto_shib",
        "crypto_ltc",
        "crypto_bch",
        "crypto_xlm",
        "crypto_xmr"
    )

    // ═══ تاپ‌لیست پیش‌فرض: دلار → تتر → یورو → سکه‌ها → بیت‌کوین ═══
    val DEFAULT_SELECTED: List<String> = listOf(
        "price_dollar_rl",  // دلار
        "crypto_usdt",      // تتر
        "price_eur",        // یورو
        "sekee",            // سکه امامی
        "sekeb",            // سکه بهار آزادی
        "nim",              // نیم سکه
        "rob",              // ربع سکه
        "crypto_btc"        // بیت کوین
    )

    // ═══ ترتیب تاپ‌لیست ═══
    fun sortForTopList(keys: List<String>): List<String> {
        return keys.sortedBy { key ->
            val index = DEFAULT_SELECTED.indexOf(key)
            if (index >= 0) index else Int.MAX_VALUE
        }
    }

    // ═══ ترتیب لیست کلی ═══
    fun sortForAllList(keys: List<String>): List<String> {
        return keys.sortedBy { key ->
            val index = ALL_ORDERED.indexOf(key)
            if (index >= 0) index else Int.MAX_VALUE
        }
    }

    // ═══ عنوان فارسی پیش‌فرض ═══
    val DEFAULT_TITLES: Map<String, String> = mapOf(
        // طلا
        "geram18" to "طلای ۱۸ عیار",
        "geram24" to "طلای ۲۴ عیار",
        "mesghal" to "مثقال طلا",
        "gold_ounce" to "انس طلا",
        "silver_ounce" to "انس نقره",
        // سکه
        "sekee" to "سکه امامی",
        "sekeb" to "سکه بهار آزادی",
        "nim" to "نیم سکه",
        "rob" to "ربع سکه",
        "gerami" to "سکه گرمی",
        "137142" to "تمام سکه (قبل ۸۶)",
        "137143" to "نیم سکه (قبل ۸۶)",
        "137144" to "ربع سکه (قبل ۸۶)",
        // ارز
        "price_dollar_rl" to "دلار آمریکا",
        "price_eur" to "یورو",
        "price_gbp" to "پوند",
        "price_aed" to "درهم امارات",
        "price_try" to "لیر ترکیه",
        "price_iqd" to "دینار عراق",
        "price_rub" to "روبل روسیه",
        "price_kwd" to "دینار کویت"
    )
}
