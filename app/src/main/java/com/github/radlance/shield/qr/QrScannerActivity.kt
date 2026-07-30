package com.github.radlance.shield.qr

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.radlance.shield.R
import com.github.radlance.shield.uikit.theme.core.ThemeViewModel
import com.github.radlance.shield.uikit.theme.ui.ShieldTheme
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import org.koin.compose.viewmodel.koinViewModel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class QrScannerActivity : ComponentActivity() {
    private lateinit var scanner: BarcodeScanner
    private lateinit var analysisExecutor: ExecutorService

    private var previewView: PreviewView? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var cameraStarting = false
    private var permissionRequested = false
    private var uiState by mutableStateOf<QrScannerUiState>(QrScannerUiState.Scanning)
    private var zoomRatio by mutableFloatStateOf(1f)
    private var minimumZoomRatio = 1f
    private var maximumZoomRatio = 1f
    private var hasFlash by mutableStateOf(false)
    private var torchEnabled by mutableStateOf(false)
    private var focusPoint by mutableStateOf<Offset?>(null)
    private var action: () -> Unit = {}

    private val mainHandler = Handler(Looper.getMainLooper())
    private val clearFocusIndicator = Runnable { focusPoint = null }
    private val scanRegion = AtomicReference(QrScanRegion.forViewport(0, 0))
    private val resultGate = QrScanResultGate()
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            previewView?.post(::startCamera)
        } else {
            showPermissionRequired()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionRequested = savedInstanceState?.getBoolean(KEY_PERMISSION_REQUESTED) ?: false
        scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
        analysisExecutor = Executors.newSingleThreadExecutor()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        setContent {
            val themeViewModel = koinViewModel<ThemeViewModel>()
            val themeState by themeViewModel.uiState.collectAsStateWithLifecycle()
            ShieldTheme(themeConfiguration = themeState.configuration) {
                QrScannerScreen(
                    uiState = uiState,
                    hasFlash = hasFlash,
                    torchEnabled = torchEnabled,
                    focusPoint = focusPoint,
                    onPreviewReady = ::attachPreview,
                    onBack = ::finish,
                    onAction = { action() },
                    onToggleTorch = ::toggleTorch,
                    onZoomGesture = ::zoomBy,
                    onFocus = ::focusAt
                )
            }
        }

        if (!hasCameraPermission()) {
            requestCameraPermission()
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasCameraPermission()) {
            previewView?.post(::startCamera)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_PERMISSION_REQUESTED, permissionRequested)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        cameraProvider?.unbindAll()
        scanner.close()
        analysisExecutor.shutdown()
        super.onDestroy()
    }

    private fun attachPreview(view: PreviewView) {
        if (previewView === view) return
        previewView = view
        view.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            scanRegion.set(QrScanRegion.forViewport(right - left, bottom - top))
        }
        view.post {
            scanRegion.set(QrScanRegion.forViewport(view.width, view.height))
            if (hasCameraPermission()) {
                startCamera()
            }
        }
    }

    private fun requestCameraPermission() {
        permissionRequested = true
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun showPermissionRequired() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        val openSettings = permissionRequested &&
            !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
        if (openSettings) {
            uiState = QrScannerUiState.Error(
                messageRes = R.string.camera_permission_required,
                actionRes = R.string.open_app_settings,
                icon = QrScannerErrorIcon.Permission
            )
            action = {
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", packageName, null)
                    )
                )
            }
        } else {
            uiState = QrScannerUiState.Error(
                messageRes = R.string.camera_permission_required,
                actionRes = R.string.grant_camera_permission,
                icon = QrScannerErrorIcon.Permission
            )
            action = ::requestCameraPermission
        }
    }

    private fun startCamera() {
        val currentPreview = previewView ?: return
        if (
            currentPreview.width == 0 ||
            currentPreview.height == 0 ||
            cameraProvider != null ||
            cameraStarting
        ) {
            return
        }
        cameraStarting = true
        uiState = QrScannerUiState.Scanning
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener(
            {
                cameraStarting = false
                if (isFinishing || isDestroyed) return@addListener
                runCatching {
                    providerFuture.get()
                }.onSuccess { provider ->
                    cameraProvider = provider
                    bindCamera(provider, currentPreview)
                }.onFailure {
                    showCameraError()
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun bindCamera(provider: ProcessCameraProvider, currentPreview: PreviewView) {
        val cameraSelector = selectCamera(provider) ?: run {
            showCameraError()
            return
        }
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = currentPreview.surfaceProvider
        }
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(
                    analysisExecutor,
                    QrCodeAnalyzer(
                        scanner = scanner,
                        scanRegion = scanRegion,
                        resultGate = resultGate,
                        onQrCode = ::returnResult
                    )
                )
            }

        runCatching {
            provider.unbindAll()
            val useCaseGroup = UseCaseGroup.Builder()
                .addUseCase(preview)
                .addUseCase(imageAnalysis)
                .apply {
                    currentPreview.viewPort?.let(::setViewPort)
                }
                .build()
            camera = provider.bindToLifecycle(this, cameraSelector, useCaseGroup).also {
                observeCameraState(it)
            }
        }.onFailure {
            showCameraError()
        }
    }

    private fun observeCameraState(currentCamera: Camera) {
        hasFlash = currentCamera.cameraInfo.hasFlashUnit()
        currentCamera.cameraInfo.zoomState.observe(this) { state ->
            minimumZoomRatio = state.minZoomRatio
            maximumZoomRatio = state.maxZoomRatio
            zoomRatio = state.zoomRatio
        }
        currentCamera.cameraInfo.torchState.observe(this) { state ->
            torchEnabled = state == TorchState.ON
        }
    }

    private fun toggleTorch() {
        val currentCamera = camera ?: return
        if (!currentCamera.cameraInfo.hasFlashUnit()) return
        currentCamera.cameraControl.enableTorch(!torchEnabled)
    }

    private fun zoomBy(scale: Float) {
        val currentCamera = camera ?: return
        val target = (zoomRatio * scale).coerceIn(minimumZoomRatio, maximumZoomRatio)
        currentCamera.cameraControl.setZoomRatio(target)
    }

    private fun focusAt(point: Offset) {
        val currentPreview = previewView ?: return
        val currentCamera = camera ?: return
        val meteringPoint = currentPreview.meteringPointFactory.createPoint(point.x, point.y)
        val focusAction = FocusMeteringAction.Builder(
            meteringPoint,
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
        )
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()
        currentCamera.cameraControl.startFocusAndMetering(focusAction)
        focusPoint = point
        mainHandler.removeCallbacks(clearFocusIndicator)
        mainHandler.postDelayed(clearFocusIndicator, FOCUS_INDICATOR_DURATION_MILLIS)
    }

    private fun selectCamera(provider: ProcessCameraProvider): CameraSelector? {
        return runCatching {
            when {
                provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ->
                    CameraSelector.DEFAULT_BACK_CAMERA
                provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ->
                    CameraSelector.DEFAULT_FRONT_CAMERA
                else -> null
            }
        }.getOrNull()
    }

    private fun returnResult(value: String) {
        if (isFinishing || isDestroyed) return
        setResult(
            RESULT_OK,
            Intent().putExtra(EXTRA_QR_VALUE, value)
        )
        finish()
    }

    private fun showCameraError() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        camera = null
        uiState = QrScannerUiState.Error(
            messageRes = R.string.camera_unavailable,
            actionRes = R.string.back,
            icon = QrScannerErrorIcon.Unavailable
        )
        action = ::finish
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val EXTRA_QR_VALUE = "qr_value"
        private const val KEY_PERMISSION_REQUESTED = "permission_requested"
        private const val FOCUS_INDICATOR_DURATION_MILLIS = 1_200L

        fun createIntent(context: Context): Intent = Intent(context, QrScannerActivity::class.java)
    }
}

