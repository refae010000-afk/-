package com.example.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.AccessibleForward
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.GlobalStrings
import com.example.model.Scene3DType
import com.example.ui.PilgrimUiState
import com.example.ui.PilgrimViewModel
import com.example.ui.components.PilgrimCard
import com.example.ui.components.PilgrimOutlineButton
import com.example.ui.components.SectionHeader
import com.example.ui.theme.SacredGold
import com.example.ui.theme.SacredGoldBright
import com.example.ui.theme.StatusSuccess
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun MapAndQiblaScreen(
    state: PilgrimUiState,
    viewModel: PilgrimViewModel,
    modifier: Modifier = Modifier
) {
    val strings = GlobalStrings.forLang(state.language)
    val qiblaBearing = state.qiblaBearingDeg
    val relAngle = if (qiblaBearing != null) {
        ((qiblaBearing - state.deviceAzimuthDeg + 360f) % 360f)
    } else 0f
    val animatedNeedleAngle by animateFloatAsState(
        targetValue = relAngle,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "compass_needle"
    )
    val isAligned = state.qiblaActive && (relAngle <= 6f || relAngle >= 354f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 600.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Photographic Holy Sites Visual Map with Mashaaer Sequence
            PilgrimCard {
                SectionHeader(
                    title = strings.mapTitle,
                    icon = Icons.Outlined.Map,
                    subtitle = strings.trainLabel
                )
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MapPhotoCard(
                            stepNum = "1",
                            imageRes = R.drawable.img_kaaba_haram,
                            label = strings.siteHaram,
                            modifier = Modifier.weight(1f)
                        ) {
                            viewModel.jumpTo3D(Scene3DType.KAABA)
                        }
                        MapPhotoCard(
                            stepNum = "2",
                            imageRes = R.drawable.img_mina_jamarat,
                            label = strings.siteMina,
                            modifier = Modifier.weight(1f)
                        ) {
                            viewModel.jumpTo3D(Scene3DType.JAMARAT)
                        }
                    }

                    // Railway Route Bar
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    ) {
                        Text(
                            text = strings.trainLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MapPhotoCard(
                            stepNum = "3",
                            imageRes = R.drawable.img_safa_marwa,
                            label = strings.siteMuzdalifah,
                            modifier = Modifier.weight(1f)
                        ) {
                            viewModel.showToast(strings.siteMuzdalifah)
                        }
                        MapPhotoCard(
                            stepNum = "4",
                            imageRes = R.drawable.img_arafat_mount,
                            label = strings.siteArafat,
                            modifier = Modifier.weight(1f)
                        ) {
                            viewModel.jumpTo3D(Scene3DType.ARAFAT)
                        }
                    }
                }
            }

            // 2. Live Qibla Compass & Distance (Elevated Focal Card)
            PilgrimCard(accentStartBar = true) {
                SectionHeader(
                    title = strings.qiblaTitle,
                    icon = Icons.Outlined.Explore
                )
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val dialBorder = if (isAligned) StatusSuccess else MaterialTheme.colorScheme.outline
                    val dialBg = if (isAligned) StatusSuccess.copy(alpha = 0.12f) else MaterialTheme.colorScheme.background
                    val needleColor = if (isAligned) StatusSuccess else SacredGold
                    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)

                    Box(
                        modifier = Modifier
                            .size(154.dp)
                            .clip(CircleShape)
                            .background(dialBg)
                            .border(4.dp, dialBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🕋",
                            fontSize = 15.sp,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp)
                        )
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val rOuter = size.minDimension / 2f - 10.dp.toPx()
                            val rInner = rOuter - 7.dp.toPx()
                            for (deg in 0 until 360 step 30) {
                                val rad = Math.toRadians(deg.toDouble())
                                val start = Offset(
                                    center.x + (rInner * cos(rad)).toFloat(),
                                    center.y + (rInner * sin(rad)).toFloat()
                                )
                                val end = Offset(
                                    center.x + (rOuter * cos(rad)).toFloat(),
                                    center.y + (rOuter * sin(rad)).toFloat()
                                )
                                drawLine(
                                    color = tickColor,
                                    start = start,
                                    end = end,
                                    strokeWidth = if (deg % 90 == 0) 2.5.dp.toPx() else 1.2.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }

                            rotate(degrees = animatedNeedleAngle, pivot = center) {
                                val needle = Path().apply {
                                    moveTo(center.x, center.y - 46.dp.toPx())
                                    lineTo(center.x - 10.dp.toPx(), center.y + 14.dp.toPx())
                                    lineTo(center.x + 10.dp.toPx(), center.y + 14.dp.toPx())
                                    close()
                                }
                                drawPath(needle, color = needleColor)
                                drawCircle(Color.White, radius = 4.dp.toPx(), center = Offset(center.x, center.y))
                            }
                        }
                    }

                    val statusText = when {
                        isAligned -> strings.qiblaAligned
                        qiblaBearing != null -> "${strings.qiblaBearingPrefix} ${qiblaBearing.roundToInt()}°"
                        else -> strings.qiblaPrompt
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isAligned) StatusSuccess.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isAligned) StatusSuccess else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }

                    val distText = state.qiblaDistanceMeters?.let { d ->
                        if (d >= 1000.0) "${String.format(Locale.US, "%.1f", d / 1000.0)} ${strings.kmUnit} ${strings.qiblaToKaabaSuffix}"
                        else "${d.roundToInt()} m ${strings.qiblaToKaabaSuffix}"
                    } ?: strings.qiblaDistPlaceholder

                    Text(
                        text = distText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    PilgrimOutlineButton(
                        text = strings.activateQiblaBtn,
                        onClick = { viewModel.activateQiblaCompass() },
                        modifier = Modifier.fillMaxWidth(),
                        tag = "activate_qibla_btn"
                    )
                }
            }

            // 3. Transport & Haram Services Guide
            PilgrimCard {
                SectionHeader(
                    title = strings.transportTitle,
                    icon = Icons.Outlined.Train
                )
                Spacer(modifier = Modifier.height(10.dp))
                TransportInfoRow(Icons.Outlined.Train, strings.transportTrain)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                TransportInfoRow(Icons.AutoMirrored.Outlined.AccessibleForward, strings.transportCarts)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                TransportInfoRow(Icons.Outlined.ContentCut, strings.transportBarber)
            }
        }
    }
}

@Composable
private fun MapPhotoCard(
    stepNum: String,
    imageRes: Int,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(98.dp)
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, SacredGold.copy(alpha = 0.55f)),
        shadowElevation = 3.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.2f), Color.Black.copy(alpha = 0.80f))
                        )
                    )
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
                shape = CircleShape,
                color = SacredGoldBright
            ) {
                Text(
                    text = stepNum,
                    color = Color.Black,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun TransportInfoRow(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            modifier = Modifier.size(34.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}
