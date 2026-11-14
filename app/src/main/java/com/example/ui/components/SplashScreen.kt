package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Animated Logo Splash Screen with "Hello, Welcome!" greeting and smooth brand reveal.
 * Displays glowing pulsing logo, animated tagline, and progress indicator.
 * Automatically transitions to the main app dashboard after 5.0s or immediately on tap.
 */
@Composable
fun SplashLogoScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var animationStarted by remember { mutableStateOf(false) }

    // Pulsing animation for outer radar waves
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_splash")
    val pulseWave by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseWave"
    )

    // Smooth logo scale in
    val logoScale by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0.5f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "logoScale"
    )

    val logoAlpha by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0f,
        animationSpec = tween(600, easing = LinearEasing),
        label = "logoAlpha"
    )

    // Progress bar animation across the 5-second transition
    var progress by remember { mutableFloatStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(4800, easing = LinearEasing),
        label = "splashProgress"
    )

    LaunchedEffect(Unit) {
        animationStarted = true
        progress = 1f
        delay(5000L)
        onDismiss()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A), // Dark slate
                        Color(0xFF062822), // Deep emerald
                        Color(0xFF021B16)  // Midnight emerald
                    )
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("splash_logo_screen"),
        contentAlignment = Alignment.Center
    ) {
        val isCompactHeight = maxHeight < 480.dp
        val logoContainerSize = if (isCompactHeight) 120.dp else 170.dp

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            // Animated Welcome Pill Badge
            AnimatedVisibility(
                visible = animationStarted,
                enter = fadeIn(tween(500, delayMillis = 150)) + slideInVertically(
                    initialOffsetY = { -40 },
                    animationSpec = tween(600, easing = FastOutSlowInEasing)
                )
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.18f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.5f)),
                    modifier = Modifier
                        .padding(bottom = if (isCompactHeight) 16.dp else 28.dp)
                        .testTag("splash_welcome_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "✨",
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Hello, Welcome!",
                            color = Color(0xFFA7F3D0),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            // Animated TaskPulse Logo Canvas with dynamic pulse rings
            Box(
                modifier = Modifier
                    .size(logoContainerSize)
                    .scale(logoScale)
                    .alpha(logoAlpha),
                contentAlignment = Alignment.Center
            ) {
                // Outer Pulse Waves Canvas
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val baseRadius = size.width * 0.32f

                    // Ring 1
                    val ring1Radius = baseRadius + (pulseWave * 42.dp.toPx())
                    val ring1Alpha = (1f - pulseWave).coerceIn(0f, 0.7f)
                    drawCircle(
                        color = Color(0xFF10B981).copy(alpha = ring1Alpha),
                        radius = ring1Radius,
                        center = center,
                        style = Stroke(width = 2.5.dp.toPx())
                    )

                    // Ring 2 (offset phase)
                    val wave2 = (pulseWave + 0.5f) % 1f
                    val ring2Radius = baseRadius + (wave2 * 42.dp.toPx())
                    val ring2Alpha = (1f - wave2).coerceIn(0f, 0.5f)
                    drawCircle(
                        color = Color(0xFF38BDF8).copy(alpha = ring2Alpha),
                        radius = ring2Radius,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                // Core Shield Icon Container
                Surface(
                    shape = RoundedCornerShape(34.dp),
                    color = Color.Transparent,
                    shadowElevation = 12.dp,
                    modifier = Modifier.size(108.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF10B981), // Emerald
                                        Color(0xFF0EA5E9), // Sky Blue
                                        Color(0xFF059669)  // Deep Emerald
                                    ),
                                    start = Offset(0f, 0f),
                                    end = Offset(200f, 200f)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // Center Pulse & Checkmark Canvas
                        Canvas(modifier = Modifier.size(64.dp)) {
                            val w = size.width
                            val h = size.height

                            // Draw Checkmark
                            val checkPath = Path().apply {
                                moveTo(w * 0.24f, h * 0.52f)
                                lineTo(w * 0.44f, h * 0.72f)
                                lineTo(w * 0.78f, h * 0.32f)
                            }
                            drawPath(
                                path = checkPath,
                                color = Color.White,
                                style = Stroke(
                                    width = 5.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // App Title & Tagline with Animated Fade-In
            AnimatedVisibility(
                visible = animationStarted,
                enter = fadeIn(tween(600, delayMillis = 300)) + scaleIn(
                    initialScale = 0.85f,
                    animationSpec = tween(600, easing = FastOutSlowInEasing)
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "TaskPulse",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 36.sp,
                            letterSpacing = 1.sp
                        ),
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Tasks • Plans • Finance • Alarms",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Loading Progress Bar
            AnimatedVisibility(
                visible = animationStarted,
                enter = fadeIn(tween(400, delayMillis = 400))
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(180.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFF10B981),
                        trackColor = Color(0xFF1E293B),
                        strokeCap = StrokeCap.Round
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Tap anywhere to skip",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