private sealed interface QrScannerUiState {
    data object Scanning : QrScannerUiState

    data class Error(
        val messageRes: Int,
        val actionRes: Int,
        val icon: QrScannerErrorIcon
    ) : QrScannerUiState
}

private enum class QrScannerErrorIcon {
    Permission,
    Unavailable
}

@Composable
private fun QrScannerScreen(
    uiState: QrScannerUiState,
    hasFlash: Boolean,
    torchEnabled: Boolean,
    focusPoint: Offset?,
    onPreviewReady: (PreviewView) -> Unit,
    onBack: () -> Unit,
    onAction: () -> Unit,
    onToggleTorch: () -> Unit,
    onZoomGesture: (Float) -> Unit,
    onFocus: (Offset) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = onFocus)
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    onZoomGesture(zoom)
                }
            }
    ) {
        AndroidView(
            factory = { context ->
                PreviewView(context).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    onPreviewReady(this)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        ScannerFinder(
            focusPoint = focusPoint,
            modifier = Modifier.fillMaxSize()
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.48f))
                .statusBarsPadding()
                .height(64.dp)
                .padding(horizontal = 8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = Color.White
                )
            }
            Text(
                text = stringResource(R.string.qr_scanner_title),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
            Spacer(modifier = Modifier.weight(1f))
            if (hasFlash) {
                IconButton(onClick = onToggleTorch) {
                    Icon(
                        imageVector = if (torchEnabled) {
                            Icons.Filled.FlashOn
                        } else {
                            Icons.Filled.FlashOff
                        },
                        contentDescription = stringResource(
                            if (torchEnabled) R.string.flashlight_off else R.string.flashlight_on
                        ),
                        tint = if (torchEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.White
                        }
                    )
                }
            }
        }

        when (uiState) {
            QrScannerUiState.Scanning -> {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 28.dp)
                ) {
                    Text(
                        text = stringResource(R.string.qr_scanner_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                    )
                }
            }

            is QrScannerUiState.Error -> {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 26.dp)
                    ) {
                        Icon(
                            imageVector = when (uiState.icon) {
                                QrScannerErrorIcon.Permission -> Icons.Filled.CameraAlt
                                QrScannerErrorIcon.Unavailable -> Icons.Filled.VideocamOff
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(52.dp)
                        )
                        Text(
                            text = stringResource(uiState.messageRes),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Button(onClick = onAction) {
                            Text(stringResource(uiState.actionRes))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScannerFinder(
    focusPoint: Offset?,
    modifier: Modifier = Modifier
) {
    val strokeWidth = with(LocalDensity.current) { 3.dp.toPx() }
    val cornerRadius = with(LocalDensity.current) { 28.dp.toPx() }
    val borderColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier.graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
        }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val finderSize = minOf(size.width, size.height) * QrScanRegion.FINDER_SIZE_FRACTION
            val left = (size.width - finderSize) / 2f
            val top = (size.height - finderSize) / 2f
            val scrim = Color.Black.copy(alpha = 0.56f)

            drawRect(scrim)
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(left, top),
                size = Size(finderSize, finderSize),
                cornerRadius = CornerRadius(cornerRadius),
                blendMode = BlendMode.Clear
            )
            drawRoundRect(
                color = borderColor,
                topLeft = Offset(left, top),
                size = Size(finderSize, finderSize),
                cornerRadius = CornerRadius(cornerRadius),
                style = Stroke(width = strokeWidth)
            )
            focusPoint?.let { point ->
                drawCircle(
                    color = borderColor,
                    radius = 30.dp.toPx(),
                    center = point,
                    style = Stroke(width = strokeWidth)
                )
                drawCircle(
                    color = borderColor,
                    radius = 3.dp.toPx(),
                    center = point
                )
            }
        }
    }
}

