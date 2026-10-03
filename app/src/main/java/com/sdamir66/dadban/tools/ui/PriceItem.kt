package com.sdamir66.dadban.tools.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sdamir66.dadban.tools.TgjuPrice
import com.sdamir66.dadban.ui.theme.CreditGreen
import com.sdamir66.dadban.ui.theme.DebitRed
import com.sdamir66.dadban.ui.theme.HeaderBlue
import java.text.NumberFormat
import java.util.Locale

@Composable
fun PriceItem(
    price: TgjuPrice,
    modifier: Modifier = Modifier
) {
    val changeColor = when (price.direction) {
        "high" -> CreditGreen
        "low" -> DebitRed
        else -> Color(0xFF5C5D72)
    }

    Column(
        modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // ═══ خط ۱: عنوان + قیمت + تغییر ═══
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                price.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B1B1F),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(Modifier.width(6.dp))

            Text(
                formatNumber(price.price),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B1B1F),
                maxLines = 1
            )

            Spacer(Modifier.width(6.dp))

            Text(
                buildChangeText(price),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = changeColor,
                maxLines = 1
            )
        }

        // ═══ خط ۲: کمترین + بیشترین + زمان ═══
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "کم: ${formatNumber(price.low)}",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF5C5D72),
                maxLines = 1,
                fontSize = 10.sp
            )

            Spacer(Modifier.width(8.dp))

            Text(
                "بیش: ${formatNumber(price.high)}",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF5C5D72),
                maxLines = 1,
                fontSize = 10.sp
            )

            Spacer(Modifier.weight(1f))

            if (price.time.isNotBlank()) {
                Text(
                    formatTime(price.time),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF5C5D72),
                    maxLines = 1,
                    fontSize = 9.sp
                )
            }
        }
    }
}

private fun formatNumber(value: Double): String {
    val formatter = NumberFormat.getNumberInstance(Locale.US)
    formatter.minimumFractionDigits = 0
    formatter.maximumFractionDigits = 0
    return formatter.format(value)
}

private fun buildChangeText(price: TgjuPrice): String {
    val arrow = when (price.direction) {
        "high" -> "▲"
        "low" -> "▼"
        else -> ""
    }
    val percent = String.format(Locale.US, "%.2f", price.changePercent)
    return "$arrow$percent%"
}

private fun formatTime(rawTime: String): String {
    if (rawTime.isBlank()) return ""

    return try {
        if (rawTime.contains("-") && rawTime.contains(":")) {
            val dateTime = rawTime.split(" ")
            if (dateTime.size >= 2) {
                val datePart = dateTime[0]
                val timePart = dateTime[1]

                val parts = datePart.split("-")
                if (parts.size == 3) {
                    val gy = parts[0].toInt()
                    val gm = parts[1].toInt()
                    val gd = parts[2].toInt()

                    val j = com.sdamir66.dadban.util.Jalali.gregorianToJalaliDirect(gy, gm, gd)
                    val jy = j[0]
                    val jm = j[1].toString().padStart(2, '0')
                    val jd = j[2].toString().padStart(2, '0')

                    val hm = timePart.substring(0, 5)

                    return "$jy/$jm/$jd $hm"
                }
            }
        }
        rawText(rawTime)
    } catch (e: Exception) {
        rawText(rawTime)
    }
}

// اگه فرمت میلادی نبود، خودش رو برگردون
private fun rawText(s: String) = s
