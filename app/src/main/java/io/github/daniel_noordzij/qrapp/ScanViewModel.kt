package io.github.daniel_noordzij.qrapp

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface ScanState {
    data object Scanning : ScanState
    data object Processing : ScanState
    /** Reading text from a frozen camera frame. */
    class Recognizing(val image: Bitmap) : ScanState
    class Found(val code: ScannedCode, val image: Bitmap?) : ScanState
    class Failed(
        @param:StringRes val message: Int,
        val image: Bitmap?,
        val offerImagePick: Boolean = true,
    ) : ScanState
}

/**
 * Holds the current scan in memory only. Nothing here is written to disk or to saved
 * instance state, so a scan disappears when the process ends.
 */
class ScanViewModel(application: Application) : AndroidViewModel(application) {
    val scanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build(),
    )
    private val textRecognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    var state: ScanState by mutableStateOf(ScanState.Scanning)
        private set

    var torchOn by mutableStateOf(false)

    var cameraPermissionRequested = false
    var cameraPermissionDeniedPermanently by mutableStateOf(false)

    private var job: Job? = null

    fun onCodeDetected(code: ScannedCode, frame: Bitmap?) {
        if (state == ScanState.Scanning) {
            state = ScanState.Found(code, frame)
        }
    }

    /** Reads the text in a frozen camera frame (OCR). */
    fun recognizeText(frame: Bitmap) {
        if (state != ScanState.Scanning) return
        job?.cancel()
        state = ScanState.Recognizing(frame)
        job = viewModelScope.launch {
            val code = try {
                textRecognizer.process(InputImage.fromBitmap(frame, 0)).await().toScannedCode()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            state = if (code != null) {
                ScanState.Found(code, frame)
            } else {
                ScanState.Failed(R.string.no_text_found, frame, offerImagePick = false)
            }
        }
    }

    fun scanImage(uri: Uri) {
        job?.cancel()
        state = ScanState.Processing
        job = viewModelScope.launch {
            val bitmap = try {
                withContext(Dispatchers.IO) { decodeBitmap(getApplication<Application>(), uri) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (bitmap == null) {
                state = ScanState.Failed(R.string.image_unreadable, null)
                return@launch
            }
            val code = try {
                scanner.process(InputImage.fromBitmap(bitmap, 0)).await()
                    .closestTo(bitmap.width / 2f, bitmap.height / 2f)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            state = if (code != null) {
                ScanState.Found(code, bitmap)
            } else {
                ScanState.Failed(R.string.no_code_in_image, bitmap)
            }
        }
    }

    fun scanAgain() {
        job?.cancel()
        job = null
        state = ScanState.Scanning
    }

    override fun onCleared() {
        scanner.close()
        textRecognizer.close()
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
