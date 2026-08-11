package com.ev.android.feature.hud

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ev.android.ui.theme.EvGreen
import com.ev.android.ui.theme.EvOutline

/**
 * Home screen ke **andar** khulne wala panel.
 *
 * Pehle Settings poori screen ka dialog thi. Reference design me aisa nahi
 * hai \u2014 wahan upar app ka header aur tabs dikhte rehte hain, neeche command
 * box rehta hai, aur beech ka hissa hi badalta hai. Isse app ek hi jagah
 * lagti hai, do alag screen nahi.
 */
@Composable
fun EvPanel(
    title: String,
    onClose: () -> Unit,
    onSave: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title.uppercase(),
                color = EvGreen,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )

            if (onSave != null) {
                EvDialogButton(label = "SAVE", onClick = onSave)
            }
            EvDialogButton(label = "CLOSE", onClick = onClose)
        }

        HorizontalDivider(color = EvOutline)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp),
            content = content,
        )
    }
}
