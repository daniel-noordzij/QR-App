package io.github.daniel_noordzij.qrapp.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PointF
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.daniel_noordzij.qrapp.R
import io.github.daniel_noordzij.qrapp.ScanState
import io.github.daniel_noordzij.qrapp.ScanViewModel
import io.github.daniel_noordzij.qrapp.ScannedCode
import io.github.daniel_noordzij.qrapp.copyToClipboard
import io.github.daniel_noordzij.qrapp.openAppSettings
import io.github.daniel_noordzij.qrapp.openLink
import io.github.daniel_noordzij.qrapp.shareText
import kotlin.math.min
import android.graphics.Color as AndroidColor

@Composable
fun QrApp(viewModel: ScanViewModel) {
    val context = LocalContext.current
    val activity = LocalActivity.current

    var hasCameraPermission by remember { mutableStateOf(context.hasCameraPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
        viewModel.cameraPermissionDeniedPermanently = !granted && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
    }
    // Picks up a permission granted from the system settings screen.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasCameraPermission = context.hasCameraPermission()
    }

    // The system photo picker: no storage permission needed.
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) viewModel.scanImage(uri) }
    val pickImage: () -> Unit = {
        try {
            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.image_unreadable, Toast.LENGTH_SHORT).show()
        }
    }

    val state = viewModel.state
    val scanning = state == ScanState.Scanning

    // Ask for the camera the first time the scanner is shown.
    LaunchedEffect(scanning, hasCameraPermission) {
        if (scanning && !hasCameraPermission && !viewModel.cameraPermissionRequested) {
            viewModel.cameraPermissionRequested = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    BackHandler(enabled = !scanning) { viewModel.scanAgain() }

    val showingPermissionScreen = scanning && !hasCameraPermission
    SystemBarsEffect(lightIcons = !showingPermissionScreen || isSystemInDarkTheme())

    when (state) {
        ScanState.Scanning ->
            if (hasCameraPermission) {
                ScannerScreen(viewModel, onPickImage = pickImage)
            } else {
                PermissionScreen(
                    deniedPermanently = viewModel.cameraPermissionDeniedPermanently,
                    onRequest = {
                        if (viewModel.cameraPermissionDeniedPermanently) {
                            openAppSettings(context)
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onPickImage = pickImage,
                )
            }

        ScanState.Processing -> ProcessingScreen()

        is ScanState.Found -> FoundScreen(
            code = state.code,
            image = state.image,
            onScanAgain = viewModel::scanAgain,
        )

        is ScanState.Failed -> FailedScreen(
            message = state.message,
            image = state.image,
            onPickImage = pickImage,
            onScanAgain = viewModel::scanAgain,
        )
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

/** Light status bar icons over the camera and images; theme-coloured ones elsewhere. */
@Composable
private fun SystemBarsEffect(lightIcons: Boolean) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    LaunchedEffect(activity, lightIcons) {
        activity.enableEdgeToEdge(
            statusBarStyle = if (lightIcons) {
                SystemBarStyle.dark(AndroidColor.TRANSPARENT)
            } else {
                SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
            },
        )
    }
}

@Composable
private fun ScannerScreen(viewModel: ScanViewModel, onPickImage: () -> Unit) {
    var hasFlash by remember { mutableStateOf(false) }
    var cameraUnavailable by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (cameraUnavailable) {
            Text(
                text = stringResource(R.string.no_camera),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
            )
        } else {
            CameraScanner(
                scanner = viewModel.scanner,
                torchOn = viewModel.torchOn,
                onFlashAvailable = { hasFlash = it },
                onCameraUnavailable = { cameraUnavailable = true },
                onDetected = viewModel::onCodeDetected,
                modifier = Modifier.fillMaxSize(),
            )
            ScanReticle(Modifier.align(Alignment.Center))
            Text(
                text = stringResource(R.string.scan_hint),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .safeDrawingPadding()
                    .padding(top = 24.dp, start = 24.dp, end = 24.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(horizontal = 32.dp, vertical = 24.dp),
        ) {
            OverlayButton(
                icon = R.drawable.ic_image,
                label = R.string.scan_from_image,
                active = false,
                onClick = onPickImage,
            )
            if (hasFlash && !cameraUnavailable) {
                val torchOn = viewModel.torchOn
                OverlayButton(
                    icon = if (torchOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off,
                    label = if (torchOn) R.string.flashlight_off else R.string.flashlight_on,
                    active = torchOn,
                    onClick = { viewModel.torchOn = !torchOn },
                )
            }
        }
    }
}

@Composable
private fun OverlayButton(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    active: Boolean,
    onClick: () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (active) Color.White else Color.Black.copy(alpha = 0.55f),
            contentColor = if (active) Color.Black else Color.White,
        ),
        modifier = Modifier.size(56.dp),
    ) {
        Icon(painterResource(icon), contentDescription = stringResource(label))
    }
}

/** Corner brackets marking the centre of the frame, where the chosen code is taken from. */
@Composable
private fun ScanReticle(modifier: Modifier = Modifier) {
    Canvas(modifier.size(240.dp)) {
        val w = size.width
        val h = size.height
        val arm = size.minDimension * 0.18f
        val r = 20.dp.toPx()
        val path = Path().apply {
            moveTo(0f, arm); lineTo(0f, r); quadraticTo(0f, 0f, r, 0f); lineTo(arm, 0f)
            moveTo(w - arm, 0f); lineTo(w - r, 0f); quadraticTo(w, 0f, w, r); lineTo(w, arm)
            moveTo(w, h - arm); lineTo(w, h - r); quadraticTo(w, h, w - r, h); lineTo(w - arm, h)
            moveTo(arm, h); lineTo(r, h); quadraticTo(0f, h, 0f, h - r); lineTo(0f, h - arm)
        }
        drawPath(path, Color.White, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun PermissionScreen(
    deniedPermanently: Boolean,
    onRequest: () -> Unit,
    onPickImage: () -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_camera),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.size(16.dp))
            Text(
                text = stringResource(R.string.camera_permission_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.camera_permission_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 480.dp),
            )
            Spacer(Modifier.size(24.dp))
            Button(onClick = onRequest) {
                Text(
                    stringResource(
                        if (deniedPermanently) R.string.camera_permission_settings else R.string.camera_permission_grant,
                    ),
                )
            }
            Spacer(Modifier.size(8.dp))
            OutlinedButton(onClick = onPickImage) {
                ButtonContent(R.drawable.ic_image, R.string.scan_from_image)
            }
        }
    }
}

@Composable
private fun ProcessingScreen() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        CircularProgressIndicator(color = Color.White)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FoundScreen(code: ScannedCode, image: Bitmap?, onScanAgain: () -> Unit) {
    val context = LocalContext.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        FrozenFrame(image, code.outline, Modifier.fillMaxSize())
        BottomPanel(Modifier.align(Alignment.BottomCenter)) {
            Text(
                text = stringResource(code.formatName),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            val windowHeight = LocalWindowInfo.current.containerSize.height
            val maxTextHeight = with(LocalDensity.current) { (windowHeight * 0.3f).toDp() }
            SelectionContainer {
                Text(
                    text = code.text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxTextHeight)
                        .verticalScroll(rememberScrollState()),
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { copyToClipboard(context, code.text) }) {
                    ButtonContent(R.drawable.ic_copy, R.string.copy)
                }
                FilledTonalButton(onClick = { shareText(context, code.text) }) {
                    ButtonContent(R.drawable.ic_share, R.string.share)
                }
                code.link?.let { link ->
                    FilledTonalButton(onClick = { openLink(context, link) }) {
                        ButtonContent(R.drawable.ic_open_in_new, R.string.open)
                    }
                }
                OutlinedButton(onClick = onScanAgain) {
                    ButtonContent(R.drawable.ic_refresh, R.string.scan_again)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FailedScreen(
    @StringRes message: Int,
    image: Bitmap?,
    onPickImage: () -> Unit,
    onScanAgain: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        FrozenFrame(image, emptyList(), Modifier.fillMaxSize())
        BottomPanel(Modifier.align(Alignment.BottomCenter)) {
            Text(stringResource(message), style = MaterialTheme.typography.bodyLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onPickImage) {
                    ButtonContent(R.drawable.ic_image, R.string.pick_another_image)
                }
                OutlinedButton(onClick = onScanAgain) {
                    ButtonContent(R.drawable.ic_refresh, R.string.scan_again)
                }
            }
        }
    }
}

@Composable
private fun BottomPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        modifier = modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                )
                .padding(20.dp),
            content = content,
        )
    }
}

@Composable
private fun ButtonContent(@DrawableRes icon: Int, @StringRes label: Int) {
    Icon(
        painterResource(icon),
        contentDescription = null,
        modifier = Modifier.size(ButtonDefaults.IconSize),
    )
    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
    Text(stringResource(label))
}

/** The frozen camera frame (or the scanned image) with the chosen code outlined. */
@Composable
private fun FrozenFrame(image: Bitmap?, outline: List<PointF>, modifier: Modifier = Modifier) {
    if (image == null) return
    val imageBitmap = remember(image) { image.asImageBitmap() }
    val highlight = MaterialTheme.colorScheme.primary
    Box(modifier) {
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        if (outline.size >= 3) {
            Canvas(Modifier.fillMaxSize()) {
                // Same mapping as ContentScale.Fit: scale to fit, then centre.
                val scale = min(size.width / image.width, size.height / image.height)
                val dx = (size.width - image.width * scale) / 2f
                val dy = (size.height - image.height * scale) / 2f
                val path = Path().apply {
                    outline.forEachIndexed { i, p ->
                        val x = dx + p.x * scale
                        val y = dy + p.y * scale
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                }
                drawPath(path, highlight.copy(alpha = 0.3f))
                drawPath(
                    path,
                    highlight,
                    style = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round),
                )
            }
        }
    }
}
