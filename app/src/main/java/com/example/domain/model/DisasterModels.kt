package com.example.domain.model

enum class DisasterCategory(val labelTh: String) {
    EARTHQUAKE("แผ่นดินไหว"),
    FLOOD("อุทกภัย/น้ำท่วม"),
    STORM("พายุรุนแรง"),
    WILDFIRE("ไฟป่า/หมอกควัน"),
    VOLCANO("ภูเขาไฟปะทุ"),
    LANDSLIDE("ดินถล่ม"),
    OTHER("ภัยธรรมชาติอื่นๆ")
}

enum class FeedFilter(val labelTh: String) {
    ALL("ทั้งหมด"),
    SEA_THAILAND("ไทยและอาเซียน"),
    EARTHQUAKE("แผ่นดินไหว"),
    STORM_FLOOD("พายุ/น้ำท่วม"),
    WILDFIRE("ไฟป่า")
}

enum class MapTimePeriod(val labelTh: String, val days: Int, val includesHistorical: Boolean) {
    LAST_24_HOURS("24 ชม.", 1, false),
    LAST_7_DAYS("7 วัน", 7, false),
    LAST_30_DAYS("30 วัน", 30, true),
    PAST_YEAR("1 ปี", 365, true)
}

enum class MapDisasterTypeFilter(val labelTh: String) {
    ALL("ทั้งหมด"),
    EARTHQUAKE("แผ่นดินไหว"),
    FLOOD("น้ำท่วม"),
    STORM("พายุ"),
    WILDFIRE("ไฟป่า"),
    VOLCANO_LANDSLIDE("ภูเขาไฟ/ดินถล่ม")
}

data class DisasterEvent(
    val id: String,
    val title: String,
    val category: DisasterCategory,
    val severityScore: Int, // 1..5
    val magnitude: Double?,
    val depthKm: Double?,
    val latitude: Double,
    val longitude: Double,
    val place: String,
    val timestampMs: Long,
    val source: String,
    val externalUrl: String?,
    val tsunamiAlert: Boolean = false,
    val isHistorical: Boolean = false
)

data class HighRiskZone(
    val id: String,
    val nameTh: String,
    val regionTh: String,
    val latitude: Double,
    val longitude: Double,
    val radiusKm: Double,
    val compositeRiskScore: Int, // 1..5
    val primaryHazards: List<DisasterCategory>,
    val geographyFactor: String,
    val climateFactor: String,
    val infrastructureFactor: String,
    val liveClimateSummary: String? = null
)

