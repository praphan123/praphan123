package com.example.data.repository

import android.graphics.Bitmap
import com.example.domain.model.AiHazardAnalysis
import com.example.domain.model.DisasterEvent
import com.example.domain.model.HighRiskZone
import com.example.domain.model.WeatherHazardSnapshot
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

class AiDisasterAnalyzer {

    private val hazardAnalysisSchema = Schema.obj(
        properties = mapOf(
            "hazardType" to Schema.string(
                description = "ประเภทภัยพิบัติเป็นภาษาไทยสั้นๆ เช่น น้ำท่วมฉับพลัน, รอยร้าวโครงสร้าง, พายุรุนแรง, แผ่นดินไหว"
            ),
            "severityScore" to Schema.integer(
                description = "ระดับความรุนแรงตั้งแต่ 1 (ต่ำมาก) ถึง 5 (วิกฤต)"
            ),
            "riskLevel" to Schema.string(
                description = "ระดับความเสี่ยง: ต่ำ, ปานกลาง, สูง, หรือ วิกฤต"
            ),
            "summary" to Schema.string(
                description = "สรุปผลการวิเคราะห์สถานการณ์ภัยพิบัติอย่างกระชับและชัดเจนเป็นภาษาไทย"
            ),
            "keyObservations" to Schema.array(
                items = Schema.string(),
                description = "รายการจุดสังเกตสำคัญที่พบจากภาพหรือข้อมูลตัวเลข (2-4 ข้อ)"
            ),
            "secondaryRisks" to Schema.array(
                items = Schema.string(),
                description = "ความเสี่ยงต่อเนื่องที่อาจเกิดขึ้นตามมา (2-3 ข้อ)"
            ),
            "immediateActions" to Schema.array(
                items = Schema.string(),
                description = "ขั้นตอนการปฏิบัติตัวเพื่อความปลอดภัยเร่งด่วน (3-4 ข้อ)"
            ),
            "shouldEvacuate" to Schema.boolean(
                description = "true หากประเมินว่าควรเตรียมพร้อมหรือดำเนินการอพยพออกจากพื้นที่ทันที"
            )
        )
    )

