package com.sdamir66.dadban.tools.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
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
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val isPositive = price.direction == "high"
    val changeColor = when {
        price.direction == "high" -> CreditGreen
        price.direction == "low" -> DebitRed
        else -> Color(0xFF5C5D72)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        // ═══ خط ۱: چک‌باکس + عنوان + قیمت + تغییر ═══
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = HeaderBlue,
                    checkmarkColor = Color.White,
                    uncheckedColor = Color(0xFF5C5D72)
                ),
                modifier = Modifier.size(28.dp)
            )

            Spacer(Modifier.width(4.dp))

            // عنوان
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

            // قیمت
            Text(
                formatNumber(price.price),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B1B1F),
                maxLines = 1
            )

            Spacer(Modifier.width(6.dp))

            // تغییر
            Text(
                buildChangeText(price, isPositive),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = changeColor,
                maxLines = 1
            )
        }

        // ═══ خط ۲: کمترین + بیشترین + زمان ═══
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 32.dp, top = 1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // کمترین
            Text(
                "کم: ${formatNumber(price.low)}",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF5C5D72),
                maxLines = 1
            )

            Spacer(Modifier.width(8.dp))

            // بیشترین
            Text(
                "بیش: ${formatNumber(price.high)}",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF5C5D72),
                maxLines = 1
            )

            Spacer(Modifier.weight(1f))

            // زمان
            if (price.time.isNotBlank()) {
                Text(
                    price.time,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF5C5D72),
                    maxLines = 1,
                    fontSize = 9.sp
                )
            }
        }
    }
}

// ═══ فرمت اعداد ═══
private fun formatNumber(value: Double): String {
    val formatter = NumberFormat.getNumberInstance(Locale.US)
    formatter.minimumFractionDigits = 0
    formatter.maximumFractionDigits = 0
    return formatter.format(value)
}

// ═══ ساخت متن تغییر ═══
private fun buildChangeText(price: TgjuPrice, isPositive: Boolean): String {
    val arrow = when (price.direction) {
        "high" -> "▲"
        "low" -> "▼"
        else -> ""
    }
    val percent = String.format(Locale.US, "%.2f", price.changePercent)
    return if (arrow.isNotEmpty()) {
        "$arrow$percent%"
    } else {
        "${percent}%"
    }
}
