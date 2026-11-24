package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.components.AiAnalysisResultView
import com.example.ui.viewmodel.AdvisorStreamUiState
import com.example.ui.viewmodel.AiAnalysisUiState

private val QUICK_SCENARIO_PROMPTS = listOf(
    "เกิดแผ่นดินไหวขณะอยู่บนตึกสูง ควรปฏิบัติตัวอย่างไร?",
    "น้ำกำลังจะไหลเข้าท่วมชั้น 1 ควรตัดไฟและเตรียมตัวอย่างไร?",
    "พบเห็นรอยร้าวแนวทแยงที่เสาและคานหลังแผ่นดินไหว อันตรายไหม?",
    "แนวทางรับมือพายุฤดูร้อนและลมกระโชกแรงขณะขับรถ"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiAnalyzerScreen(
    selectedPhotoUri: Uri?,
    photoAnalysisState: AiAnalysisUiState,
    advisorStreamState: AdvisorStreamUiState,
    onPhotoSelected: (Uri?) -> Unit,
    onAnalyzePhoto: (String) -> Unit,
    onOpenCamera: () -> Unit,
    onAskAdvisor: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedMode by rememberSaveable { mutableIntStateOf(0) }
    var photoNote by rememberSaveable { mutableStateOf("") }
    var advisorQuestion by rememberSaveable { mutableStateOf("") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onPhotoSelected(uri)
        }
    }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
        ) {
            item {
                AiScannerHeroBanner()
            }

            item {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SegmentedButton(
                        selected = selectedMode == 0,
                        onClick = { selectedMode = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.QrCodeScanner,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("mode_photo_scanner_tab")
                    ) {
                        Text("สแกนภาพภัยพิบัติ")
                    }
                    SegmentedButton(
                        selected = selectedMode == 1,
                        onClick = { selectedMode = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.Psychology,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("mode_ai_advisor_tab")
                    ) {
                        Text("ที่ปรึกษา AI ฉุกเฉิน")
                    }
                }
            }

            if (selectedMode == 0) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "วิเคราะห์ความเสี่ยงจากภาพถ่ายจริง",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "ถ่ายภาพสดด้วยกล้องหรือเลือกรูปภาพน้ำท่วม รอยร้าวอาคาร ดินสไลด์ เมฆพายุ เพื่อให้ Gemini AI ประเมินระดับอันตราย 1-5 และขั้นตอนรับมือ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Button(
                                onClick = onOpenCamera,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("open_camerax_screen_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CameraAlt,
                                    contentDescription = stringResource(R.string.action_open_camera),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.action_open_camera))
                            }

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(210.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline,
                                        MaterialTheme.shapes.medium
                                    )
                                    .testTag("photo_picker_box")
                                    .clickable {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                    }
                            ) {
                                if (selectedPhotoUri != null) {
                                    AsyncImage(
                                        model = selectedPhotoUri,
                                        contentDescription = "ภาพถ่ายสถานการณ์ภัยพิบัติที่เลือก",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(16.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.AddPhotoAlternate,
                                            contentDescription = stringResource(R.string.action_pick_photo),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(44.dp)
                                        )
                                        Text(
                                            text = "แตะเพื่อเลือกรูปภาพจากเครื่อง",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "ใช้ Android Photo Picker (ปลอดภัย ไม่ต้องขอสิทธิ์เข้าถึงคลังภาพทั้งหมด)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            if (selectedPhotoUri != null) {
                                OutlinedButton(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                        .testTag("change_photo_button")
                                ) {
                                    Text("เปลี่ยนรูปภาพ")
                                }
                            }

                            OutlinedTextField(
                                value = photoNote,
                                onValueChange = { photoNote = it },
                                label = { Text("รายละเอียดเพิ่มเติม (ไม่บังคับ)") },
                                placeholder = { Text("เช่น รอยร้าวที่เสาชั้น 1 หลังแผ่นดินไหว, ระดับน้ำหน้าบ้าน") },
                                maxLines = 3,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("photo_context_input")
                            )

                            Button(
                                onClick = { onAnalyzePhoto(photoNote) },
                                enabled = selectedPhotoUri != null && photoAnalysisState !is AiAnalysisUiState.Loading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("analyze_photo_button")
                            ) {
                                if (photoAnalysisState is AiAnalysisUiState.Loading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("AI กำลังวิเคราะห์ภาพ...")
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.action_analyze_photo))
                                }
                            }
                        }
                    }
                }

                item {
                    when (photoAnalysisState) {
                        AiAnalysisUiState.Idle -> Unit
                        AiAnalysisUiState.Loading -> Unit
                        is AiAnalysisUiState.Success -> {
                            AiAnalysisResultView(analysis = photoAnalysisState.analysis)
                        }
                        is AiAnalysisUiState.Error -> {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text(
                                    text = photoAnalysisState.message,
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
            } else {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "ปรึกษาแผนรับมือเหตุฉุกเฉินกับ AI",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "AI จะนำข้อมูลสภาพอากาศและแผ่นดินไหวล่าสุดในพื้นที่ของคุณมาประกอบคำแนะนำแบบเรียลไทม์",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                QUICK_SCENARIO_PROMPTS.forEachIndexed { index, prompt ->
                                    AssistChip(
                                        onClick = {
                                            advisorQuestion = prompt
                                            onAskAdvisor(prompt)
                                        },
                                        label = { Text(prompt, maxLines = 1) },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("quick_prompt_chip_$index")
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = advisorQuestion,
                                onValueChange = { advisorQuestion = it },
                                label = { Text("พิมพ์สถานการณ์หรือคำถามของคุณ") },
                                placeholder = { Text("เช่น บ้านอยู่ใกล้แม่น้ำ ฝนตกหนักมา 3 ชั่วโมง ควรเตรียมอะไรบ้าง") },
                                minLines = 2,
                                maxLines = 4,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("advisor_question_input")
                            )

                            Button(
                                onClick = { onAskAdvisor(advisorQuestion) },
                                enabled = advisorQuestion.isNotBlank() && advisorStreamState !is AdvisorStreamUiState.Loading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("ask_advisor_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("ขอคำแนะนำเร่งด่วนจาก AI")
                            }
                        }
                    }
                }

                item {
                    when (advisorStreamState) {
                        AdvisorStreamUiState.Idle -> Unit
                        AdvisorStreamUiState.Loading -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("กำลังวิเคราะห์สถานการณ์และเตรียมคำแนะนำ...")
                            }
                        }
                        is AdvisorStreamUiState.Streaming -> {
                            AdvisorResponseCard(
                                question = advisorStreamState.question,
                                answer = advisorStreamState.text,
                                isStreaming = true
                            )
                        }
                        is AdvisorStreamUiState.Success -> {
                            AdvisorResponseCard(
                                question = advisorStreamState.question,
                                answer = advisorStreamState.text,
                                isStreaming = false
                            )
                        }
                        is AdvisorStreamUiState.Error -> {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text(
                                    text = advisorStreamState.message,
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
}

@Composable
private fun AiScannerHeroBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(142.dp)
            .clip(MaterialTheme.shapes.large)
            .border(
                1.dp,
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
                MaterialTheme.shapes.large
            )
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_ai_scanner_banner_1795527564707),
            contentDescription = stringResource(R.string.cd_ai_banner),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                            Color.Transparent
                        )
                    )
                )
        )
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "GEMINI MULTIMODAL HAZARD LAB",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "AI วิเคราะห์ภาพถ่ายและแผนรับมือ",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "ประเมินระดับความเสี่ยงและวิธีเอาตัวรอดในเสี้ยววินาที",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.84f)
            )
        }
    }
}

@Composable
private fun AdvisorResponseCard(
    question: String,
    answer: String,
    isStreaming: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isStreaming) "AI กำลังพิมพ์คำแนะนำ..." else "คำแนะนำจาก AI",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (isStreaming) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }
            Text(
                text = "ประเด็น: $question",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = answer,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
