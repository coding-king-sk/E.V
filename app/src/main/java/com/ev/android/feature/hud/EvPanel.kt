package com.ev.android.feature.hud

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ev.android.ui.theme.EvBlack
import com.ev.android.ui.theme.EvGreen
import com.ev.android.ui.theme.EvOutline

/**
 * Poori screen wala page (Settings, Orb design).
 *
 * Pehle ye home screen ke beech me khulta tha, par tab settings ka lamba
 * content ek chhoti si khidki me thusa hua lagta tha. Ab ye poori screen leta
 * hai \u2014 peeche ke header, tabs aur command box chhup jaate hain, aur upar
 * bayen kone me ek back arrow rehta hai jisse wapas home aa jao.
 *
 * Dialog jaan boojh ke nahi use kiya: dialog ke kinaare, chhaya aur apna
 * background app ke flat black look se match nahi karte.
 *
 * Phone ka back button bhi yahi karta hai jo arrow karta hai \u2014 page band,
 * app khuli. Pehle back seedha app se bahar nikal deta tha, jo galat lagta
 * tha: Settings khol ke back dabana sabse aam aadat hai.
 */
@Composable
fun EvPanel(
    title: String,
    onClose: () -> Unit,
    onSave: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Panel khula ho to back sabse pehle yahan aata hai; MainActivity wala
    // callback (jo app ko peeche bhejta hai) tabhi chalta hai jab koi panel
    // khula na ho.
    BackHandler(onBack = onClose)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EvBlack)
            .padding(horizontal = 16.dp),
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "\u2190",
                color = EvGreen,
                fontSize = 22.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onClose)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )

            Text(
                text = title.uppercase(),
                color = EvGreen,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp),
            )

            if (onSave != null) {
                EvDialogButton(label = "SAVE", onClick = onSave)
            }
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
