package com.backtosafety.app.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.backtosafety.app.ui.LocalAppColors
import com.backtosafety.app.ui.PrimaryButton
import com.backtosafety.app.ui.rnTextStyle
import com.backtosafety.app.ui.style
import com.backtosafety.core.DesignTokens
import com.backtosafety.core.Translate
import com.backtosafety.core.data.Store
import com.backtosafety.core.invoke
import kotlinx.coroutines.launch

/** Port of app/onboarding/complete.tsx. */
@Composable
fun CompleteScreen(t: Translate, store: Store, onFinish: () -> Unit) {
    val colors = LocalAppColors.current
    val type = DesignTokens.Typography
    val space = DesignTokens.Spacing
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(start = space.xl.dp, end = space.xl.dp, top = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.padding(bottom = space.xxl.dp).size(100.dp).clip(CircleShape).background(colors.success),
                contentAlignment = Alignment.Center,
            ) {
                Text(t("complete.icon"), style = rnTextStyle(48f, 24f), color = Color(DesignTokens.Light.textOnPrimary))
            }
            Text(t("complete.title"), style = type.display.style(), color = colors.text, textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = space.lg.dp))
            Text(t("complete.description"), style = type.body.style(), color = colors.textSecondary, textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = space.xxl.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(space.sm.dp)) {
                Text(t("complete.addMoreLater"), style = type.title.style(), color = colors.text,
                    modifier = Modifier.padding(bottom = space.xs.dp))
                for (key in listOf("details", "deescalation", "destinations", "contacts", "checklist")) {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        Box(Modifier.width(3.dp).fillMaxSize().clip(RoundedCornerShape(2.dp)).background(colors.border))
                        Text(t("complete.nextSteps.$key"), style = type.bodyBold.style(), color = colors.textSecondary,
                            modifier = Modifier.padding(vertical = space.sm.dp, horizontal = space.md.dp))
                    }
                }
            }
        }
        Box(Modifier.padding(start = space.xl.dp, end = space.xl.dp, top = space.xl.dp, bottom = space.xxl.dp)) {
            PrimaryButton(
                t("complete.goHome"), "onboarding-complete-home",
                textStyle = rnTextStyle(18f, 24f, fontWeight = 600),
                verticalPadding = space.lg,
                onClick = { scope.launch { store.completeStep("complete"); onFinish() } },
            )
        }
    }
}
