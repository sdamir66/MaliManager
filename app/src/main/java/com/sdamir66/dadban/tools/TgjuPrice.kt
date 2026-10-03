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
    //  رمزارزها — کاملاً جدا از tgju، از نوبیتکس
    // ═══════════════════════════════════════════════════════════

    val CRYPTO_KEYS: Set<String> = setOf(
        "crypto_btc", "crypto_eth", "crypto_usdt", "crypto_bnb",
        "crypto_xrp", "crypto_ada", "crypto_doge", "crypto_sol",
        "crypto_trx", "crypto_dot", "crypto_matic", "crypto_shib",
        "crypto_ltc", "crypto_bch", "crypto_xlm", "crypto_xmr"
    )

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
    //  ترتیب کامل لیست:
    //  ۱) طلا
    //  ۲) سکه‌ها: امامی → بهار → نیم → ربع → گرمی → قدیم‌ها
    //  ۳) ارزها
    //  ۴) رمزارزها
    // ═══════════════════════════════════════════════════════════

    val ALL_ORDERED: List<String> = listOf(
        // ─── ۱. طلا ───
        "137119",   // انس طلا
        "137123",   // انس نقره
        "137121",   // طلای ۱۸ عیار
        "137122",   // طلای ۲۴ عیار

        // ─── ۲. سکه‌ها ───
        "137138",   // سکه امامی
        "137137",   // سکه بهار آزادی
        "137139",   // نیم سکه
        "137140",   // ربع سکه
        "137141",   // سکه گرمی
        "137142",   // تمام سکه (قبل ۸۶)
        "137143",   // نیم سکه (قبل ۸۶)
        "137144",   // ربع سکه (قبل ۸۶)

        // ─── ۳. ارزها ───
        "137203",   // دلار
        "137205",   // یورو
        "137206",   // درهم امارات
        "137222",   // یوان چین

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
        "137203",       // دلار
        "crypto_usdt",  // تتر
        "137205",       // یورو
        "137138",       // سکه امامی
        "137137",       // سکه بهار آزادی
        "137139",       // نیم سکه
        "137140",       // ربع سکه
        "crypto_btc"    // بیت کوین
    )

    fun sortForTopList(keys: List<String>): List<String> {
        return keys.sortedBy { key ->
            val index = DEFAULT_SELECTED.indexOf(key)
            if (index >= 0) index else Int.MAX_VALUE
        }
    }

    fun sortForAllList(keys: List<String>): List<String> {
        return keys.sortedBy { key ->
            val index = ALL_ORDERED.indexOf(key)
            if (index >= 0) index else Int.MAX_VALUE
        }
    }

    // ═══ عنوان فارسی پیش‌فرض (قبل از لود از API) ═══
    val DEFAULT_TITLES: Map<String, String> = mapOf(
        // طلا
        "137119" to "انس طلا",
        "137123" to "انس نقره",
        "137121" to "طلای ۱۸ عیار",
        "137122" to "طلای ۲۴ عیار",
        // سکه
        "137138" to "سکه امامی",
        "137137" to "سکه بهار آزادی",
        "137139" to "نیم سکه",
        "137140" to "ربع سکه",
        "137141" to "سکه گرمی",
        "137142" to "تمام سکه (قبل ۸۶)",
        "137143" to "نیم سکه (قبل ۸۶)",
        "137144" to "ربع سکه (قبل ۸۶)",
        // ارز
        "137203" to "دلار آمریکا",
        "137205" to "یورو",
        "137206" to "درهم امارات",
        "137222" to "یوان چین"
    )
}