private class QrCodeAnalyzer(
    private val scanner: BarcodeScanner,
    private val scanRegion: AtomicReference<QrScanRegion>,
    private val resultGate: QrScanResultGate,
    private val onQrCode: (String) -> Unit
) : ImageAnalysis.Analyzer {
    private val processing = AtomicBoolean(false)

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        if (!processing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            processing.set(false)
            imageProxy.close()
            return
        }
        val rotation = imageProxy.imageInfo.rotationDegrees
        val cropRect = imageProxy.cropRect
        val viewport = orientedCropRect(
            imageWidth = imageProxy.width,
            imageHeight = imageProxy.height,
            rotationDegrees = rotation,
            cropRect = QrImageRect(
                left = cropRect.left,
                top = cropRect.top,
                right = cropRect.right,
                bottom = cropRect.bottom
            )
        )

        runCatching {
            scanner.process(InputImage.fromMediaImage(mediaImage, rotation))
                .addOnSuccessListener { barcodes ->
                    val region = scanRegion.get()
                    val value = barcodes.firstNotNullOfOrNull { barcode ->
                        val bounds = barcode.boundingBox ?: return@firstNotNullOfOrNull null
                        barcode.rawValue?.takeIf {
                            region.contains(
                                barcode = QrImageRect(
                                    left = bounds.left,
                                    top = bounds.top,
                                    right = bounds.right,
                                    bottom = bounds.bottom
                                ),
                                viewport = viewport
                            )
                        }
                    }
                    resultGate.tryDeliver(value, onQrCode)
                }
                .addOnCompleteListener {
                    processing.set(false)
                    imageProxy.close()
                }
        }.onFailure {
            processing.set(false)
            imageProxy.close()
        }
    }
}
