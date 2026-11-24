'use strict';

const express = require('express');
const admin = require('firebase-admin');

const PORT = Number(process.env.PORT || 8080);
const PROJECT_ID = process.env.FIREBASE_PROJECT_ID || 'gen-lang-client-0386658613';
const POLL_INTERVAL_MS = Math.max(1, Number(process.env.POLL_INTERVAL_MINUTES || 5)) * 60 * 1000;
const SERVER_API_KEY = process.env.SERVER_API_KEY || '';

// Initialize Firebase Admin SDK using ADC or GOOGLE_APPLICATION_CREDENTIALS
if (!admin.apps.length) {
  admin.initializeApp({
    credential: admin.credential.applicationDefault(),
    projectId: PROJECT_ID
  });
}

const messaging = admin.messaging();
const app = express();
app.use(express.json({ limit: '256kb' }));

// In-memory store (persists active device subscriptions & deduped events per instance;
// replace or mirror to Firestore if running multi-instance Cloud Run)
const deviceRegistry = new Map(); // fcmToken -> { fcmToken, latitude, longitude, locationLabel, radiusKm, topics, updatedAtMs }
const dispatchedEventIds = new Set();

const THAI_PROVINCES = [
  { id: 'bkk', nameTh: 'กรุงเทพมหานคร', latitude: 13.7563, longitude: 100.5018 },
  { id: 'cnx', nameTh: 'เชียงใหม่', latitude: 18.7883, longitude: 98.9853 },
  { id: 'cei', nameTh: 'เชียงราย', latitude: 19.9105, longitude: 99.8406 },
  { id: 'hkt', nameTh: 'ภูเก็ต', latitude: 7.8804, longitude: 98.3923 },
  { id: 'hdy', nameTh: 'หาดใหญ่ (สงขลา)', latitude: 7.0086, longitude: 100.4747 },
  { id: 'kkc', nameTh: 'ขอนแก่น', latitude: 16.4322, longitude: 102.8236 },
  { id: 'cbi', nameTh: 'ชลบุรี', latitude: 13.3611, longitude: 100.9847 },
  { id: 'urt', nameTh: 'สุราษฎร์ธานี', latitude: 9.1382, longitude: 99.3217 }
];

