package com.sdamir66.dadban.tools.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.sdamir66.dadban.ui.theme.HeaderBlue
import com.sdamir66.dadban.util.money
import kotlin.math.pow

@Composable
fun LoanCalculator() {
    var principal by remember { mutableStateOf("") }
    var annualRate by remember { mutableStateOf("") }
    var months by remember { mutableStateOf("") }
    val p = principal.replace(",", "").toDoubleOrNull() ?: 0.0
    val r = (annualRate.toDoubleOrNull() ?: 0.0) / 100.0 / 12.0
    val n = months.toIntOrNull() ?: 0
    val payment = when {
        p <= 0 || n <= 0 -> 0.0
        r == 0.0 -> p / n
        else -> p * r * (1 + r).pow(n) / ((1 + r).pow(n) - 1)
    }
    val total = payment * n

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("محاسبه‌گر وام", style = MaterialTheme.typography.titleMedium, color = HeaderBlue)
            Text("قسط ماهانه بر اساس نرخ سالانه و بازپرداخت مساوی", style = MaterialTheme.typography.bodySmall, color = Color(0xFF5C5D72))
            OutlinedTextField(principal, { principal = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("اصل وام") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(annualRate, { annualRate = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("نرخ سالانه (%)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(months, { months = it.filter(Char::isDigit) }, label = { Text("تعداد اقساط (ماه)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true)
            if (payment > 0) {
                HorizontalDivider()
                Text("قسط ماهانه: ${money(payment)}", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = HeaderBlue)
                Text("مجموع بازپرداخت: ${money(total)}", color = Color(0xFF1B1B1F))
                Text("مجموع سود: ${money(total - p)}", color = Color(0xFF5C5D72))
            }
        }
    }
}