val DEFAULT_HIGH_RISK_ZONES = listOf(
    HighRiskZone(
        id = "zone_chao_phraya",
        nameTh = "ลุ่มน้ำเจ้าพระยาตอนล่าง (กทม.-ปริมณฑล)",
        regionTh = "ภาคกลาง",
        latitude = 13.7563,
        longitude = 100.5018,
        radiusKm = 75.0,
        compositeRiskScore = 4,
        primaryHazards = listOf(DisasterCategory.FLOOD, DisasterCategory.STORM),
        geographyFactor = "พื้นที่ราบลุ่มต่ำปากแม่น้ำ สูงกว่าระดับน้ำทะเลเฉลี่ยเพียง 1–2 เมตร และมีอัตราการทรุดตัวของชั้นดิน",
        climateFactor = "มรสุมตะวันตกเฉียงใต้และฝนตกหนักสะสมจากลุ่มน้ำเหนือร่วมกับภาวะน้ำทะเลหนุนสูง",
        infrastructureFactor = "ความหนาแน่นของโครงข่ายคมนาคม ระบบไฟฟ้าใต้ดิน โรงพยาบาล และเขตเศรษฐกิจหลัก"
    ),
    HighRiskZone(
        id = "zone_andaman",
        nameTh = "แนวชายฝั่งอันดามัน (ภูเก็ต-พังงา-กระบี่)",
        regionTh = "ภาคใต้ฝั่งตะวันตก",
        latitude = 8.0500,
        longitude = 98.3500,
        radiusKm = 95.0,
        compositeRiskScore = 4,
        primaryHazards = listOf(DisasterCategory.EARTHQUAKE, DisasterCategory.STORM, DisasterCategory.LANDSLIDE),
        geographyFactor = "อยู่ใกล้แนวรอยเลื่อนสะกาย-ร่องลึกสุมาตรา และมีลักษณะชายฝั่งอ่าวเปิดรับคลื่น",
        climateFactor = "ลมมรสุมตะวันตกเฉียงใต้กำลังแรง คลื่นซัดฝั่ง และฝนตกหนักบนลาดเชิงเขา",
        infrastructureFactor = "ท่าเรือท่องเที่ยว โรงแรมชายฝั่ง และถนนเลียบไหล่เขาที่มีความเสี่ยงดินสไลด์"
    ),
    HighRiskZone(
        id = "zone_north_fault",
        nameTh = "แอ่งเชียงใหม่-เชียงราย (รอยเลื่อนแม่จัน-พะเยา)",
        regionTh = "ภาคเหนือ",
        latitude = 19.3500,
        longitude = 99.4500,
        radiusKm = 110.0,
        compositeRiskScore = 4,
        primaryHazards = listOf(DisasterCategory.EARTHQUAKE, DisasterCategory.LANDSLIDE, DisasterCategory.WILDFIRE),
        geographyFactor = "แนวรอยเลื่อนมีพลัง (แม่จัน, พะเยา, แม่ทา) และภูมิประเทศแอ่งหุบเขาล้อมรอบ",
        climateFactor = "ภาวะความกดอากาศกักฝุ่น PM2.5 ฤดูแล้ง และฝนตกหนักทำให้เกิดน้ำป่าไหลหลาก",
        infrastructureFactor = "เส้นทางคมนาคมผ่านหุบเขา สะพาน อ่างเก็บน้ำ และอาคารประวัติศาสตร์"
    ),
    HighRiskZone(
        id = "zone_lower_south",
        nameTh = "ลุ่มน้ำทะเลสาบสงขลา-หาดใหญ่",
        regionTh = "ภาคใต้ฝั่งอ่าวไทย",
        latitude = 7.0500,
        longitude = 100.4700,
        radiusKm = 80.0,
        compositeRiskScore = 4,
        primaryHazards = listOf(DisasterCategory.FLOOD, DisasterCategory.STORM),
        geographyFactor = "แอ่งรับน้ำจากเทือกเขาสันกาลาคีรีและบรรทัดก่อนระบายออกสู่อ่าวไทย",
        climateFactor = "มรสุมตะวันออกเฉียงเหนือปะทะชายฝั่งทำให้เกิดฝนแช่ปริมาณสะสมสูง",
        infrastructureFactor = "ศูนย์กลางพาณิชยกรรมภาคใต้ สถานีรถไฟชุมทาง และระบบระบายน้ำเขตเมือง"
    ),
    HighRiskZone(
        id = "zone_eastern_corridor",
        nameTh = "เขตอุตสาหกรรมชายฝั่งตะวันออก (ชลบุรี-ระยอง)",
        regionTh = "ภาคตะวันออก",
        latitude = 12.9500,
        longitude = 101.1000,
        radiusKm = 70.0,
        compositeRiskScore = 3,
        primaryHazards = listOf(DisasterCategory.STORM, DisasterCategory.FLOOD),
        geographyFactor = "ที่ราบชายฝั่งและทางลาดจากทิวเขาจันทบุรีลงสู่อ่าวไทย",
        climateFactor = "แนวพายุโซนร้อนและร่องมรสุมพาดผ่านช่วงปลายฤดูฝน",
        infrastructureFactor = "นิคมอุตสาหกรรมปิโตรเคมี ท่าเรือน้ำลึก และโครงข่ายท่อส่งพลังงาน"
    ),
    HighRiskZone(
        id = "zone_northeast_basin",
        nameTh = "ลุ่มน้ำชี-มูล (ขอนแก่น-อุบลราชธานี)",
        regionTh = "ภาคตะวันออกเฉียงเหนือ",
        latitude = 15.8500,
        longitude = 103.5000,
        radiusKm = 135.0,
        compositeRiskScore = 3,
        primaryHazards = listOf(DisasterCategory.FLOOD, DisasterCategory.WILDFIRE),
        geographyFactor = "ที่ราบสูงสลับแอ่งกะทะขนาดใหญ่ที่น้ำไหลหลากและระบายลงแม่น้ำโขงช้า",
        climateFactor = "ความแปรปรวนรุนแรงระหว่างคลื่นความร้อนจัดในฤดูแล้งและพายุฤดูฝน",
        infrastructureFactor = "พื้นที่เกษตรกรรมขนาดใหญ่ อ่างเก็บน้ำ และทางหลวงสายหลักเชื่อมภูมิภาค"
    ),
    HighRiskZone(
        id = "zone_sagaing_fault",
        nameTh = "แนวรอยเลื่อนสะกาย (เมียนมา-รอยต่อภาคเหนือ)",
        regionTh = "ภูมิภาคเอเชียตะวันออกเฉียงใต้",
        latitude = 20.5000,
        longitude = 96.1000,
        radiusKm = 190.0,
        compositeRiskScore = 5,
        primaryHazards = listOf(DisasterCategory.EARTHQUAKE),
        geographyFactor = "แนวรอยเลื่อนแผ่นเปลือกโลกอินเดีย-ยูเรเชียที่เคลื่อนตัวในแนวระนาบอย่างต่อเนื่อง",
        climateFactor = "ฝนตกหนักในฤดูมรสุมเพิ่มโอกาสเกิดดินถล่มซ้ำหลังแผ่นดินไหว",
        infrastructureFactor = "เขื่อนกักเก็บน้ำ เส้นทางขนส่งข้ามพรมแดน และอาคารสูงในรัศมีคลื่นไหวสะเทือน"
    ),
    HighRiskZone(
        id = "zone_sumatra_arc",
        nameTh = "แนวร่องลึกมุดตัวสุมาตรา-อันดามัน",
        regionTh = "มหาสมุทรอินเดียตะวันออก",
        latitude = 4.2000,
        longitude = 95.3000,
        radiusKm = 240.0,
        compositeRiskScore = 5,
        primaryHazards = listOf(DisasterCategory.EARTHQUAKE, DisasterCategory.VOLCANO),
        geographyFactor = "แนวมุดตัวของแผ่นเปลือกโลกอินโด-ออสเตรเลียใต้แผ่นยูเรเชีย แหล่งกำเนิดแผ่นดินไหวใหญ่และสึนามิ",
        climateFactor = "เขตกำเนิดพายุไซโคลนและความชื้นสูงในมหาสมุทรอินเดีย",
        infrastructureFactor = "เส้นทางเดินเรือพาณิชย์ระหว่างประเทศ สายเคเบิลใต้น้ำ และเมืองท่าชายฝั่งอันดามัน"
    )
)

