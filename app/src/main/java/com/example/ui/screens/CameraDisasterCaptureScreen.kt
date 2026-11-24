package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.ui.components.AiAnalysisResultView
import com.example.ui.viewmodel.AiAnalysisUiState

private const val TAG = "CameraDisasterCapture"

@Composable
fun CameraDisasterCaptureScreen(
    capturedBitmap: Bitmap?,
    analysisState: AiAnalysisUiState,
    onImageCaptured: (Bitmap) -> Unit,
    onRetake: () -> Unit,
    onAnalyzeCapturedImage: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (!hasCameraPermission) {
            CameraPermissionFallback(
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onNavigateBack = onNavigateBack
            )
        } else if (capturedBitmap == null) {
            LiveCameraViewfinder(
                onImageCaptured = onImageCaptured,
                onNavigateBack = onNavigateBack
            )
        } else {
            CapturedDisasterReview(
                bitmap = capturedBitmap,
                analysisState = analysisState,
                onRetake = onRetake,
                onAnalyze = onAnalyzeCapturedImage,
                onNavigateBack = onNavigateBack
            )
        }
    }
}

@Composable
private fun CameraPermissionFallback(
    onRequestPermission: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.widthIn(max = 480.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "ต้องการสิทธิ์เข้าถึงกล้องเพื่อสแกนภัยพิบัติ",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "ใช้กล้องถ่ายภาพสถานการณ์จริง เช่น รอยร้าวอาคาร ระดับน้ำท่วม ดินสไลด์ หรือกลุ่มเมฆพายุ เพื่อส่งให้ Gemini AI ประเมินความเสี่ยง",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("grant_camera_permission_button")
                ) {
                    Text("อนุญาตใช้งานกล้อง")
                }
                OutlinedButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("camera_back_button")
                ) {
                    Text("ย้อนกลับไปเลือกรูปภาพแทน")
                }
            }
        }
    }
}

