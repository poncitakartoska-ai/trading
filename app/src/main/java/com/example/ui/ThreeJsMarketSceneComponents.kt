package com.example.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.quant.ImoexIndexSnapshot
import com.example.quant.QuantSignal
import com.example.quant.QuantSignalEngine
import com.example.quant.SignalDirection
import com.example.ui.theme.LocalAppPalette
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

@Immutable
enum class ThreeJsSceneMode(val code: String, val labelRu: String) {
    SKYSCRAPER_CITY("CITY", "3D Сити"),
    ORBITAL_CORE("ORBIT", "3D Орбита"),
    VOLATILITY_WAVE("WAVE", "3D Волна")
}

@Immutable
enum class StudioLightPreset(val code: String, val labelRu: String) {
    CYBER_STUDIO("CYBER", "Cyber PBR"),
    EXECUTIVE_GOLD("GOLD", "Gold Studio"),
    EMERALD_MATRIX("MATRIX", "Matrix Rig")
}

private class ThreeJsSceneBridge(
    private val onTickerClickedFrom3D: (String) -> Unit
) {
    @JavascriptInterface
    fun onSelectTicker(ticker: String) {
        onTickerClickedFrom3D(ticker)
    }
}

/**
 * Interactive Three.js 3D Scene Viewer with Studio PBR Rendering, Procedural 3D Financial Assets,
 * Raycast Object Picking, and Zero-Occlusion Edge Controls.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ThreeJsMarketSceneViewer(
    signals: List<QuantSignal>,
    imoexSnapshot: ImoexIndexSnapshot,
    onOpenPaperTrade: (QuantSignal) -> Unit,
    onSelectForRisk: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val haptic = LocalHapticFeedback.current

    var sceneMode by rememberSaveable { mutableStateOf(ThreeJsSceneMode.SKYSCRAPER_CITY) }
    var lightPreset by rememberSaveable { mutableStateOf(StudioLightPreset.CYBER_STUDIO) }
    var autoRotate by rememberSaveable { mutableStateOf(true) }
    var zeroOcclusionMinimalHud by rememberSaveable { mutableStateOf(false) }
    var showPbrSliders by rememberSaveable { mutableStateOf(false) }
    var metalness by rememberSaveable { mutableFloatStateOf(0.78f) }
    var roughness by rememberSaveable { mutableFloatStateOf(0.22f) }
    var selectedTicker by rememberSaveable {
        mutableStateOf(signals.firstOrNull()?.ticker ?: "SBER")
    }

    val selectedSignal = remember(signals, selectedTicker) {
        signals.firstOrNull { it.ticker.equals(selectedTicker, ignoreCase = true) }
            ?: signals.firstOrNull()
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val signalsJsonPayload = remember(signals, imoexSnapshot) {
        buildSignalsJsonForThreeJs(signals, imoexSnapshot)
    }

    val htmlContent = remember {
        buildThreeJsPbrStudioHtml()
    }

    // Push live MOEX telemetry & PBR settings into the Three.js scene without reloading the WebView
    LaunchedEffect(
        webViewRef,
        signalsJsonPayload,
        sceneMode,
        lightPreset,
        autoRotate,
        metalness,
        roughness,
        selectedTicker
    ) {
        val wv = webViewRef ?: return@LaunchedEffect
        val escapedJson = signalsJsonPayload
            .replace("\\", "\\\\")
            .replace("'", "\\'")
        val jsCall = """
            if (window.updateMoexThreeScene) {
                window.updateMoexThreeScene(
                    '$escapedJson',
                    '${sceneMode.code}',
                    '${lightPreset.code}',
                    $autoRotate,
                    $metalness,
                    $roughness,
                    '$selectedTicker'
                );
            }
        """.trimIndent()
        wv.evaluateJavascript(jsCall, null)
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.destroy()
            webViewRef = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF040812))
            .testTag("threejs_pbr_scene_container")
    ) {
        // Full-bleed 100% viewport Three.js WebGL2 Studio PBR Canvas (Zero-Occlusion layout)
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.parseColor("#040812"))
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        mediaPlaybackRequiresUserGesture = false
                        cacheMode = WebSettings.LOAD_DEFAULT
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            val escapedJson = signalsJsonPayload
                                .replace("\\", "\\\\")
                                .replace("'", "\\'")
                            view?.evaluateJavascript(
                                """
                                if (window.updateMoexThreeScene) {
                                    window.updateMoexThreeScene(
                                        '$escapedJson',
                                        '${sceneMode.code}',
                                        '${lightPreset.code}',
                                        $autoRotate,
                                        $metalness,
                                        $roughness,
                                        '$selectedTicker'
                                    );
                                }
                                """.trimIndent(),
                                null
                            )
                        }
                    }
                    webChromeClient = WebChromeClient()
                    addJavascriptInterface(
                        ThreeJsSceneBridge { clickedTicker ->
                            post {
                                selectedTicker = clickedTicker
                            }
                        },
                        "AndroidBridge"
                    )
                    loadDataWithBaseURL(
                        "https://cdnjs.cloudflare.com/",
                        htmlContent,
                        "text/html",
                        "UTF-8",
                        null
                    )
                    webViewRef = this
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .testTag("threejs_webview_canvas")
        )

        // ZERO-OCCLUSION TOP EDGE HUD BAR (Translucent perimeter pills, never blocking center 3D model)
        AnimatedVisibility(
            visible = !zeroOcclusionMinimalHud,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 3 Procedural Scene Switcher Pills
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xAA070E1B),
                        border = BorderStroke(0.8.dp, palette.cyanAccent.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ThreeJsSceneMode.entries.forEach { mode ->
                                val active = sceneMode == mode
                                Surface(
                                    shape = RoundedCornerShape(9.dp),
                                    color = if (active) palette.cyanAccent else Color.Transparent,
                                    modifier = Modifier
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            sceneMode = mode
                                        }
                                        .testTag("threejs_scene_mode_${mode.code}")
                                ) {
                                    Text(
                                        text = mode.labelRu,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (active) Color(0xFF04101C) else Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Right Corner Zero-Occlusion Quick Action Pills (Orbit, Studio PBR, Reset Camera, Hide HUD)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xAA070E1B),
                        border = BorderStroke(0.8.dp, palette.borderCard.copy(alpha = 0.65f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    autoRotate = !autoRotate
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("threejs_toggle_orbit_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Авто-вращение 3D орбиты",
                                    tint = if (autoRotate) palette.longGreen else Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val nextIdx = (StudioLightPreset.entries.indexOf(lightPreset) + 1) % StudioLightPreset.entries.size
                                    lightPreset = StudioLightPreset.entries[nextIdx]
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("threejs_cycle_light_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WbSunny,
                                    contentDescription = "Свет студии PBR",
                                    tint = palette.amberWait,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showPbrSliders = !showPbrSliders
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("threejs_pbr_sliders_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Настройки материала PBR",
                                    tint = if (showPbrSliders) palette.cyanAccent else Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    webViewRef?.evaluateJavascript("if(window.resetMoexCamera) window.resetMoexCamera();", null)
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("threejs_reset_camera_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CenterFocusStrong,
                                    contentDescription = "Сброс 3D камеры",
                                    tint = palette.cyanAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    zeroOcclusionMinimalHud = true
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("threejs_zero_occlusion_hide_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VisibilityOff,
                                    contentDescription = "Чистый 3D экран (Zero-Occlusion)",
                                    tint = Color.White.copy(alpha = 0.75f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Optional slim Studio PBR Material bar (Metalness & Roughness + Light Preset badge)
                AnimatedVisibility(visible = showPbrSliders) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xCC070E1B),
                        border = BorderStroke(0.8.dp, palette.cyanAccent.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "${lightPreset.labelRu} • Metal ${(metalness * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = palette.cyanAccent,
                                fontSize = 9.sp
                            )
                            Slider(
                                value = metalness,
                                onValueChange = { metalness = it },
                                valueRange = 0.1f..1.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = palette.cyanAccent,
                                    activeTrackColor = palette.cyanAccent
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(22.dp)
                            )
                            Text(
                                text = "Rough ${(roughness * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = palette.amberWait,
                                fontSize = 9.sp
                            )
                            Slider(
                                value = roughness,
                                onValueChange = { roughness = it },
                                valueRange = 0.05f..0.95f,
                                colors = SliderDefaults.colors(
                                    thumbColor = palette.amberWait,
                                    activeTrackColor = palette.amberWait
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(22.dp)
                            )
                        }
                    }
                }
            }
        }

        // Floating restore button when in 100% Zero-Occlusion Clean View
        if (zeroOcclusionMinimalHud) {
            Surface(
                shape = CircleShape,
                color = Color(0xBB070E1B),
                border = BorderStroke(1.dp, palette.cyanAccent.copy(alpha = 0.7f)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        zeroOcclusionMinimalHud = false
                    }
                    .testTag("threejs_restore_hud_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "Показать HUD",
                        tint = palette.cyanAccent,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "HUD 3D",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // ZERO-OCCLUSION BOTTOM EDGE DOCK: Raycast-selected 3D Stock Inspector + 1-Tap Paper Trade
        AnimatedVisibility(
            visible = !zeroOcclusionMinimalHud && selectedSignal != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            selectedSignal?.let { sig ->
                val accent = when (sig.direction) {
                    SignalDirection.LONG -> palette.longGreen
                    SignalDirection.SHORT -> palette.shortRed
                    SignalDirection.WAIT -> palette.amberWait
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xD9070E1B),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("threejs_bottom_inspector_dock")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(accent)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "#${sig.ticker} (${sig.name}) • ${sig.direction.badgeText}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${QuantSignalEngine.formatPrice(sig.currentPrice, sig.minStep)} ₽ (${QuantSignalEngine.formatSignedPercent(sig.changePercent)})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = accent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "TP1: ${QuantSignalEngine.formatPrice(sig.takeProfit1, sig.minStep)} • TP2: ${QuantSignalEngine.formatPrice(sig.takeProfit2, sig.minStep)} • SL: ${QuantSignalEngine.formatPrice(sig.dynamicStopLoss, sig.minStep)} • Conf ${sig.confidence}% • Клик по 3D башне для выбора",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFB0C4DE),
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelectForRisk(sig.ticker)
                                },
                                border = BorderStroke(1.dp, palette.cyanAccent.copy(alpha = 0.7f)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(
                                    text = "Риск",
                                    color = palette.cyanAccent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onOpenPaperTrade(sig)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accent,
                                    contentColor = Color(0xFF04101C)
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalanceWallet,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "В Демо",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildSignalsJsonForThreeJs(
    signals: List<QuantSignal>,
    imoexSnapshot: ImoexIndexSnapshot
): String {
    val root = JSONObject()
    root.put("imoexValue", imoexSnapshot.currentValue)
    root.put("imoexChange", imoexSnapshot.changePercent)

    val arr = JSONArray()
    val listToUse = if (signals.isNotEmpty()) signals else defaultProceduralSignalsPreview()
    listToUse.forEach { sig ->
        val obj = JSONObject()
        obj.put("ticker", sig.ticker)
        obj.put("name", sig.name)
        obj.put("sector", sig.sectorRu)
        obj.put("price", sig.currentPrice)
        obj.put("change", sig.changePercent)
        obj.put("direction", sig.direction.name)
        obj.put("confidence", sig.confidence)
        obj.put("rsi", sig.rsi14)
        obj.put("volumeRatio", sig.volumeRatio)
        obj.put("buyRatio", sig.buySellVolumeRatio)
        arr.put(obj)
    }
    root.put("stocks", arr)
    return root.toString()
}

private data class PreviewStock3D(
    val ticker: String,
    val name: String,
    val sectorRu: String,
    val currentPrice: Double,
    val changePercent: Double,
    val direction: SignalDirection,
    val confidence: Int,
    val rsi14: Double,
    val volumeRatio: Double,
    val buySellVolumeRatio: Double
)

private fun defaultProceduralSignalsPreview(): List<QuantSignal> = emptyList()

/**
 * Self-contained HTML5 + Three.js (r128) Studio PBR Engine + Offline WebGL/Canvas3D PBR Fallback
 * so that 3D procedural MOEX models render with zero latency both online and offline.
 */