data class WeatherHazardSnapshot(
    val locationName: String,
    val latitude: Double,
    val longitude: Double,
    val temperatureC: Double,
    val humidityPercent: Int,
    val windSpeedKmh: Double,
    val windGustsKmh: Double,
    val precipitationMm: Double,
    val pressureHpa: Double,
    val weatherCode: Int,
    val weatherConditionTh: String,
    val riskScore: Int, // 1..5
    val riskLabelTh: String,
    val riskReasonTh: String,
    val observedAtIso: String
)

data class ThaiProvincePreset(
    val id: String,
    val nameTh: String,
    val regionTh: String,
    val latitude: Double,
    val longitude: Double
)

val THAI_PROVINCE_PRESETS = listOf(
    ThaiProvincePreset("bkk", "กรุงเทพมหานคร", "ภาคกลาง", 13.7563, 100.5018),
    ThaiProvincePreset("cnx", "เชียงใหม่", "ภาคเหนือ", 18.7883, 98.9853),
    ThaiProvincePreset("cei", "เชียงราย", "ภาคเหนือ", 19.9105, 99.8406),
    ThaiProvincePreset("hkt", "ภูเก็ต", "ภาคใต้", 7.8804, 98.3923),
    ThaiProvincePreset("hdy", "หาดใหญ่ (สงขลา)", "ภาคใต้", 7.0086, 100.4747),
    ThaiProvincePreset("kkc", "ขอนแก่น", "ภาคตะวันออกเฉียงเหนือ", 16.4322, 102.8236),
    ThaiProvincePreset("cbi", "ชลบุรี", "ภาคตะวันออก", 13.3611, 100.9847),
    ThaiProvincePreset("urt", "สุราษฎร์ธานี", "ภาคใต้", 9.1382, 99.3217)
)