    private val structuredModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = hazardAnalysisSchema
                temperature = 0.2f
            },
            systemInstruction = content {
                text(
                    "คุณคือผู้เชี่ยวชาญด้านการวิเคราะห์ภัยพิบัติ วิศวกรรมความปลอดภัย และแผนรับมือเหตุฉุกเฉินในประเทศไทย " +
                        "ให้ประเมินความเสี่ยงตามหลักวิชาการอย่างตรงไปตรงมา รอบคอบ ไม่สร้างความตื่นตระหนกเกินจริง แต่เน้นความปลอดภัยสูงสุดของชีวิต " +
                        "ตอบเป็นภาษาไทยที่เข้าใจง่าย กระชับ และนำไปปฏิบัติได้จริงทันที"
                )
            }
        )
    }

    private val advisorStreamModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.35f
            },
            systemInstruction = content {
                text(
                    "คุณคือที่ปรึกษา AI ด้านการรับมือภัยพิบัติและสถานการณ์ฉุกเฉินในประเทศไทย " +
                        "ตอบคำถามเป็นภาษาไทยแบบแบ่งหัวข้อชัดเจน กระชับ อ่านง่ายในภาวะเร่งด่วน " +
                        "หากเป็นเหตุฉุกเฉินรุนแรง ให้ย้ำเบอร์สายด่วนไทย เช่น 1784 (ปภ.) หรือ 1669 (กู้ชีพ)"
                )
            }
        )
    }

    suspend fun analyzePhotoAndContext(
        bitmap: Bitmap,
        userNote: String
    ): AiHazardAnalysis = withTimeout(60_000L) {
        val scaledBitmap = scaleBitmapSafely(bitmap, maxDimension = 1024)
        val promptText = buildString {
            append("กรุณาวิเคราะห์ภาพถ่ายสถานการณ์หรือสภาพแวดล้อมนี้เพื่อประเมินความเสี่ยงภัยพิบัติ")
            if (userNote.isNotBlank()) {
                append("\nข้อมูลเพิ่มเติมจากผู้ใช้: $userNote")
            }
            append("\nระบุประเภทภัย ระดับความรุนแรง 1-5 สิ่งที่สังเกตเห็น ความเสี่ยงแฝง และวิธีปฏิบัติตัวที่ปลอดภัย")
        }

        val response = structuredModel.generateContent(
            content {
                image(scaledBitmap)
                text(promptText)
            }
        )

        val jsonText = response.text
            ?: throw IllegalStateException("AI ไม่สามารถส่งคืนผลการวิเคราะห์ได้ในขณะนี้")
        parseAnalysisJson(jsonText)
    }

    suspend fun analyzeDisasterEvent(
        event: DisasterEvent,
        localWeather: WeatherHazardSnapshot?
    ): AiHazardAnalysis = withTimeout(60_000L) {
        val prompt = buildString {
            appendLine("วิเคราะห์เหตุการณ์ภัยพิบัติแบบเรียลไทม์ต่อไปนี้:")
            appendLine("- หัวข้อ: ${event.title}")
            appendLine("- ประเภท: ${event.category.labelTh}")
            appendLine("- แหล่งข้อมูล: ${event.source}")
            appendLine("- พื้นที่/พิกัด: ${event.place} (${event.latitude}, ${event.longitude})")
            event.magnitude?.let { appendLine("- ขนาด (Magnitude): M${"%.1f".format(it)}") }
            event.depthKm?.let { appendLine("- ความลึก: ${"%.1f".format(it)} กม.") }
            if (event.tsunamiAlert) {
                appendLine("- หมายเหตุ: ระบบแจ้งเตือนมีสัญญาณเฝ้าระวังสึนามิ")
            }
            localWeather?.let {
                appendLine("บริบทสภาพอากาศพื้นที่ผู้ใช้ (${it.locationName}): อุณหภูมิ ${it.temperatureC}°C, ฝน ${it.precipitationMm} มม., ลมกระโชก ${it.windGustsKmh} กม./ชม.")
            }
            appendLine("ประเมินผลกระทบที่อาจเกิดขึ้น ความเสี่ยงอาฟเตอร์ช็อก/ภัยต่อเนื่อง และแนวทางเฝ้าระวังที่เหมาะสม")
        }

        val response = structuredModel.generateContent(prompt)
        val jsonText = response.text
            ?: throw IllegalStateException("AI ไม่สามารถส่งคืนผลการวิเคราะห์เหตุการณ์ได้")
        parseAnalysisJson(jsonText)
    }

    suspend fun analyzeLocalWeather(
        weather: WeatherHazardSnapshot
    ): AiHazardAnalysis = withTimeout(60_000L) {
        val prompt = buildString {
            appendLine("วิเคราะห์ความเสี่ยงภัยพิบัติทางสภาพอากาศและอุทกภัยจากค่าตรวจวัดล่าสุด:")
            appendLine("- พื้นที่: ${weather.locationName} (${"%.3f".format(weather.latitude)}, ${"%.3f".format(weather.longitude)})")
            appendLine("- สภาพอากาศ: ${weather.weatherConditionTh}")
            appendLine("- อุณหภูมิ: ${weather.temperatureC} °C | ความชื้น: ${weather.humidityPercent}%")
            appendLine("- ปริมาณฝน/น้ำฝน: ${weather.precipitationMm} มม./ชม.")
            appendLine("- ความเร็วลม: ${weather.windSpeedKmh} กม./ชม. | ลมกระโชกสูงสุด: ${weather.windGustsKmh} กม./ชม.")
            appendLine("- ความกดอากาศ: ${weather.pressureHpa} hPa")
            appendLine("วิเคราะห์โอกาสเกิดน้ำท่วมฉับพลัน พายุลมแรง หรือภัยความร้อน และแนะนำการเตรียมพร้อม")
        }

        val response = structuredModel.generateContent(prompt)
        val jsonText = response.text
            ?: throw IllegalStateException("AI ไม่สามารถวิเคราะห์สภาพอากาศได้")
        parseAnalysisJson(jsonText)
    }

    suspend fun analyzeHighRiskZone(
        zone: HighRiskZone
    ): AiHazardAnalysis = withTimeout(60_000L) {
        val prompt = buildString {
            appendLine("วิเคราะห์พื้นที่เสี่ยงภัยพิบัติสูงต่อไปนี้โดยบูรณาการปัจจัย 3 ด้าน (ภูมิศาสตร์ ภูมิอากาศ และโครงสร้างพื้นฐาน):")
            appendLine("- พื้นที่: ${zone.nameTh} (${zone.regionTh})")
            appendLine("- พิกัดศูนย์กลาง: ${"%.3f".format(zone.latitude)}, ${"%.3f".format(zone.longitude)} (รัศมีประมาณ ${zone.radiusKm.toInt()} กม.)")
            appendLine("- ภัยพิบัติหลัก: ${zone.primaryHazards.joinToString(", ") { it.labelTh }}")
            appendLine("- ปัจจัยด้านภูมิศาสตร์: ${zone.geographyFactor}")
            appendLine("- ปัจจัยด้านภูมิอากาศ: ${zone.climateFactor}")
            zone.liveClimateSummary?.let { appendLine("- ข้อมูลตรวจวัดอากาศปัจจุบัน: $it") }
            appendLine("- ปัจจัยด้านโครงสร้างพื้นฐาน: ${zone.infrastructureFactor}")
            appendLine("สรุปความเสี่ยงรวม จุดเปราะบางสำคัญ และแนวทางป้องกัน/รับมือสำหรับประชาชนและหน่วยงานในพื้นที่นี้")
        }

        val response = structuredModel.generateContent(prompt)
        val jsonText = response.text
            ?: throw IllegalStateException("AI ไม่สามารถวิเคราะห์พื้นที่เสี่ยงได้ในขณะนี้")
        parseAnalysisJson(jsonText)
    }

    fun streamEmergencyAdvice(
        question: String,
        contextSummary: String
    ): Flow<String> {
        val fullPrompt = buildString {
            if (contextSummary.isNotBlank()) {
                appendLine("บริบทข้อมูลเฝ้าระวังปัจจุบันในแอป:")
                appendLine(contextSummary)
                appendLine()
            }
            append("คำถามจากผู้ใช้: $question")
        }
        return advisorStreamModel.generateContentStream(fullPrompt)
            .map { chunk -> chunk.text.orEmpty() }
    }

    private fun parseAnalysisJson(jsonString: String): AiHazardAnalysis {
        val json = JSONObject(jsonString)
        val observations = json.optJSONArray("keyObservations")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
        }.orEmpty()
        val secondary = json.optJSONArray("secondaryRisks")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
        }.orEmpty()
        val actions = json.optJSONArray("immediateActions")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
        }.orEmpty()

        return AiHazardAnalysis(
            hazardType = json.optString("hazardType", "การประเมินความเสี่ยง"),
            severityScore = json.optInt("severityScore", 2).coerceIn(1, 5),
            riskLevel = json.optString("riskLevel", "ปานกลาง"),
            summary = json.optString("summary", ""),
            keyObservations = observations,
            secondaryRisks = secondary,
            immediateActions = actions,
            shouldEvacuate = json.optBoolean("shouldEvacuate", false)
        )
    }

    private fun scaleBitmapSafely(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap
        val ratio = width.toFloat() / height.toFloat()
        val targetW: Int
        val targetH: Int
        if (width > height) {
            targetW = maxDimension
            targetH = (maxDimension / ratio).toInt().coerceAtLeast(1)
        } else {
            targetH = maxDimension
            targetW = (maxDimension * ratio).toInt().coerceAtLeast(1)
        }
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }
}
