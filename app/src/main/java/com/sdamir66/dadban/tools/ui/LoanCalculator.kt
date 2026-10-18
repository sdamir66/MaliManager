package com.sdamir66.dadban.tools.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import com.sdamir66.dadban.ui.theme.HeaderBlue
import com.sdamir66.dadban.ui.theme.DebitRed
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

private val loanText = Color(0xFF1B1B1F)
private val loanLabel = Color(0xFF4B4D61)
private val loanBorder = Color(0xFF9AA3B2)

@Composable
fun LoanCalculator() {
    var principal by remember { mutableStateOf("") }
    var payment by remember { mutableStateOf("") }
    var months by remember { mutableStateOf("") }
    var interval by remember { mutableStateOf("1") }
    var annualRate by remember { mutableStateOf("") }
    var totalRepayment by remember { mutableStateOf("") }
    var totalInterest by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    fun calculate() {
        val p = principal.toNumber()
        val pay = payment.toNumber()
        val n = months.toNumber()?.roundToInt()
        val rate = annualRate.toNumber()
        val total = totalRepayment.toNumber()
        val interest = totalInterest.toNumber()
        if (p == null && total == null) {
            error = "حداقل اصل وام یا مجموع بازپرداخت را وارد کن"
            return
        }
        var solvedP = p
        var solvedPay = pay
        var solvedN = n
        var solvedRate = rate
        var solvedTotal = total

        if (solvedTotal == null && solvedP != null && interest != null) solvedTotal = solvedP + interest
        if (solvedP == null && solvedTotal != null && interest != null) solvedP = solvedTotal - interest
        if (solvedPay == null && solvedTotal != null && solvedN != null && solvedN > 0) solvedPay = solvedTotal / solvedN
        if (solvedN == null && solvedTotal != null && solvedPay != null && solvedPay > 0) solvedN = (solvedTotal / solvedPay).roundToInt().coerceAtLeast(1)

        when {
            solvedP != null && solvedN != null && solvedRate != null -> solvedPay = monthlyPayment(solvedP, solvedN, solvedRate)
            solvedP != null && solvedN != null && solvedPay != null && solvedRate == null -> solvedRate = solveAnnualRate(solvedP, solvedN, solvedPay)
            solvedP != null && solvedPay != null && solvedRate != null && solvedN == null -> solvedN = solveMonths(solvedP, solvedPay, solvedRate)
            solvedPay != null && solvedN != null && solvedRate != null && solvedP == null -> solvedP = loanPrincipal(solvedPay, solvedN, solvedRate)
        }
        if (solvedTotal == null && solvedPay != null && solvedN != null) solvedTotal = solvedPay * solvedN
        if (solvedPay == null && solvedP != null && solvedTotal != null && solvedN != null && solvedN > 0) solvedPay = solvedTotal / solvedN
        if (solvedRate == null && solvedP != null && solvedN != null && solvedPay != null) solvedRate = solveAnnualRate(solvedP, solvedN, solvedPay)
        if (solvedN == null && solvedP != null && solvedPay != null && solvedTotal != null && solvedPay > 0) solvedN = (solvedTotal / solvedPay).roundToInt().coerceAtLeast(1)
        if (solvedTotal == null && solvedP != null && solvedPay != null && solvedN != null) solvedTotal = solvedPay * solvedN

        if (solvedP == null || solvedPay == null || solvedN == null || solvedN <= 0) {
            error = "برای حل این حالت، حداقل سه مقدار سازگار وارد کن"
            return
        }
        error = ""
        principal = solvedP.twoDecimals()
        payment = solvedPay.twoDecimals()
        months = solvedN.toString()
        annualRate = (solvedRate ?: 0.0).twoDecimals()
        totalRepayment = (solvedTotal ?: solvedPay * solvedN).twoDecimals()
        totalInterest = ((solvedTotal ?: solvedPay * solvedN) - solvedP).twoDecimals()
    }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("محاسبه‌گر وام", style = MaterialTheme.typography.titleMedium, color = HeaderBlue, fontWeight = FontWeight.Bold)
            Text("هر چند مقدار را داری وارد کن؛ برنامه بقیه را محاسبه می‌کند. برای حالت‌های دقیق، حداقل سه مقدار مرتبط وارد شود.", style = MaterialTheme.typography.bodySmall, color = loanLabel)
            LoanField("اصل وام", principal, { principal = it })
            LoanField("قسط ماهانه", payment, { payment = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    LoanField("تعداد اقساط", months, { months = it }, KeyboardType.Number)
                }
                Box(Modifier.weight(1f)) {
                    LoanField("فاصله اقساط (ماه)", interval, { interval = it }, KeyboardType.Number, groupThousands = false)
                }
            }
            LoanField("نرخ سالانه (%)", annualRate, { annualRate = it }, groupThousands = false)
            LoanField("مجموع بازپرداخت", totalRepayment, { totalRepayment = it })
            LoanField("مجموع سود", totalInterest, { totalInterest = it })
            Button(onClick = ::calculate, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = HeaderBlue)) {
                Text("محاسبه و تکمیل مقادیر", color = Color.White)
            }
            OutlinedButton(
                onClick = {
                    principal = ""; payment = ""; months = ""; interval = "1"
                    annualRate = ""; totalRepayment = ""; totalInterest = ""; error = ""
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("پاک کردن همه مقادیر", color = HeaderBlue) }
            if (error.isNotBlank()) Text(error, color = Color(0xFFC62828), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LoanField(label: String, value: String, onChange: (String) -> Unit, type: KeyboardType = KeyboardType.Decimal, groupThousands: Boolean = true) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    LaunchedEffect(value) {
        if (fieldValue.text != value) fieldValue = TextFieldValue(value, TextRange(value.length))
    }
    OutlinedTextField(
        value = fieldValue,
        onValueChange = {
            val formatted = formatLoanInputValue(it, groupThousands)
            fieldValue = TextFieldValue(formatted.first, TextRange(formatted.second))
            onChange(formatted.first)
        },
        label = { Text(label, color = loanLabel) },
        keyboardOptions = KeyboardOptions(keyboardType = type),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = loanText,
            unfocusedTextColor = loanText,
            focusedLabelColor = HeaderBlue,
            unfocusedLabelColor = loanLabel,
            focusedBorderColor = HeaderBlue,
            unfocusedBorderColor = loanBorder,
            cursorColor = HeaderBlue
        ),
        trailingIcon = {
            if (fieldValue.text.isNotEmpty()) {
                IconButton(onClick = {
                    fieldValue = TextFieldValue("")
                    onChange("")
                }) { Icon(Icons.Default.Delete, contentDescription = "پاک کردن", tint = DebitRed) }
            }
        }
    )
}

