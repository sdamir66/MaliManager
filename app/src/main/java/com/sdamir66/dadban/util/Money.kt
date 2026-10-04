package com.sdamir66.dadban.util

import androidx.compose.ui.graphics.Color
import com.sdamir66.dadban.ui.theme.CreditGreen
import com.sdamir66.dadban.ui.theme.DebitRed
import java.text.NumberFormat
import java.util.Locale

/**
 * فرمت‌کردن عدد به صورت پول
 */
fun money(v: Double): String {
    val formatter = NumberFormat.getNumberInstance(Locale.US)
    formatter.minimumFractionDigits = 0
    formatter.maximumFractionDigits = 2
    return formatter.format(v)
}

fun money(v: Long): String = NumberFormat.getNumberInstance(Locale.US).format(v)

/**
 * نمایش مانده با رنگ و برچسب
 * - صفر: متن "0" با رنگ مشکی، بدون برچسب
 * - مثبت: متن بدون علامت، رنگ سبز، برچسب "بستانکار"
 * - منفی: متن با علامت "−"، رنگ قرمز، برچسب "بدهکار"
 */
data class BalanceDisplay(
    val text: String,
    val label: String,
    val color: Color
)

fun balanceDisplay(balance: Double): BalanceDisplay {
    // نرمال‌سازی -0.0 به 0.0
    val bal = if (balance == 0.0 || balance == -0.0) 0.0 else balance

    return when {
        bal > 0.0 -> BalanceDisplay(
            text = money(bal),
            label = "بستانکار",
            color = CreditGreen
        )
        bal < 0.0 -> BalanceDisplay(
            text = "−${money(-bal)}",
            label = "بدهکار",
            color = DebitRed
        )
        else -> BalanceDisplay(
            text = "0",
            label = "",
            color = Color(0xFF1B1B1F)
        )
    }
}

/**
 * نمایش مانده با در نظر گرفتن ماهیت حساب
 * @param nature "credit" (بستانکار) یا "debit" (بدهکار)
 *
 * برای حساب‌های credit: مانده = جمع(بستانکار) - جمع(بدهکار)
 * برای حساب‌های debit: مانده = جمع(بدهکار) - جمع(بستانکار)
 *
 * تابع balanceDisplay بر اساس عدد نهایی تصمیم می‌گیره.
 */
fun computeBalance(
    creditSum: Double,
    debitSum: Double,
    nature: String
): Double {
    return if (nature == "debit") {
        debitSum - creditSum
    } else {
        creditSum - debitSum
    }
}
