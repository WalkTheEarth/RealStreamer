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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irlstream.app.R
import com.irlstream.app.ui.AppViewModel
import com.irlstream.app.ui.components.AppBackground
import com.irlstream.app.ui.components.LiveBadge
import com.irlstream.app.ui.components.MicroLabel
import com.irlstream.app.ui.components.PrimaryButton
import com.irlstream.app.ui.components.SecondaryButton
import com.irlstream.app.ui.theme.IRL
import com.irlstream.app.ui.theme.MicroStyle

@Composable
fun WelcomeScreen(vm: AppViewModel) {
    AppBackground {
        Box(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(Modifier.fillMaxWidth().widthIn(max = 560.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(IRL.NeonFaint)
                            .border(1.dp, IRL.Neon.copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Icon(Icons.Rounded.Mic, null, tint = IRL.Neon, modifier = Modifier.size(23.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "REALSTREAMER",
                            style = MaterialTheme.typography.titleMedium.copy(
                                letterSpacing = 2.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            ),
                            color = IRL.Text,
                        )
                        MicroLabel("Broadcast studio", IRL.TextFaint)
                    }
                }
                Spacer(Modifier.height(30.dp))
                LiveBadge(live = false, durationMs = 0, idleLabel = "Android · UVC · RTMP", modifier = Modifier)
                Spacer(Modifier.height(18.dp))
                Text(
                    "GO LIVE.\nFROM ANYWHERE.",
                    style = MaterialTheme.typography.displayLarge,
                    color = IRL.Text,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "A real broadcast studio in your pocket. Composite overlays on the GPU, plug in a UVC camera, and publish one encode to every destination at once.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = IRL.TextSecondary,
                )
                Spacer(Modifier.height(28.dp))
                FeatureRow(
                    icon = Icons.Rounded.Podcasts,
                    title = "Multi-output by design",
                    body = "Encode once and publish to Twitch, YouTube, Kick, and custom RTMP/RTMPS independently.",
                )
                Spacer(Modifier.height(16.dp))
                FeatureRow(
                    icon = Icons.Rounded.Usb,
                    title = "Bring a real camera",
                    body = "Use the built-in lens or any generic UVC webcam — action cameras in webcam mode included.",
                )
                Spacer(Modifier.height(16.dp))
                FeatureRow(
                    icon = Icons.Rounded.Layers,
                    title = "See exactly what you send",
                    body = "Text, chat, stats, web widgets, and images composited as a WYSIWYG scene.",
                )
                Spacer(Modifier.height(16.dp))
                FeatureRow(
                    icon = Icons.Rounded.Visibility,
                    title = "A console only you see",
                    body = "Private chat, bitrate, dropped frames, and output health in the streamer sidebar.",
                )
                Spacer(Modifier.height(32.dp))
                PrimaryButton(
                    text = "Set up outputs",
                    icon = Icons.Rounded.Podcasts,
                    onClick = { vm.completeOnboarding(openDestinations = true) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(9.dp))
                SecondaryButton(
                    text = "Enter studio",
                    onClick = { vm.completeOnboarding(openDestinations = false) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Android first. The engine boundary and iOS port notes ship with the project.",
                    style = MicroStyle.copy(fontSize = 9.sp, letterSpacing = 0.8.sp),
                    color = IRL.TextFaint,
                )
            }
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(IRL.Panel)
                .border(1.dp, IRL.Line, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.TopCenter,
        ) {
            Icon(icon, null, tint = IRL.Neon, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = IRL.Text)
            Spacer(Modifier.height(2.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = IRL.TextMuted)
        }
    }
}
