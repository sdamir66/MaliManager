package com.sdamir66.dadban.util

import androidx.compose.ui.graphics.Color
import com.sdamir66.dadban.ui.theme.CreditGreen
import com.sdamir66.dadban.ui.theme.DebitRed
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

fun money(v: Double): String {
    val formatter = NumberFormat.getNumberInstance(Locale.US)
    formatter.minimumFractionDigits = 0
    formatter.maximumFractionDigits = 2
    return formatter.format(v)
}

fun money(v: Long): String = NumberFormat.getNumberInstance(Locale.US).format(v)

data class BalanceDisplay(
    val text: String,
    val label: String,
    val color: Color
)

fun balanceDisplay(balance: Double): BalanceDisplay {
    // نرمال‌سازی: هر عدد کوچیک‌تر از 0.01 → صفر
    val bal = if (abs(balance) < 0.01) 0.0 else balance

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
