package io.github.daniel_noordzij.qrapp.ui

import android.graphics.Bitmap
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import io.github.daniel_noordzij.qrapp.ScannedCode
import io.github.daniel_noordzij.qrapp.closestTo

/** Lets the screen freeze the camera on demand, e.g. to read the text in the current frame. */
@Stable
class CameraScannerState {
    internal var freezeFrame: (() -> Bitmap?)? = null

    /**
     * Stops scanning and returns the frame as shown on screen, or null if the camera isn't
     * showing anything yet (scanning then simply continues).
     */
    fun freeze(): Bitmap? = freezeFrame?.invoke()
}

@Composable
fun rememberCameraScannerState(): CameraScannerState = remember { CameraScannerState() }

/**
 * Live camera preview that scans for codes. On the first detection it captures the frame
 * as shown on screen and reports the code closest to the centre of the view. Leaving the
 * composition releases the camera.
 */
@Composable
fun CameraScanner(
    state: CameraScannerState,
    scanner: BarcodeScanner,
    torchOn: Boolean,
    onFlashAvailable: (Boolean) -> Unit,
    onCameraUnavailable: () -> Unit,
    onDetected: (code: ScannedCode, frame: Bitmap?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember(context) {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            imageAnalysisResolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(1280, 960),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                    ),
                )
                .build()
        }
    }
    val previewView = remember(context) {
        PreviewView(context).apply {
            // TextureView-based, so the on-screen frame can be captured reliably.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            this.controller = controller
        }
    }
    val currentTorchOn by rememberUpdatedState(torchOn)
    val currentOnDetected by rememberUpdatedState(onDetected)
    val currentOnFlashAvailable by rememberUpdatedState(onFlashAvailable)
    val currentOnCameraUnavailable by rememberUpdatedState(onCameraUnavailable)

    DisposableEffect(controller, lifecycleOwner, state) {
        val mainExecutor = ContextCompat.getMainExecutor(context)
        var disposed = false
        var detected = false
        // The camera is only bound once the preview is laid out, so wait for frames to flow
        // before reading camera info. This also re-applies the flashlight whenever the
        // preview restarts, e.g. after "Scan again" or returning to the app.
        val streamObserver = Observer<PreviewView.StreamState> { streamState ->
            if (streamState != PreviewView.StreamState.STREAMING) return@Observer
            val info = controller.cameraInfo ?: return@Observer
            currentOnFlashAvailable(info.hasFlashUnit())
            if (currentTorchOn) controller.enableTorch(true)
        }
        previewView.previewStreamState.observe(lifecycleOwner, streamObserver)

        controller.setImageAnalysisAnalyzer(
            mainExecutor,
            MlKitAnalyzer(
                listOf(scanner),
                ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED,
                mainExecutor,
            ) { result ->
                if (disposed || detected) return@MlKitAnalyzer
                val code = result.getValue(scanner)
                    ?.closestTo(previewView.width / 2f, previewView.height / 2f)
                    ?: return@MlKitAnalyzer
                detected = true
                controller.clearImageAnalysisAnalyzer()
                currentOnDetected(code, previewView.bitmap)
            },
        )

        state.freezeFrame = freeze@{
            if (disposed || detected) return@freeze null
            val frame = previewView.bitmap ?: return@freeze null
            detected = true
            controller.clearImageAnalysisAnalyzer()
            frame
        }

        controller.initializationFuture.addListener({
            if (disposed) return@addListener
            val bound = try {
                controller.cameraSelector = when {
                    controller.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ->
                        CameraSelector.DEFAULT_BACK_CAMERA
                    controller.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ->
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    else -> null
                } ?: return@addListener currentOnCameraUnavailable()
                controller.bindToLifecycle(lifecycleOwner)
                true
            } catch (e: Exception) {
                false
            }
            if (!bound) currentOnCameraUnavailable()
        }, mainExecutor)

        onDispose {
            disposed = true
            state.freezeFrame = null
            previewView.previewStreamState.removeObserver(streamObserver)
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
        }
    }

    LaunchedEffect(controller, torchOn) {
        if (controller.cameraInfo != null) {
            controller.enableTorch(torchOn)
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}