private fun String.toNumber(): Double? = replace(",", "").toDoubleOrNull()?.takeIf { it >= 0.0 }
private fun Double.twoDecimals(): String = String.format(Locale.US, "%.2f", this)

private fun formatLoanInputValue(input: TextFieldValue, groupThousands: Boolean): Pair<String, Int> {
    val rawBeforeCursor = input.text.take(input.selection.start)
    val digitsBeforeCursor = rawBeforeCursor.count { it.isDigit() }
    val normalized = input.text.filter { it.isDigit() || it == '.' || it == ',' }.replace(",", "")
    val parts = normalized.split('.', limit = 2)
    val integer = parts[0].ifEmpty { "0" }
    val formattedInteger = if (groupThousands) {
        NumberFormat.getIntegerInstance(Locale.US).format(integer.toLongOrNull() ?: 0L)
    } else integer
    val result = if (parts.size == 2) "$formattedInteger.${parts[1].filter { it.isDigit() }.take(2)}" else formattedInteger
    if (digitsBeforeCursor >= integer.length) return result to result.length
    var seen = 0
    val cursor = result.indexOfFirst { ch ->
        if (ch.isDigit()) seen++
        seen == digitsBeforeCursor
    }
    return result to if (cursor >= 0) cursor + 1 else result.length
}

private fun monthlyPayment(principal: Double, months: Int, annualRate: Double): Double {
    val monthly = annualRate / 100.0 / 12.0
    if (monthly == 0.0) return principal / months
    val factor = (1 + monthly).pow(months)
    return principal * monthly * factor / (factor - 1)
}

private fun loanPrincipal(payment: Double, months: Int, annualRate: Double): Double {
    val monthly = annualRate / 100.0 / 12.0
    if (monthly == 0.0) return payment * months
    val factor = (1 + monthly).pow(months)
    return payment * (factor - 1) / (monthly * factor)
}

private fun solveAnnualRate(principal: Double, months: Int, payment: Double): Double {
    if (payment <= principal / months) return 0.0
    var low = 0.0
    var high = 10.0
    repeat(80) {
        val mid = (low + high) / 2.0
        if (monthlyPayment(principal, months, mid) < payment) low = mid else high = mid
    }
    return ((low + high) / 2.0) * 100.0
}

private fun solveMonths(principal: Double, payment: Double, annualRate: Double): Int {
    val monthly = annualRate / 100.0 / 12.0
    if (monthly == 0.0) return (principal / payment).roundToInt().coerceAtLeast(1)
    val n = ln(payment / (payment - principal * monthly)) / ln(1 + monthly)
    return n.roundToInt().coerceAtLeast(1)
}
