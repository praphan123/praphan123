# DisasterPulse FCM Push Alert Server

เซิร์ฟเวอร์ส่งการแจ้งเตือนแบบเรียลไทม์ผ่าน **Firebase Cloud Messaging (HTTP v1 / `firebase-admin`)** สำหรับโปรเจกต์ `gen-lang-client-0386658613`

## ความสามารถหลัก
1. **ตรวจจับภัยพิบัติอัตโนมัติ (`pollDisastersAndNotify`)**: ดึงข้อมูลแผ่นดินไหว USGS (M4.0+) และ NASA EONET v3 ทุกๆ 5 นาที แล้วส่ง Push Alert ไปยัง Topic เชิงพื้นที่ (`disaster_grid_*`, `disaster_zone_*`, `disaster_th_all`) และอุปกรณ์ที่ลงทะเบียนอยู่ในรัศมี
2. **REST API สำหรับแอป Android**:
   - `GET /health` — ตรวจสอบสถานะเซิร์ฟเวอร์
   - `POST /api/register-location` — บันทึก `fcmToken`, พิกัด (`latitude`, `longitude`) และรัศมี (`radiusKm`)
   - `POST /api/send-test-alert` — ส่งการแจ้งเตือนทดสอบไปยังอุปกรณ์ทันที
   - `POST /api/dispatch-alert` — ส่งแจ้งเตือนกำหนดเองตามพิกัด
   - `POST /api/poll-now` — สั่งตรวจสอบและกระจายข่าวภัยพิบัติล่าสุดทันที

## วิธีรันหรือ Deploy

### วิธีที่ 1: Deploy ขึ้น Google Cloud Run (แนะนำ)
```bash
cd fcm-server
gcloud run deploy disasterpulse-fcm-server \
  --source . \
  --project gen-lang-client-0386658613 \
  --region asia-southeast1 \
  --allow-unauthenticated \
  --set-env-vars FIREBASE_PROJECT_ID=gen-lang-client-0386658613
```
จากนั้นนำ URL ที่ได้ (เช่น `https://disasterpulse-fcm-server-xxx.a.run.app`) ไปกรอกในหน้า **ศูนย์แจ้งเตือนภัยเรียลไทม์ (FCM)** ในแอป Android

### วิธีที่ 2: รันบนเครื่อง/VPS ด้วย Node.js 20+
1. ดาวน์โหลด Service Account Key จาก Firebase Console > **Project Settings > Service accounts > Generate new private key** แล้วบันทึกเป็น `fcm-server/service-account.json`
2. คัดลอก `.env.example` เป็น `.env` และติดตั้งแพ็กเกจ:
```bash
cd fcm-server
npm install
GOOGLE_APPLICATION_CREDENTIALS=./service-account.json npm start
```
