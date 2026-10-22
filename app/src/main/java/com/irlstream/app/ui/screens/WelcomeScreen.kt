package com.irlstream.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irlstream.app.R
import com.irlstream.app.ui.AppViewModel
import com.irlstream.app.ui.components.AppBackground
import com.irlstream.app.ui.components.DeckPlate
import com.irlstream.app.ui.components.Hairline
import com.irlstream.app.ui.components.MicroLabel
import com.irlstream.app.ui.components.PrimaryButton
import com.irlstream.app.ui.components.SecondaryButton
import com.irlstream.app.ui.theme.IRL
import com.irlstream.app.ui.theme.MicroStyle
import com.irlstream.app.ui.theme.MonoStyle

@Composable
fun WelcomeScreen(vm: AppViewModel) {
    AppBackground {
        Box(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(Modifier.fillMaxWidth().widthIn(max = 560.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val logoShape = CutCornerShape(topEnd = 10.dp)
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(logoShape)
                            .background(IRL.Ink)
                            .border(1.dp, IRL.LineStrong, logoShape),
                    ) {
                        Image(
                            painter = painterResource(R.drawable.realstreamer_badge),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "REALSTREAMER",
                            style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 1.9.sp),
                            color = IRL.Text,
                        )
                        MicroLabel("Broadcast deck", IRL.TextFaint)
                    }
                    DeckPlate("Android", color = IRL.Signal)
                }

                Spacer(Modifier.height(38.dp))
                Row {
                    DeckPlate("UVC", color = IRL.TextMuted)
                    Spacer(Modifier.width(5.dp))
                    DeckPlate("Multi-RTMP", color = IRL.TextMuted)
                    Spacer(Modifier.width(5.dp))
                    DeckPlate("WYSIWYG", color = IRL.TextMuted)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "GO LIVE.\nFROM ANYWHERE.",
                    style = MaterialTheme.typography.displayMedium,
                    color = IRL.Text,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "A real broadcast studio in your pocket. Composite overlays on the GPU, plug in a UVC camera, and publish one encode to every destination at once.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = IRL.TextSecondary,
                    modifier = Modifier.widthIn(max = 480.dp),
                )

                Spacer(Modifier.height(30.dp))
                FeatureRow(
                    index = "01",
                    title = "One encode. Every output.",
                    body = "Publish to Twitch, YouTube, Kick, and custom RTMP/RTMPS independently, with per-destination reconnect.",
                )
                FeatureRow(
                    index = "02",
                    title = "Bring a real camera.",
                    body = "Use the built-in lens or any generic UVC webcam — action cameras in webcam mode included.",
                )
                FeatureRow(
                    index = "03",
                    title = "See exactly what you send.",
                    body = "Text, chat, stats, web widgets, and images composited into a WYSIWYG program frame.",
                )
                FeatureRow(
                    index = "04",
                    title = "Keep the console private.",
                    body = "Streamer-only chat, bitrate, dropped frames, and output health never enter the broadcast.",
                )

                Spacer(Modifier.height(30.dp))
                PrimaryButton(
                    text = "Set up outputs",
                    icon = Icons.Rounded.Podcasts,
                    onClick = { vm.completeOnboarding(openDestinations = true) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                SecondaryButton(
                    text = "Enter studio",
                    onClick = { vm.completeOnboarding(openDestinations = false) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(18.dp))
                Hairline()
                Spacer(Modifier.height(10.dp))
                Text(
                    "ANDROID FIRST · IOS PORT NOTES INCLUDED",
                    style = MicroStyle.copy(fontSize = 8.sp, letterSpacing = 1.3.sp),
                    color = IRL.TextFaint,
                )
            }
        }
    }
}

@Composable
private fun FeatureRow(index: String, title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Hairline()
        Spacer(Modifier.height(12.dp))
        Row {
            Text(
                index,
                style = MonoStyle.copy(fontSize = 10.sp),
                color = IRL.Signal,
                modifier = Modifier.width(30.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = IRL.Text)
                Spacer(Modifier.height(3.dp))
                Text(body, style = MaterialTheme.typography.bodySmall, color = IRL.TextMuted)
            }
        }
    }
}