@Composable
private fun LiveCameraViewfinder(
    onImageCaptured: (Bitmap) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var lensFacing by rememberSaveable { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashMode by rememberSaveable { mutableIntStateOf(ImageCapture.FLASH_MODE_AUTO) }
    var isCapturing by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    val imageCapture = remember(flashMode) {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setFlashMode(flashMode)
            .build()
    }

    DisposableEffect(lifecycleOwner, lensFacing, imageCapture) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        var boundProvider: ProcessCameraProvider? = null

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                boundProvider = cameraProvider
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val selector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    imageCapture
                )
                cameraError = null
            } catch (e: Exception) {
                Log.e(TAG, "CameraX bind failed", e)
                cameraError = e.localizedMessage ?: "ไม่สามารถเชื่อมต่อกล้องบนอุปกรณ์นี้ได้"
            }
        }, executor)

        onDispose {
            boundProvider?.unbindAll()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .testTag("camerax_preview_view")
        )

        // Tactical hazard reticle corner markers
        val reticleColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 36.dp, vertical = 120.dp)
        ) {
            val len = 28.dp.toPx()
            val stroke = 2.5.dp.toPx()
            // Top-left
            drawLine(reticleColor, Offset(0f, 0f), Offset(len, 0f), stroke)
            drawLine(reticleColor, Offset(0f, 0f), Offset(0f, len), stroke)
            // Top-right
            drawLine(reticleColor, Offset(size.width, 0f), Offset(size.width - len, 0f), stroke)
            drawLine(reticleColor, Offset(size.width, 0f), Offset(size.width, len), stroke)
            // Bottom-left
            drawLine(reticleColor, Offset(0f, size.height), Offset(len, size.height), stroke)
            drawLine(reticleColor, Offset(0f, size.height), Offset(0f, size.height - len), stroke)
            // Bottom-right
            drawLine(reticleColor, Offset(size.width, size.height), Offset(size.width - len, size.height), stroke)
            drawLine(reticleColor, Offset(size.width, size.height), Offset(size.width, size.height - len), stroke)
        }

        // Top controls (Back + Flash + Switch Camera)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            FilledTonalIconButton(
                onClick = onNavigateBack,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("camera_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_navigate_back)
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                shape = RoundedCornerShape(50.dp)
            ) {
                Text(
                    text = "AI HAZARD CAMERA",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton(
                    onClick = {
                        flashMode = when (flashMode) {
                            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
                            else -> ImageCapture.FLASH_MODE_AUTO
                        }
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("camera_flash_button")
                ) {
                    Icon(
                        imageVector = when (flashMode) {
                            ImageCapture.FLASH_MODE_ON -> Icons.Filled.FlashOn
                            ImageCapture.FLASH_MODE_OFF -> Icons.Filled.FlashOff
                            else -> Icons.Filled.FlashAuto
                        },
                        contentDescription = stringResource(R.string.cd_toggle_flash)
                    )
                }

                FilledTonalIconButton(
                    onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("camera_switch_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Cameraswitch,
                        contentDescription = stringResource(R.string.cd_switch_camera)
                    )
                }
            }
        }

        // Bottom guidance + Shutter button
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.72f))
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            cameraError?.let { errorMsg ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            Text(
                text = "เล็งกล้องไปที่พื้นที่น้ำท่วม รอยร้าวอาคาร ดินสไลด์ หรือเมฆพายุ",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White
            )

            Button(
                onClick = {
                    if (isCapturing) return@Button
                    isCapturing = true
                    cameraError = null
                    imageCapture.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                try {
                                    val bitmap = imageProxyToRotatedBitmap(image)
                                    onImageCaptured(bitmap)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Bitmap conversion failed", e)
                                    cameraError = "ไม่สามารถประมวลผลภาพที่ถ่ายได้"
                                } finally {
                                    image.close()
                                    isCapturing = false
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                Log.e(TAG, "Capture failed", exception)
                                cameraError = exception.localizedMessage ?: "การถ่ายภาพไม่สำเร็จ"
                                isCapturing = false
                            }
                        }
                    )
                },
                enabled = !isCapturing,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .height(60.dp)
                    .minimumInteractiveComponentSize()
                    .semantics(mergeDescendants = true) {}
                    .testTag("camera_capture_button")
            ) {
                if (isCapturing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("กำลังบันทึกภาพ...")
                } else {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = stringResource(R.string.cd_capture_photo)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ถ่ายภาพเพื่อวิเคราะห์", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable
private fun CapturedDisasterReview(
    bitmap: Bitmap,
    analysisState: AiAnalysisUiState,
    onRetake: () -> Unit,
    onAnalyze: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    var note by rememberSaveable { mutableStateOf("") }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalIconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("review_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_navigate_back)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "ตรวจสอบภาพและวิเคราะห์ความเสี่ยง",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    OutlinedButton(
                        onClick = onRetake,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("camera_retake_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ถ่ายใหม่")
                    }
                }
            }

            item {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "ภาพถ่ายสถานการณ์ภัยพิบัติที่บันทึกได้",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(MaterialTheme.shapes.large)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            MaterialTheme.shapes.large
                        )
                )
            }

            item {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("ระบุรายละเอียดเพิ่มเติม (ไม่บังคับ)") },
                    placeholder = { Text("เช่น พบรอยร้าวที่เสาชั้น 1 หลังแผ่นดินไหว, ระดับน้ำหน้าถนน") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("camera_context_input")
                )
            }

            item {
                Button(
                    onClick = { onAnalyze(note) },
                    enabled = analysisState !is AiAnalysisUiState.Loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("camera_send_to_ai_button")
                ) {
                    if (analysisState is AiAnalysisUiState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("กำลังส่งภาพให้ Gemini AI วิเคราะห์...")
                    } else {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ส่งวิเคราะห์ความเสี่ยงด้วย Gemini AI")
                    }
                }
            }

            item {
                when (analysisState) {
                    AiAnalysisUiState.Idle -> Unit
                    AiAnalysisUiState.Loading -> Unit
                    is AiAnalysisUiState.Success -> {
                        AiAnalysisResultView(analysis = analysisState.analysis)
                    }
                    is AiAnalysisUiState.Error -> {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(
                                text = analysisState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun imageProxyToRotatedBitmap(image: ImageProxy): Bitmap {
    val rawBitmap = image.toBitmap()
    val rotation = image.imageInfo.rotationDegrees.toFloat()
    if (rotation == 0f) return rawBitmap
    val matrix = Matrix().apply { postRotate(rotation) }
    return Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
}
