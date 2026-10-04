package com.sdamir66.dadban.treasury

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sdamir66.dadban.ui.theme.DebitRed
import com.sdamir66.dadban.ui.theme.HeaderBlue
import kotlinx.coroutines.delay

/**
 * دیالوگ ورود رمز دارایی‌های من
 * - بدون دکمه تأیید/انصراف
 * - به محض تطابق طول ورودی با رمز ذخیره‌شده → خودکار چک می‌شه
 * - اگه درست بود → onCorrect()
 * - اگه غلط بود → پیام خطا + فیلد پاک می‌شه
 */
@Composable
fun PasswordDialog(
    correctPassword: String,
    onDismiss: () -> Unit,
    onCorrect: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    // ═══ چک خودکار به محض تطابق طول ═══
    LaunchedEffect(input) {
        if (input.length == correctPassword.length && input.isNotEmpty()) {
            if (input == correctPassword) {
                onCorrect()
            } else {
                error = true
                // یه مکث کوتاه تا کاربر پیام خطا رو ببینه، بعد پاک کن
                delay(600L)
                input = ""
                error = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.border(2.dp, HeaderBlue, RoundedCornerShape(20.dp)),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .background(HeaderBlue, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🔐", fontSize = 20.sp)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "ورود به دارایی‌های من",
                    fontWeight = FontWeight.Bold,
                    color = HeaderBlue,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { newValue ->
                        // اجازه‌ی هر کاراکتری، فقط از طول رمز ذخیره‌شده بیشتر نشه
                        if (newValue.length <= correctPassword.length) {
                            error = false
                            input = newValue
                        }
                    },
                    label = { Text("رمز را وارد کنید", color = Color(0xFF5C5D72)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = error,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF1B1B1F),
                        unfocusedTextColor = Color(0xFF1B1B1F),
                        focusedBorderColor = HeaderBlue,
                        unfocusedBorderColor = if (error) DebitRed else Color(0xFFCCCCCC),
                        focusedLabelColor = HeaderBlue,
                        unfocusedLabelColor = Color(0xFF5C5D72),
                        cursorColor = HeaderBlue
                    )
                )

                if (error) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "❌ رمز اشتباه است",
                        color = DebitRed,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        // ═══ بدون دکمه ═══
        confirmButton = {},
        dismissButton = {}
    )
}