private fun buildThreeJsPbrStudioHtml(): String = """
<!DOCTYPE html>
<html lang="ru">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
<title>MOEX Pulse 3D Studio PBR</title>
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; user-select: none; -webkit-user-select: none; }
  html, body { width: 100%; height: 100%; overflow: hidden; background: #040812; font-family: monospace; }
  #viewport { width: 100%; height: 100%; display: block; touch-action: none; }
  #cornerTelemetry {
    position: fixed;
    bottom: 66px;
    left: 12px;
    color: rgba(0, 229, 255, 0.82);
    font-size: 10px;
    pointer-events: none;
    text-shadow: 0 1px 4px rgba(0,0,0,0.9);
  }
</style>
<script src="https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js"></script>
</head>
<body>
<canvas id="viewport"></canvas>
<div id="cornerTelemetry">THREE.JS STUDIO PBR • DRAG: ORBIT • PINCH: ZOOM • TAP: PICK 3D ASSET</div>

<script>
(function() {
  const canvas = document.getElementById('viewport');
  const telemetryEl = document.getElementById('cornerTelemetry');

  const DEFAULT_STOCKS = [
    { ticker: 'SBER', name: 'Сбербанк', sector: 'Банки', price: 304.5, change: 1.45, direction: 'LONG', confidence: 86, rsi: 61, volumeRatio: 1.5, buyRatio: 0.68 },
    { ticker: 'LKOH', name: 'ЛУКОЙЛ', sector: 'Нефть', price: 7420.0, change: 1.12, direction: 'LONG', confidence: 82, rsi: 58, volumeRatio: 1.3, buyRatio: 0.64 },
    { ticker: 'GAZP', name: 'Газпром', sector: 'Нефть', price: 138.4, change: -0.95, direction: 'SHORT', confidence: 79, rsi: 39, volumeRatio: 1.4, buyRatio: 0.36 },
    { ticker: 'YDEX', name: 'Яндекс', sector: 'IT', price: 4180.0, change: 2.15, direction: 'LONG', confidence: 89, rsi: 65, volumeRatio: 1.8, buyRatio: 0.72 },
    { ticker: 'T', name: 'Т-Технологии', sector: 'Банки', price: 2940.0, change: 1.65, direction: 'LONG', confidence: 84, rsi: 60, volumeRatio: 1.5, buyRatio: 0.67 },
    { ticker: 'ROSN', name: 'Роснефть', sector: 'Нефть', price: 518.0, change: 0.45, direction: 'WAIT', confidence: 64, rsi: 52, volumeRatio: 1.0, buyRatio: 0.52 },
    { ticker: 'NVTK', name: 'НОВАТЭК', sector: 'Нефть', price: 1045.0, change: -1.25, direction: 'SHORT', confidence: 80, rsi: 38, volumeRatio: 1.3, buyRatio: 0.35 },
    { ticker: 'GMKN', name: 'Норникель', sector: 'Металлы', price: 118.6, change: -0.60, direction: 'WAIT', confidence: 62, rsi: 46, volumeRatio: 1.1, buyRatio: 0.46 },
    { ticker: 'PLZL', name: 'Полюс', sector: 'Металлы', price: 14650.0, change: 1.90, direction: 'LONG', confidence: 87, rsi: 63, volumeRatio: 1.6, buyRatio: 0.70 },
    { ticker: 'CHMF', name: 'Северсталь', sector: 'Металлы', price: 1290.0, change: 0.85, direction: 'LONG', confidence: 76, rsi: 56, volumeRatio: 1.2, buyRatio: 0.59 },
    { ticker: 'TATN', name: 'Татнефть', sector: 'Нефть', price: 665.0, change: 1.10, direction: 'LONG', confidence: 78, rsi: 57, volumeRatio: 1.25, buyRatio: 0.61 },
    { ticker: 'SNGS', name: 'Сургутнефтегаз', sector: 'Нефть', price: 27.8, change: 0.25, direction: 'WAIT', confidence: 60, rsi: 50, volumeRatio: 0.95, buyRatio: 0.51 },
    { ticker: 'MOEX', name: 'МосБиржа', sector: 'Банки', price: 224.5, change: 1.35, direction: 'LONG', confidence: 81, rsi: 59, volumeRatio: 1.35, buyRatio: 0.63 },
    { ticker: 'VTBR', name: 'Банк ВТБ', sector: 'Банки', price: 88.2, change: -0.75, direction: 'SHORT', confidence: 75, rsi: 42, volumeRatio: 1.2, buyRatio: 0.40 },
    { ticker: 'NLMK', name: 'НЛМК', sector: 'Металлы', price: 144.0, change: -0.40, direction: 'WAIT', confidence: 63, rsi: 47, volumeRatio: 1.0, buyRatio: 0.48 },
    { ticker: 'ALRS', name: 'АЛРОСА', sector: 'Металлы', price: 54.6, change: -1.15, direction: 'SHORT', confidence: 77, rsi: 37, volumeRatio: 1.3, buyRatio: 0.37 },
    { ticker: 'MTSS', name: 'МТС', sector: 'IT', price: 212.0, change: 0.30, direction: 'WAIT', confidence: 65, rsi: 51, volumeRatio: 1.0, buyRatio: 0.50 },
    { ticker: 'POSI', name: 'Позитив', sector: 'IT', price: 1890.0, change: 1.80, direction: 'LONG', confidence: 83, rsi: 62, volumeRatio: 1.45, buyRatio: 0.66 },
    { ticker: 'VKCO', name: 'ВК', sector: 'IT', price: 318.0, change: -1.55, direction: 'SHORT', confidence: 81, rsi: 36, volumeRatio: 1.5, buyRatio: 0.34 },
    { ticker: 'CBOM', name: 'МКБ', sector: 'Банки', price: 8.45, change: 0.55, direction: 'WAIT', confidence: 66, rsi: 53, volumeRatio: 1.05, buyRatio: 0.54 }
  ];

  let state = {
    imoexValue: 2865.0,
    imoexChange: 0.85,
    stocks: DEFAULT_STOCKS,
    sceneMode: 'CITY',
    lightPreset: 'CYBER',
    autoRotate: true,
    metalness: 0.78,
    roughness: 0.22,
    selectedTicker: 'SBER'
  };

  // Camera spherical coordinates for smooth zero-occlusion orbit & zoom
  let camTheta = 0.65;
  let camPhi = 1.02;
  let camRadius = 26.0;
  let targetX = 0, targetY = 1.5, targetZ = 0;

  window.resetMoexCamera = function() {
    camTheta = 0.65;
    camPhi = 1.02;
    camRadius = 26.0;
    targetX = 0; targetY = 1.5; targetZ = 0;
  };

  // Pointer / Touch gestures (1-finger orbit, 2-finger pinch zoom, tap raycast)
  let isDragging = false;
  let startX = 0, startY = 0;
  let lastX = 0, lastY = 0;
  let pinchDist = 0;
  let pointerDownTime = 0;

  canvas.addEventListener('touchstart', function(e) {
    if (e.touches.length === 1) {
      isDragging = true;
      startX = lastX = e.touches[0].clientX;
      startY = lastY = e.touches[0].clientY;
      pointerDownTime = Date.now();
    } else if (e.touches.length === 2) {
      isDragging = false;
      const dx = e.touches[0].clientX - e.touches[1].clientX;
      const dy = e.touches[0].clientY - e.touches[1].clientY;
      pinchDist = Math.hypot(dx, dy);
    }
  }, { passive: true });

  canvas.addEventListener('touchmove', function(e) {
    if (e.touches.length === 1 && isDragging) {
      const x = e.touches[0].clientX;
      const y = e.touches[0].clientY;
      const dx = x - lastX;
      const dy = y - lastY;
      lastX = x;
      lastY = y;
      camTheta -= dx * 0.0085;
      camPhi = Math.max(0.28, Math.min(1.46, camPhi - dy * 0.0075));
    } else if (e.touches.length === 2) {
      const dx = e.touches[0].clientX - e.touches[1].clientX;
      const dy = e.touches[0].clientY - e.touches[1].clientY;
      const d = Math.hypot(dx, dy);
      if (pinchDist > 0) {
        const scale = pinchDist / d;
        camRadius = Math.max(11.0, Math.min(48.0, camRadius * scale));
      }
      pinchDist = d;
    }
  }, { passive: true });

  canvas.addEventListener('touchend', function(e) {
    if (e.changedTouches.length === 1) {
      const elapsed = Date.now() - pointerDownTime;
      const moveDist = Math.hypot(e.changedTouches[0].clientX - startX, e.changedTouches[0].clientY - startY);
      if (elapsed < 280 && moveDist < 12) {
        handleTapPick(e.changedTouches[0].clientX, e.changedTouches[0].clientY);
      }
    }
    isDragging = false;
  }, { passive: true });

  canvas.addEventListener('mousedown', function(e) {
    isDragging = true;
    startX = lastX = e.clientX;
    startY = lastY = e.clientY;
    pointerDownTime = Date.now();
  });
  window.addEventListener('mousemove', function(e) {
    if (!isDragging) return;
    const dx = e.clientX - lastX;
    const dy = e.clientY - lastY;
    lastX = e.clientX;
    lastY = e.clientY;
    camTheta -= dx * 0.0085;
    camPhi = Math.max(0.28, Math.min(1.46, camPhi - dy * 0.0075));
  });
  window.addEventListener('mouseup', function(e) {
    if (isDragging) {
      const elapsed = Date.now() - pointerDownTime;
      const moveDist = Math.hypot(e.clientX - startX, e.clientY - startY);
      if (elapsed < 280 && moveDist < 10) {
        handleTapPick(e.clientX, e.clientY);
      }
    }
    isDragging = false;
  });

  let pickHandler = function(clientX, clientY) {};
  function handleTapPick(clientX, clientY) {
    pickHandler(clientX, clientY);
  }

  // Check if Three.js loaded from CDN; if so use full Three.js WebGLRenderer Studio PBR, otherwise use built-in procedural 3D PBR projection
  if (typeof THREE !== 'undefined') {
    initThreeJsEngine();
  } else {
    initFallbackProcedural3DEngine();
  }

  function createTickerLabelTexture(ticker, change, dirColorHex, isSelected) {
    const c = document.createElement('canvas');
    c.width = 256;
    c.height = 128;
    const ctx = c.getContext('2d');
    ctx.fillStyle = isSelected ? 'rgba(0, 229, 255, 0.28)' : 'rgba(6, 14, 28, 0.82)';
    ctx.strokeStyle = isSelected ? '#00E5FF' : dirColorHex;
    ctx.lineWidth = isSelected ? 6 : 3;
    ctx.beginPath();
    ctx.roundRect(8, 12, 240, 104, 18);
    ctx.fill();
    ctx.stroke();

    ctx.fillStyle = '#FFFFFF';
    ctx.font = 'bold 38px monospace';
    ctx.textAlign = 'center';
    ctx.fillText(ticker, 128, 60);

    const sign = change >= 0 ? '+' : '';
    ctx.fillStyle = dirColorHex;
    ctx.font = 'bold 28px monospace';
    ctx.fillText(sign + change.toFixed(2) + '%', 128, 98);

    const tex = new THREE.CanvasTexture(c);
    tex.needsUpdate = true;
    return tex;
  }

  function initThreeJsEngine() {
    const renderer = new THREE.WebGLRenderer({ canvas: canvas, antialias: true, powerPreference: 'high-performance' });
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1.5, 2.0));
    renderer.setSize(window.innerWidth, window.innerHeight);
    renderer.toneMapping = THREE.ACESFilmicToneMapping;
    renderer.toneMappingExposure = 1.25;
    renderer.shadowMap.enabled = true;
    renderer.shadowMap.type = THREE.PCFSoftShadowMap;

    const scene = new THREE.Scene();
    scene.background = new THREE.Color(0x000000);
    scene.fog = new THREE.FogExp2(0x000000, 0.018);

    const camera = new THREE.PerspectiveCamera(48, window.innerWidth / window.innerHeight, 0.1, 200);

    // Studio 3-Point Monochrome PBR Lighting Rig
    const hemiLight = new THREE.HemisphereLight(0xffffff, 0x0a0a0a, 0.90);
    scene.add(hemiLight);

    const keyLight = new THREE.DirectionalLight(0xffffff, 2.3);
    keyLight.position.set(18, 28, 16);
    keyLight.castShadow = true;
    scene.add(keyLight);

    const rimLight = new THREE.PointLight(0xe5e5e5, 2.5, 55);
    rimLight.position.set(-18, 14, -16);
    scene.add(rimLight);

    const fillLight = new THREE.PointLight(0x9e9e9e, 1.8, 50);
    fillLight.position.set(14, 8, -14);
    scene.add(fillLight);

    // Studio Reflective Floor & Architectural Monochrome Grid
    const floorGeo = new THREE.CircleGeometry(36, 64);
    const floorMat = new THREE.MeshStandardMaterial({
      color: 0x050505,
      metalness: 0.88,
      roughness: 0.16
    });
    const floorMesh = new THREE.Mesh(floorGeo, floorMat);
    floorMesh.rotation.x = -Math.PI / 2;
    floorMesh.receiveShadow = true;
    scene.add(floorMesh);

    const gridHelper = new THREE.GridHelper(52, 26, 0xcccccc, 0x222222);
    gridHelper.position.y = 0.02;
    scene.add(gridHelper);

    const assetGroup = new THREE.Group();
    scene.add(assetGroup);

    let pickableMeshes = [];
    let animatedRings = [];
    const raycaster = new THREE.Raycaster();
    const mouse = new THREE.Vector2();

    function rebuildProceduralScene() {
      while (assetGroup.children.length > 0) {
        const ch = assetGroup.children[0];
        assetGroup.remove(ch);
      }
      pickableMeshes = [];
      animatedRings = [];

      // Apply Studio Monochrome Light Preset
      if (state.lightPreset === 'GOLD') {
        keyLight.color.setHex(0xffffff);
        rimLight.color.setHex(0xd4d4d4);
        fillLight.color.setHex(0x8c8c8c);
      } else if (state.lightPreset === 'MATRIX') {
        keyLight.color.setHex(0xf5f5f5);
        rimLight.color.setHex(0xffffff);
        fillLight.color.setHex(0xa3a3a3);
      } else {
        keyLight.color.setHex(0xffffff);
        rimLight.color.setHex(0xe5e5e5);
        fillLight.color.setHex(0x9e9e9e);
      }

      const stocks = (state.stocks && state.stocks.length > 0) ? state.stocks : DEFAULT_STOCKS;

      if (state.sceneMode === 'CITY') {
        // 3D SKYSCRAPER FINANCIAL DISTRICT (5x4 grid of procedural Monochrome PBR towers)
        const cols = 5;
        const spacing = 4.2;
        stocks.forEach((st, idx) => {
          const row = Math.floor(idx / cols);
          const col = idx % cols;
          const x = (col - (cols - 1) / 2) * spacing;
          const z = (row - 1.5) * spacing;

          const isLong = st.direction === 'LONG';
          const isShort = st.direction === 'SHORT';
          const baseColor = isLong ? 0xffffff : (isShort ? 0x787878 : 0x424242);
          const hexStr = isLong ? '#FFFFFF' : (isShort ? '#9E9E9E' : '#707070');
          const isSelected = st.ticker === state.selectedTicker;

          const h = Math.max(1.8, (st.confidence / 100.0) * 7.5 + Math.abs(st.change) * 1.15);

          // Main PBR Tower Body
          const towerGeo = new THREE.BoxGeometry(1.65, h, 1.65);
          const towerMat = new THREE.MeshStandardMaterial({
            color: baseColor,
            metalness: state.metalness,
            roughness: state.roughness,
            emissive: baseColor,
            emissiveIntensity: isSelected ? 0.55 : 0.16
          });
          const towerMesh = new THREE.Mesh(towerGeo, towerMat);
          towerMesh.position.set(x, h / 2, z);
          towerMesh.castShadow = true;
          towerMesh.receiveShadow = true;
          towerMesh.userData = { ticker: st.ticker };
          assetGroup.add(towerMesh);
          pickableMeshes.push(towerMesh);

          // Procedural Crown Spire
          const crownGeo = new THREE.OctahedronGeometry(0.58, 0);
          const crownMat = new THREE.MeshStandardMaterial({
            color: isSelected ? 0xffffff : baseColor,
            metalness: 0.92,
            roughness: 0.1,
            emissive: isSelected ? 0xffffff : baseColor,
            emissiveIntensity: 0.65
          });
          const crown = new THREE.Mesh(crownGeo, crownMat);
          crown.position.set(x, h + 0.75, z);
          crown.userData = { ticker: st.ticker };
          assetGroup.add(crown);
          pickableMeshes.push(crown);
          animatedRings.push({ mesh: crown, speed: 0.025, baseY: h + 0.75 });

          // Selection Halo Ring
          if (isSelected) {
            const ringGeo = new THREE.TorusGeometry(1.45, 0.08, 16, 48);
            const ringMat = new THREE.MeshBasicMaterial({ color: 0xffffff });
            const ring = new THREE.Mesh(ringGeo, ringMat);
            ring.rotation.x = Math.PI / 2;
            ring.position.set(x, 0.18, z);
            assetGroup.add(ring);
          }

          // Floating Billboard Sprite Label
          const spriteMat = new THREE.SpriteMaterial({
            map: createTickerLabelTexture(st.ticker, st.change, hexStr, isSelected),
            transparent: true
          });
          const sprite = new THREE.Sprite(spriteMat);
          sprite.position.set(x, h + 2.0, z);
          sprite.scale.set(2.8, 1.4, 1);
          assetGroup.add(sprite);
        });
      } else if (state.sceneMode === 'ORBIT') {
        // 3D ORBITAL LIQUIDITY CORE (IMOEX central geodesic sphere + orbiting stock satellites)
        const coreColor = state.imoexChange >= 0 ? 0xffffff : 0x8c8c8c;
        const coreGeo = new THREE.IcosahedronGeometry(2.8, 2);
        const coreMat = new THREE.MeshStandardMaterial({
          color: coreColor,
          metalness: state.metalness,
          roughness: state.roughness,
          emissive: coreColor,
          emissiveIntensity: 0.35,
          wireframe: false
        });
        const coreMesh = new THREE.Mesh(coreGeo, coreMat);
        coreMesh.position.set(0, 4.2, 0);
        assetGroup.add(coreMesh);
        animatedRings.push({ mesh: coreMesh, speed: 0.008, baseY: 4.2 });

        // Wireframe outer shell
        const shellGeo = new THREE.IcosahedronGeometry(3.35, 1);
        const shellMat = new THREE.MeshBasicMaterial({ color: 0xffffff, wireframe: true, transparent: true, opacity: 0.35 });
        const shellMesh = new THREE.Mesh(shellGeo, shellMat);
        shellMesh.position.set(0, 4.2, 0);
        assetGroup.add(shellMesh);
        animatedRings.push({ mesh: shellMesh, speed: -0.012, baseY: 4.2 });

        stocks.forEach((st, idx) => {
          const ringIndex = idx % 3;
          const radius = 6.5 + ringIndex * 3.6;
          const angle = (idx / stocks.length) * Math.PI * 2;
          const x = Math.cos(angle) * radius;
          const z = Math.sin(angle) * radius;
          const y = 2.6 + ringIndex * 1.3 + Math.sin(idx) * 0.7;

          const isLong = st.direction === 'LONG';
          const isShort = st.direction === 'SHORT';
          const baseColor = isLong ? 0xffffff : (isShort ? 0x888888 : 0x4a4a4a);
          const hexStr = isLong ? '#FFFFFF' : (isShort ? '#9E9E9E' : '#707070');
          const isSelected = st.ticker === state.selectedTicker;

          const satGeo = new THREE.DodecahedronGeometry(isSelected ? 1.05 : 0.78, 0);
          const satMat = new THREE.MeshStandardMaterial({
            color: baseColor,
            metalness: state.metalness,
            roughness: state.roughness,
            emissive: baseColor,
            emissiveIntensity: isSelected ? 0.65 : 0.25
          });
          const sat = new THREE.Mesh(satGeo, satMat);
          sat.position.set(x, y, z);
          sat.userData = { ticker: st.ticker };
          assetGroup.add(sat);
          pickableMeshes.push(sat);
          animatedRings.push({ mesh: sat, speed: 0.02, baseY: y });

          const spriteMat = new THREE.SpriteMaterial({
            map: createTickerLabelTexture(st.ticker, st.change, hexStr, isSelected),
            transparent: true
          });
          const sprite = new THREE.Sprite(spriteMat);
          sprite.position.set(x, y + 1.55, z);
          sprite.scale.set(2.5, 1.25, 1);
          assetGroup.add(sprite);
        });
      } else {
        // 3D VOLATILITY & RSI WAVE SURFACE
        const stocks = (state.stocks && state.stocks.length > 0) ? state.stocks : DEFAULT_STOCKS;
        stocks.forEach((st, idx) => {
          const angle = (idx / stocks.length) * Math.PI * 2;
          const rad = 4.2 + (idx % 2) * 4.5;
          const x = (idx - (stocks.length - 1) / 2) * 1.65;
          const z = Math.sin(idx * 0.65) * 5.2;
          const h = Math.max(1.4, (st.rsi / 100.0) * 7.2);

          const isLong = st.direction === 'LONG';
          const isShort = st.direction === 'SHORT';
          const baseColor = isLong ? 0xffffff : (isShort ? 0x888888 : 0x4a4a4a);
          const hexStr = isLong ? '#FFFFFF' : (isShort ? '#9E9E9E' : '#707070');
          const isSelected = st.ticker === state.selectedTicker;

          const cylGeo = new THREE.CylinderGeometry(0.62, 0.85, h, 24);
          const cylMat = new THREE.MeshStandardMaterial({
            color: baseColor,
            metalness: state.metalness,
            roughness: state.roughness,
            emissive: baseColor,
            emissiveIntensity: isSelected ? 0.55 : 0.2
          });
          const cyl = new THREE.Mesh(cylGeo, cylMat);
          cyl.position.set(x, h / 2, z);
          cyl.userData = { ticker: st.ticker };
          assetGroup.add(cyl);
          pickableMeshes.push(cyl);

          const spriteMat = new THREE.SpriteMaterial({
            map: createTickerLabelTexture(st.ticker, st.change, hexStr, isSelected),
            transparent: true
          });
          const sprite = new THREE.Sprite(spriteMat);
          sprite.position.set(x, h + 1.4, z);
          sprite.scale.set(2.4, 1.2, 1);
          assetGroup.add(sprite);
        });
      }

      telemetryEl.textContent = 'THREE.JS WEBGL2 PBR • ' + state.sceneMode + ' • IMOEX ' +
        state.imoexValue.toFixed(1) + ' (' + (state.imoexChange >= 0 ? '+' : '') + state.imoexChange.toFixed(2) + '%)';
    }

    pickHandler = function(clientX, clientY) {
      const rect = canvas.getBoundingClientRect();
      mouse.x = ((clientX - rect.left) / rect.width) * 2 - 1;
      mouse.y = -((clientY - rect.top) / rect.height) * 2 + 1;
      raycaster.setFromCamera(mouse, camera);
      const hits = raycaster.intersectObjects(pickableMeshes, false);
      if (hits.length > 0 && hits[0].object.userData && hits[0].object.userData.ticker) {
        const picked = hits[0].object.userData.ticker;
        state.selectedTicker = picked;
        rebuildProceduralScene();
        if (window.AndroidBridge && window.AndroidBridge.onSelectTicker) {
          window.AndroidBridge.onSelectTicker(picked);
        }
      }
    };

    window.updateMoexThreeScene = function(jsonStr, sceneMode, lightPreset, autoRot, metal, rough, selectedTicker) {
      try {
        const parsed = JSON.parse(jsonStr);
        if (parsed.stocks && parsed.stocks.length > 0) {
          state.stocks = parsed.stocks;
        }
        if (typeof parsed.imoexValue === 'number') state.imoexValue = parsed.imoexValue;
        if (typeof parsed.imoexChange === 'number') state.imoexChange = parsed.imoexChange;
      } catch (e) {}
      state.sceneMode = sceneMode || 'CITY';
      state.lightPreset = lightPreset || 'CYBER';
      state.autoRotate = !!autoRot;
      state.metalness = Number(metal) || 0.78;
      state.roughness = Number(rough) || 0.22;
      if (selectedTicker) state.selectedTicker = selectedTicker;
      rebuildProceduralScene();
    };

    window.addEventListener('resize', function() {
      camera.aspect = window.innerWidth / window.innerHeight;
      camera.updateProjectionMatrix();
      renderer.setSize(window.innerWidth, window.innerHeight);
    });

    rebuildProceduralScene();

    let clock = 0;
    function animate() {
      requestAnimationFrame(animate);
      clock += 0.016;
      if (state.autoRotate && !isDragging) {
        camTheta += 0.0032;
      }
      camera.position.x = targetX + camRadius * Math.sin(camPhi) * Math.cos(camTheta);
      camera.position.y = targetY + camRadius * Math.cos(camPhi);
      camera.position.z = targetZ + camRadius * Math.sin(camPhi) * Math.sin(camTheta);
      camera.lookAt(targetX, targetY, targetZ);

      for (let i = 0; i < animatedRings.length; i++) {
        const item = animatedRings[i];
        item.mesh.rotation.y += item.speed;
        item.mesh.position.y = item.baseY + Math.sin(clock * 2.2 + i) * 0.14;
      }
      renderer.render(scene, camera);
    }
    animate();
  }

  // Built-in Offline 3D Isometric/Perspective PBR Renderer if CDN is unreachable
  function initFallbackProcedural3DEngine() {
    const ctx = canvas.getContext('2d');
    function resize() {
      canvas.width = window.innerWidth * (window.devicePixelRatio || 1);
      canvas.height = window.innerHeight * (window.devicePixelRatio || 1);
    }
    window.addEventListener('resize', resize);
    resize();

    let projectedBoxes = [];

    window.updateMoexThreeScene = function(jsonStr, sceneMode, lightPreset, autoRot, metal, rough, selectedTicker) {
      try {
        const parsed = JSON.parse(jsonStr);
        if (parsed.stocks && parsed.stocks.length > 0) state.stocks = parsed.stocks;
      } catch (e) {}
      state.sceneMode = sceneMode || 'CITY';
      state.lightPreset = lightPreset || 'CYBER';
      state.autoRotate = !!autoRot;
      if (selectedTicker) state.selectedTicker = selectedTicker;
    };

    pickHandler = function(clientX, clientY) {
      const dpr = window.devicePixelRatio || 1;
      const px = clientX * dpr;
      const py = clientY * dpr;
      for (let i = projectedBoxes.length - 1; i >= 0; i--) {
        const b = projectedBoxes[i];
        if (Math.hypot(px - b.x, py - b.y) <= b.r) {
          state.selectedTicker = b.ticker;
          if (window.AndroidBridge && window.AndroidBridge.onSelectTicker) {
            window.AndroidBridge.onSelectTicker(b.ticker);
          }
          break;
        }
      }
    };

    function renderFallback() {
      requestAnimationFrame(renderFallback);
      if (state.autoRotate && !isDragging) camTheta += 0.004;
      const w = canvas.width, h = canvas.height;
      ctx.fillStyle = '#040812';
      ctx.fillRect(0, 0, w, h);

      projectedBoxes = [];
      const stocks = (state.stocks && state.stocks.length > 0) ? state.stocks : DEFAULT_STOCKS;
      const cols = 5;
      stocks.forEach((st, idx) => {
        const r = Math.floor(idx / cols) - 1.5;
        const c = (idx % cols) - 2;
        const wx = c * 3.8;
        const wz = r * 3.8;
        const rx = wx * Math.cos(camTheta) - wz * Math.sin(camTheta);
        const rz = wx * Math.sin(camTheta) + wz * Math.cos(camTheta) + camRadius;
        const scale = (Math.min(w, h) * 0.72) / Math.max(8, rz);
        const sx = w / 2 + rx * scale;
        const sy = h * 0.62 - (st.confidence / 22.0) * scale * 0.5;
        const colHex = st.direction === 'LONG' ? '#00E676' : (st.direction === 'SHORT' ? '#FF5252' : '#00E5FF');
        ctx.fillStyle = colHex;
        const bw = Math.max(18, 1.3 * scale);
        const bh = Math.max(28, (st.confidence / 18.0) * scale);
        ctx.fillRect(sx - bw / 2, sy - bh / 2, bw, bh);
        ctx.fillStyle = '#FFFFFF';
        ctx.font = 'bold ' + Math.max(16, Math.floor(scale * 0.42)) + 'px monospace';
        ctx.textAlign = 'center';
        ctx.fillText(st.ticker, sx, sy - bh / 2 - 8);
        projectedBoxes.push({ x: sx, y: sy, r: Math.max(bw, bh) * 0.65, ticker: st.ticker });
      });
    }
    renderFallback();
  }
})();
</script>
</body>
</html>
""".trimIndent()
