package com.example.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.domain.model.PushAlertConfig
import com.example.domain.model.PushAlertRecord
import com.example.notifications.DisasterNotificationHelper
import com.example.ui.theme.LocalHazardColors
import com.example.ui.viewmodel.FcmSyncUiState
import com.google.android.gms.location.LocationServices
import kotlin.math.roundToInt

private val ALERT_RADIUS_OPTIONS_KM = listOf(50.0, 100.0, 150.0, 300.0)

@Composable
fun PushAlertStatusCard(
    config: PushAlertConfig,
    unreadCount: Int,
    onOpenCenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hazard = LocalHazardColors.current
    val context = LocalContext.current
    val osAllowed = DisasterNotificationHelper.areNotificationsEnabled(context)
    val active = config.enabled && osAllowed

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (active) {
                hazard.telemetryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (active) hazard.telemetry.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                MaterialTheme.shapes.large
            )
            .clip(MaterialTheme.shapes.large)
            .clickable { onOpenCenter() }
            .testTag("fcm_alert_status_card")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) hazard.telemetry.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
            ) {
                Icon(
                    imageVector = if (active) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
                    contentDescription = stringResource(R.string.cd_push_alerts),
                    tint = if (active) hazard.telemetry else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (active) "แจ้งเตือนพุช FCM เปิดใช้งาน" else "เปิดรับแจ้งเตือนภัยใกล้คุณ (FCM)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (unreadCount > 0) {
                        Surface(
                            color = hazard.critical,
                            shape = RoundedCornerShape(50.dp)
                        ) {
                            Text(
                                text = "$unreadCount ใหม่",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = if (active) {
                        "พื้นที่: ${config.locationLabel} • รัศมี ${config.radiusKm.roundToInt()} กม. • ${config.subscribedTopics.size} หัวข้อ"
                    } else {
                        "รับการแจ้งเตือนทันทีเมื่อเกิดภัยพิบัติใกล้ตำแหน่งของคุณ"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = if (active) "ตั้งค่า" else "เปิดใช้",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PushAlertCenterSheet(
    config: PushAlertConfig,
    alerts: List<PushAlertRecord>,
    fcmSyncState: FcmSyncUiState,
    onEnableAlerts: () -> Unit,
    onDisableAlerts: () -> Unit,
    onUpdateLocation: (String, Double, Double) -> Unit,
    onUpdateRadius: (Double) -> Unit,
    onRefreshToken: () -> Unit,
    onSaveServerUrl: (String) -> Unit,
    onSendTestServerPush: (String) -> Unit,
    onClearAlerts: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val hazard = LocalHazardColors.current
    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var permissionError by remember { mutableStateOf<String?>(null) }
    var copiedToken by remember { mutableStateOf(false) }
    var serverUrlInput by remember(config.fcmServerUrl) { mutableStateOf(config.fcmServerUrl) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionError = null
            onEnableAlerts()
        } else {
            permissionError = "ไม่ได้รับสิทธิ์แจ้งเตือนบนอุปกรณ์ กรุณาอนุญาตในการตั้งค่าระบบ"
        }
    }

    @SuppressLint("MissingPermission")
    fun fetchGpsAndSubscribe() {
        fusedClient.lastLocation
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    permissionError = null
                    onUpdateLocation("ตำแหน่ง GPS ปัจจุบัน", loc.latitude, loc.longitude)
                } else {
                    permissionError = "ยังไม่มีพิกัด GPS ล่าสุดบนอุปกรณ์ ใช้พื้นที่จังหวัดที่เลือกแทน"
                }
            }
            .addOnFailureListener {
                permissionError = "ไม่สามารถอ่านตำแหน่ง GPS ได้"
            }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            fetchGpsAndSubscribe()
        } else {
            permissionError = "ไม่ได้รับสิทธิ์เข้าถึงตำแหน่ง สามารถใช้พื้นที่จังหวัดที่เลือกได้"
        }
    }

    fun requestEnableFlow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                onEnableAlerts()
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            onEnableAlerts()
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ศูนย์แจ้งเตือนภัยเรียลไทม์ (FCM)",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "แจ้งเตือนทันทีผ่าน Firebase Cloud Messaging เมื่อพบเหตุใกล้คุณ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = config.enabled,
                    onCheckedChange = { checked ->
                        if (checked) requestEnableFlow() else onDisableAlerts()
                    },
                    modifier = Modifier.testTag("fcm_enable_switch")
                )
            }

            permissionError?.let { msg ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                    )
                }
            }

            when (fcmSyncState) {
                FcmSyncUiState.Syncing -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            text = "กำลังลงทะเบียน FCM Token และหัวข้อพื้นที่...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                is FcmSyncUiState.Synced -> {
                    Surface(
                        color = hazard.lowContainer.copy(alpha = 0.7f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = fcmSyncState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = hazard.low,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp)
                        )
                    }
                }
                is FcmSyncUiState.Error -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = fcmSyncState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp)
                        )
                    }
                }
                FcmSyncUiState.Idle -> Unit
            }

            // Location & Radius Configuration
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "พื้นที่เป้าหมายการแจ้งเตือน",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = config.locationLabel,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${"%.3f".format(config.latitude)}, ${"%.3f".format(config.longitude)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                val hasLocation = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasLocation) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        fetchGpsAndSubscribe()
                                    }
                                } else {
                                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                }
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("fcm_update_gps_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MyLocation,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("อัปเดต GPS")
                        }
                    }

                    Text(
                        text = "รัศมีแจ้งเตือนภัยพิบัติรอบตัวคุณ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ALERT_RADIUS_OPTIONS_KM.forEach { radius ->
                            FilterChip(
                                selected = config.radiusKm.roundToInt() == radius.roundToInt(),
                                onClick = { onUpdateRadius(radius) },
                                label = { Text("${radius.roundToInt()} กม.") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("fcm_radius_${radius.roundToInt()}")
                            )
                        }
                    }
                }
            }

            // FCM Token & Subscribed Topics Diagnostics
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    MaterialTheme.shapes.medium
                )
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "สถานะหัวข้อ FCM และ Device Token",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = onRefreshToken,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("fcm_refresh_token_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "ซิงค์ FCM Token",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (config.subscribedTopics.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            config.subscribedTopics.forEach { topic ->
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "#$topic",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "ยังไม่ได้สมัครรับหัวข้อพื้นที่ (เปิดสวิตช์ด้านบนเพื่อสมัคร)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val token = config.fcmToken
                    if (!token.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = token,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    clipboard?.setPrimaryClip(ClipData.newPlainText("FCM Token", token))
                                    copiedToken = true
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("fcm_copy_token_button")
                            ) {
                                Icon(
                                    imageVector = if (copiedToken) Icons.Filled.Check else Icons.Filled.ContentCopy,
                                    contentDescription = "คัดลอก FCM Token",
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (copiedToken) "คัดลอกแล้ว" else "Token")
                            }
                        }
                    } else if (!config.enabled) {
                        Button(
                            onClick = { requestEnableFlow() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("fcm_activate_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("เปิดใช้งานแจ้งเตือนภัยพิบัติเรียลไทม์ (FCM)")
                        }
                    }
                }
            }

            // Optional Backend FCM Server Endpoint (fcm-server/server.js)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    MaterialTheme.shapes.medium
                )
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Text(
                        text = "เชื่อมต่อเซิร์ฟเวอร์ FCM (fcm-server)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "ระบุ URL ของเซิร์ฟเวอร์ Node.js/Cloud Run (โฟลเดอร์ fcm-server) เพื่อให้เซิร์ฟเวอร์ดึงข่าวภัยพิบัติและส่งพุชเข้าพื้นที่อัตโนมัติ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = serverUrlInput,
                        onValueChange = { serverUrlInput = it },
                        singleLine = true,
                        label = { Text("FCM Server URL (เช่น https://...run.app)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("fcm_server_url_input")
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { onSaveServerUrl(serverUrlInput) },
                            enabled = fcmSyncState !is FcmSyncUiState.Syncing,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("fcm_save_server_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("บันทึกและซิงค์")
                        }
                        val validUrl = serverUrlInput.trim().lowercase().let {
                            it.startsWith("https://") || it.startsWith("http://")
                        }
                        Button(
                            onClick = { onSendTestServerPush(serverUrlInput) },
                            enabled = validUrl && fcmSyncState !is FcmSyncUiState.Syncing,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("fcm_test_server_push_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Send,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ทดสอบพุช")
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

            // Received Push Alerts Inbox
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "ประวัติการแจ้งเตือนที่ได้รับ (${alerts.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (alerts.isNotEmpty()) {
                    TextButton(
                        onClick = onClearAlerts,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteSweep,
                            contentDescription = "ล้างประวัติแจ้งเตือน",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ล้างทั้งหมด")
                    }
                }
            }

            if (alerts.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = "ยังไม่มีข้อความแจ้งเตือนพุชในพื้นที่ของคุณ เมื่อ Firebase Cloud Messaging ส่งสัญญาณเหตุการณ์ภายในรัศมีที่ตั้งไว้ ข้อความจะแสดงที่นี่ทันที",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    )
                }
            } else {
                alerts.forEach { alert ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                severityColor(alert.severityScore).copy(alpha = 0.45f),
                                MaterialTheme.shapes.medium
                            )
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SeverityBadge(
                                    score = alert.severityScore,
                                    label = "ระดับ ${alert.severityScore} • ${alert.category.labelTh}"
                                )
                                Text(
                                    text = formatRelativeTimeTh(alert.receivedAtMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = alert.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = alert.body,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val metaLine = buildString {
                                append(alert.source)
                                alert.distanceKm?.let {
                                    append(" • ห่าง ${"%.1f".format(it)} กม.")
                                }
                                append(if (alert.withinImmediateRadius) " (ในรัศมีใกล้คุณ)" else " (นอกรัศมี)")
                            }
                            Text(
                                text = metaLine,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }
}
