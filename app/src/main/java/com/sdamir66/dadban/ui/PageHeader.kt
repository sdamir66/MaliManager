package com.sdamir66.dadban.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sdamir66.dadban.ui.theme.HeaderBlue

@Composable
fun PageHeader(
    title: String,
    subtitle: String,
    onBackClick: (() -> Unit)? = null,
    extraActions: (@Composable () -> Unit)? = null
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                color = HeaderBlue,
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
            )
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 36.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBackClick != null) {
                IconButton(onClick = onBackClick, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.ArrowBack, "بازگشت", tint = Color.White, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(4.dp))
            }

            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                }
            }

            if (extraActions != null) extraActions()
        }
    }
}