data class AiHazardAnalysis(
    val hazardType: String,
    val severityScore: Int, // 1..5
    val riskLevel: String,
    val summary: String,
    val keyObservations: List<String>,
    val secondaryRisks: List<String>,
    val immediateActions: List<String>,
    val shouldEvacuate: Boolean
)

data class EmergencyKitItem(
    val id: String,
    val categoryTh: String,
    val titleTh: String,
    val detailTh: String
)

val DEFAULT_EMERGENCY_KIT_ITEMS = listOf(
    EmergencyKitItem("water_3days", "น้ำและอาหาร", "น้ำดื่มสะอาด (3 ลิตร/คน/วัน)", "สำรองไว้อย่างน้อย 3 วัน เพื่อป้องกันระบบประปาขัดข้อง"),
    EmergencyKitItem("dry_food", "น้ำและอาหาร", "อาหารแห้ง / อาหารกระป๋องพร้อมทาน", "เลือกอาหารที่ไม่ต้องปรุงสุกและมีที่เปิดกระป๋อง"),
    EmergencyKitItem("first_aid", "ยาและปฐมพยาบาล", "ชุดปฐมพยาบาลและยาประจำตัว", "ผ้าพันแผล ยาฆ่าเชื้อ ยาแก้ปวด และยาโรคประจำตัว 7 วัน"),
    EmergencyKitItem("n95_mask", "ยาและปฐมพยาบาล", "หน้ากาก N95 และเจลแอลกอฮอล์", "ป้องกันฝุ่นควันไฟป่า PM2.5 หรือฝุ่นซากปรักหักพัง"),
    EmergencyKitItem("flashlight", "อุปกรณ์ฉุกเฉิน", "ไฟฉาย LED พร้อมถ่านสำรอง", "ใช้ส่งสัญญาณขอความช่วยเหลือเมื่อไฟฟ้าดับ"),
    EmergencyKitItem("power_bank", "อุปกรณ์ฉุกเฉิน", "พาวเวอร์แบงก์ชาร์จเต็ม + สายชาร์จ", "รักษาการติดต่อสื่อสารและติดตามประกาศเตือนภัย"),
    EmergencyKitItem("whistle", "อุปกรณ์ฉุกเฉิน", "นกหวีดฉุกเฉิน", "เป่าขอความช่วยเหลือได้ไกลกว่าการตะโกนและประหยัดแรง"),
    EmergencyKitItem("docs_bag", "เอกสารและเงินสด", "สำเนาบัตรประชาชน/ทะเบียนบ้านใส่ซองกันน้ำ", "รวมถึงสำเนาเอกสารสำคัญและเงินสดสำรองสำหรับกรณีระบบล่ม"),
    EmergencyKitItem("raincoat", "เครื่องนุ่งห่ม", "เสื้อกันฝน / ผ้าห่มฉุกเฉิน", "ช่วยรักษาอุณหภูมิร่างกายระหว่างอพยพ")
)

data class ThaiEmergencyHotline(
    val number: String,
    val agencyTh: String,
    val descriptionTh: String
)

val THAI_EMERGENCY_HOTLINES = listOf(
    ThaiEmergencyHotline("1784", "ปภ. สายด่วนนิรภัย", "กรมป้องกันและบรรเทาสาธารณภัย แจ้งเหตุภัยพิบัติ 24 ชม."),
    ThaiEmergencyHotline("1669", "สถาบันการแพทย์ฉุกเฉิน", "เจ็บป่วยฉุกเฉิน อุบัติเหตุ กู้ชีพเร่งด่วนทั่วประเทศ"),
    ThaiEmergencyHotline("191", "เหตุด่วนเหตุร้าย (ตำรวจ)", "ประสานงานกู้ภัย เหตุฉุกเฉินและอาชญากรรม"),
    ThaiEmergencyHotline("199", "สายด่วนดับเพลิงและกู้ภัย", "แจ้งเหตุเพลิงไหม้ สัตว์มีพิษ อาคารถล่ม"),
    ThaiEmergencyHotline("1182", "กรมอุตุนิยมวิทยา", "สอบถามพยากรณ์อากาศ พายุ และประกาศเตือนภัยธรรมชาติ")
)