function haversineDistanceKm(lat1, lon1, lat2, lon2) {
  const toRad = (deg) => (deg * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function formatGridCell(lat, lon) {
  const latInt = Math.round(lat);
  const lonInt = Math.round(lon);
  const latPart = latInt < 0 ? `s${Math.abs(latInt)}` : `n${latInt}`;
  const lonPart = lonInt < 0 ? `w${Math.abs(lonInt)}` : `e${lonInt}`;
  return `disaster_grid_${latPart}_${lonPart}`;
}

function buildTargetTopics(latitude, longitude, severityScore) {
  const topics = new Set();
  // 3x3 1-degree spatial neighborhood matching Android GeoAlertUtils.buildLocationTopics
  for (let dLat = -1; dLat <= 1; dLat += 1) {
    for (let dLon = -1; dLon <= 1; dLon += 1) {
      topics.add(formatGridCell(latitude + dLat, longitude + dLon));
    }
  }
  for (const province of THAI_PROVINCES) {
    if (haversineDistanceKm(latitude, longitude, province.latitude, province.longitude) <= 220) {
      topics.add(`disaster_zone_${province.id}`);
    }
  }
  if (severityScore >= 5) {
    topics.add('disaster_th_all');
  }
  return Array.from(topics);
}

function buildFcmDataPayload(alert) {
  return {
    eventId: String(alert.eventId || `srv_${Date.now()}`),
    title: String(alert.title || 'แจ้งเตือนภัยพิบัติเร่งด่วน'),
    body: String(alert.body || 'ตรวจพบเหตุการณ์ภัยพิบัติใหม่ใกล้ตำแหน่งของคุณ'),
    category: String(alert.category || 'OTHER').toUpperCase(),
    severityScore: String(Math.min(5, Math.max(1, Number(alert.severityScore || 4)))),
    latitude: String(alert.latitude),
    longitude: String(alert.longitude),
    place: String(alert.place || 'พื้นที่เฝ้าระวัง'),
    source: String(alert.source || 'DisasterPulse FCM Server'),
    magnitude: alert.magnitude != null ? String(alert.magnitude) : '',
    timestampMs: String(alert.timestampMs || Date.now()),
    tsunamiAlert: String(Boolean(alert.tsunamiAlert))
  };
}

async function dispatchAlertToMatchingTargets(alert) {
  const data = buildFcmDataPayload(alert);
  const androidConfig = {
    priority: 'high',
    ttl: 3600 * 1000
  };

  const results = {
    topicMessagesSent: 0,
    directDeviceMessagesSent: 0,
    errors: []
  };

  // 1. Send to matching spatial topics (receives even if device didn't send token directly)
  const topics = buildTargetTopics(alert.latitude, alert.longitude, Number(data.severityScore));
  for (const topic of topics) {
    try {
      await messaging.send({
        topic,
        data,
        android: androidConfig
      });
      results.topicMessagesSent += 1;
    } catch (err) {
      results.errors.push(`topic:${topic}: ${err.message}`);
    }
  }

  // 2. Also directly notify registered tokens within their exact radius
  for (const [token, reg] of deviceRegistry.entries()) {
    const distKm = haversineDistanceKm(reg.latitude, reg.longitude, alert.latitude, alert.longitude);
    if (distKm <= reg.radiusKm || Number(data.severityScore) >= 5) {
      try {
        await messaging.send({
          token,
          data,
          notification: {
            title: data.title,
            body: `ห่าง ${distKm.toFixed(1)} กม. • ${data.body}`
          },
          android: {
            priority: 'high',
            notification: {
              channelId: 'disaster_critical_alerts',
              color: '#F59E0B',
              sound: 'default'
            }
          }
        });
        results.directDeviceMessagesSent += 1;
      } catch (err) {
        results.errors.push(`token:${token.slice(0, 12)}...: ${err.message}`);
        if (err.code === 'messaging/registration-token-not-registered') {
          deviceRegistry.delete(token);
        }
      }
    }
  }

  return results;
}

async function fetchRecentSevereDisasters() {
  const events = [];
  const twoHoursAgoIso = new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString();

  // 1. USGS Earthquakes >= M4.0
  try {
    const usgsUrl = `https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson&limit=25&minmagnitude=4.0&orderby=time&starttime=${encodeURIComponent(twoHoursAgoIso)}`;
    const res = await fetch(usgsUrl);
    if (res.ok) {
      const json = await res.json();
      for (const f of json.features || []) {
        const coords = f.geometry?.coordinates || [];
        if (coords.length < 2) continue;
        const mag = Number(f.properties?.mag || 0);
        const severity = mag >= 6.5 ? 5 : mag >= 5.5 ? 4 : 3;
        events.push({
          eventId: `usgs_${f.id}`,
          title: f.properties?.title || `แผ่นดินไหวขนาด M${mag.toFixed(1)}`,
          body: `สถานที่: ${f.properties?.place || 'ไม่ทราบพิกัด'} • ความรุนแรง M${mag.toFixed(1)}`,
          category: 'EARTHQUAKE',
          severityScore: severity,
          latitude: Number(coords[1]),
          longitude: Number(coords[0]),
          place: f.properties?.place || 'ไม่ทราบพิกัด',
          source: 'USGS',
          magnitude: mag,
          timestampMs: Number(f.properties?.time || Date.now()),
          tsunamiAlert: Number(f.properties?.tsunami || 0) === 1
        });
      }
    }
  } catch (err) {
    console.error('USGS poll failed:', err.message);
  }

  // 2. NASA EONET Open Events (recent 1 day)
  try {
    const eonetUrl = 'https://eonet.gsfc.nasa.gov/api/v3/events?status=open&limit=15&days=1';
    const res = await fetch(eonetUrl);
    if (res.ok) {
      const json = await res.json();
      for (const ev of json.events || []) {
        const geom = ev.geometry?.[ev.geometry.length - 1];
        const coords = geom?.coordinates;
        if (!Array.isArray(coords) || coords.length < 2 || typeof coords[0] !== 'number') continue;
        const catTitle = String(ev.categories?.[0]?.title || '').toLowerCase();
        let category = 'OTHER';
        if (catTitle.includes('storm')) category = 'STORM';
        else if (catTitle.includes('flood')) category = 'FLOOD';
        else if (catTitle.includes('fire')) category = 'WILDFIRE';
        else if (catTitle.includes('volcan')) category = 'VOLCANO';
        else if (catTitle.includes('landslide')) category = 'LANDSLIDE';

        events.push({
          eventId: `eonet_${ev.id}`,
          title: ev.title,
          body: `เหตุการณ์จาก NASA EONET (${ev.categories?.[0]?.title || 'Disaster'})`,
          category,
          severityScore: category === 'STORM' || category === 'VOLCANO' ? 4 : 3,
          latitude: Number(coords[1]),
          longitude: Number(coords[0]),
          place: `${Number(coords[1]).toFixed(2)}, ${Number(coords[0]).toFixed(2)}`,
          source: 'NASA EONET',
          magnitude: null,
          timestampMs: geom?.date ? Date.parse(geom.date) || Date.now() : Date.now(),
          tsunamiAlert: false
        });
      }
    }
  } catch (err) {
    console.error('EONET poll failed:', err.message);
  }

  return events;
}

async function pollDisastersAndNotify() {
  const events = await fetchRecentSevereDisasters();
  let newlyDispatched = 0;

  for (const event of events) {
    if (dispatchedEventIds.has(event.eventId)) continue;
    dispatchedEventIds.add(event.eventId);
    await dispatchAlertToMatchingTargets(event);
    newlyDispatched += 1;
  }

  if (dispatchedEventIds.size > 1000) {
    const keep = Array.from(dispatchedEventIds).slice(-500);
    dispatchedEventIds.clear();
    keep.forEach((id) => dispatchedEventIds.add(id));
  }

  return { scannedEvents: events.length, newlyDispatched };
}

function requireApiKey(req, res, next) {
  if (!SERVER_API_KEY) return next();
  if (req.header('x-api-key') !== SERVER_API_KEY) {
    return res.status(401).json({ error: 'Unauthorized: invalid x-api-key' });
  }
  return next();
}

app.get('/health', (_req, res) => {
  res.json({
    status: 'ok',
    projectId: PROJECT_ID,
    registeredDevices: deviceRegistry.size,
    dispatchedEvents: dispatchedEventIds.size
  });
});

app.post('/api/register-location', requireApiKey, (req, res) => {
  const { fcmToken, latitude, longitude, locationLabel, radiusKm, topics } = req.body || {};
  if (!fcmToken || typeof latitude !== 'number' || typeof longitude !== 'number') {
    return res.status(400).json({ error: 'fcmToken, latitude, and longitude are required' });
  }
  deviceRegistry.set(fcmToken, {
    fcmToken,
    latitude,
    longitude,
    locationLabel: locationLabel || 'ตำแหน่งผู้ใช้',
    radiusKm: Number(radiusKm || 150),
    topics: Array.isArray(topics) ? topics : [],
    updatedAtMs: Date.now()
  });
  return res.json({
    ok: true,
    registeredCount: deviceRegistry.size,
    locationLabel: locationLabel || 'ตำแหน่งผู้ใช้'
  });
});

app.post('/api/send-test-alert', requireApiKey, async (req, res) => {
  try {
    const {
      fcmToken,
      latitude = 13.7563,
      longitude = 100.5018,
      locationLabel = 'กรุงเทพมหานคร',
      title = 'ทดสอบระบบแจ้งเตือนภัยพิบัติ (FCM Server)',
      body = 'เชื่อมต่อเซิร์ฟเวอร์ FCM สำเร็จ พร้อมแจ้งเตือนเหตุภัยพิบัติใกล้ตำแหน่งของคุณแบบเรียลไทม์'
    } = req.body || {};

    const alertPayload = {
      eventId: `test_${Date.now()}`,
      title,
      body,
      category: 'STORM',
      severityScore: 4,
      latitude: Number(latitude),
      longitude: Number(longitude),
      place: locationLabel,
      source: 'DisasterPulse FCM Server',
      timestampMs: Date.now(),
      tsunamiAlert: false
    };

    const data = buildFcmDataPayload(alertPayload);
    if (fcmToken) {
      const messageId = await messaging.send({
        token: fcmToken,
        data,
        notification: { title: data.title, body: data.body },
        android: {
          priority: 'high',
          notification: {
            channelId: 'disaster_critical_alerts',
            color: '#F59E0B',
            sound: 'default'
          }
        }
      });
      return res.json({ ok: true, mode: 'direct_token', messageId });
    }

    const result = await dispatchAlertToMatchingTargets(alertPayload);
    return res.json({ ok: true, mode: 'spatial_broadcast', ...result });
  } catch (err) {
    return res.status(500).json({ error: err.message });
  }
});

app.post('/api/dispatch-alert', requireApiKey, async (req, res) => {
  try {
    const body = req.body || {};
    if (typeof body.latitude !== 'number' || typeof body.longitude !== 'number') {
      return res.status(400).json({ error: 'latitude and longitude numbers are required' });
    }
    const result = await dispatchAlertToMatchingTargets(body);
    return res.json({ ok: true, ...result });
  } catch (err) {
    return res.status(500).json({ error: err.message });
  }
});

app.post('/api/poll-now', requireApiKey, async (_req, res) => {
  try {
    const summary = await pollDisastersAndNotify();
    return res.json({ ok: true, ...summary });
  } catch (err) {
    return res.status(500).json({ error: err.message });
  }
});

app.listen(PORT, () => {
  console.log(`DisasterPulse FCM Server listening on port ${PORT} (project: ${PROJECT_ID})`);
  setInterval(() => {
    pollDisastersAndNotify()
      .then((res) => console.log('Scheduled disaster poll:', res))
      .catch((err) => console.error('Scheduled poll error:', err));
  }, POLL_INTERVAL_MS);
});
