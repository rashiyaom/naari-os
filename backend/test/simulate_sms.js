/**
 * SheShield Sentinel - Inbound SMS Simulation Test Harness (CLI)
 * 
 * Simulates real SMS alert texts forwarded by SIM800L to the backend webhook.
 * Usage:
 *   node test/simulate_sms.js fall
 *   node test/simulate_sms.js voice
 *   node test/simulate_sms.js button
 *   node test/simulate_sms.js watch_start
 *   node test/simulate_sms.js ping
 *   node test/simulate_sms.js cancel
 *   node test/simulate_sms.js twilio
 */

import http from 'http';

const BACKEND_PORT = process.env.PORT || 4000;
const SAMPLES = {
  fall: {
    name: "Fall Shock (3.6G Impact) Emergency SOS",
    endpoint: "/api/sms-webhook/inbound",
    body: {
      From: "+919876543210",
      Body: "CRITICAL EMERGENCY ALERT!\nWearer: Aanya Sharma\nTrigger: Fall Shock (3.6G Impact) / Immobility Detected\nEvidence: Audio Recorded\nLocation: https://maps.google.com/?q=28.614520,77.209530\nTime: 2026/09/27 22:45:12"
    }
  },
  voice: {
    name: "Voice Keyword Distress ('Help Me') SOS",
    endpoint: "/api/sms-webhook/inbound",
    body: {
      From: "+919876543210",
      Body: "CRITICAL EMERGENCY ALERT!\nWearer: Aanya Sharma\nTrigger: Voice Keyword (\"HELP ME\" 97% Conf)\nEvidence: Audio Recorded\nLocation: https://maps.google.com/?q=28.613800,77.208900\nTime: 2026/09/27 22:46:00"
    }
  },
  button: {
    name: "Hardware SOS Button Hold Emergency SOS",
    endpoint: "/api/sms-webhook/inbound",
    body: {
      From: "+919876543210",
      Body: "CRITICAL EMERGENCY ALERT!\nWearer: Aanya Sharma\nTrigger: SOS Button Long Press\nEvidence: Audio Recorded\nLocation: https://maps.google.com/?q=28.613200,77.208100\nTime: 2026/09/27 22:47:30"
    }
  },
  watch_start: {
    name: "Safety Watch Activation (Night Walk)",
    endpoint: "/api/sms-webhook/inbound",
    body: {
      From: "+919876543210",
      Body: "[SAFETY WATCH ACTIVE]\nWearer: Aanya Sharma has enabled Safety Watch (Night Walk Guard).\nLocation: https://maps.google.com/?q=28.612800,77.207500\nTime: 2026/09/27 22:40:00"
    }
  },
  ping: {
    name: "Safety Watch Periodic GPS Ping",
    endpoint: "/api/sms-webhook/inbound",
    body: {
      From: "+919876543210",
      Body: "[SAFETY WATCH UPDATE]\nWearer: Aanya Sharma\nStatus: Active Watch\nLocation: https://maps.google.com/?q=28.613400,77.208300\nTime: 2026/09/27 22:43:00"
    }
  },
  cancel: {
    name: "Safety Watch Ended / Wearer Safe",
    endpoint: "/api/sms-webhook/inbound",
    body: {
      From: "+919876543210",
      Body: "[SAFETY WATCH ENDED]\nWearer Aanya Sharma has safely ended the Safety Watch session. All is well."
    }
  },
  twilio: {
    name: "Twilio Inbound SMS Webhook Format",
    endpoint: "/api/sms-webhook/twilio",
    isUrlEncoded: true,
    body: {
      From: "+919876543210",
      To: "+18005550199",
      MessageSid: "SM" + Math.random().toString(36).substring(2, 15),
      Body: "CRITICAL EMERGENCY ALERT!\nWearer: Aanya Sharma\nTrigger: Fall Shock (3.8G Impact)\nLocation: https://maps.google.com/?q=28.614100,77.209100\nTime: 2026/09/27 22:48:00"
    }
  },
  forwarder: {
    name: "Android SMS-to-Webhook Forwarder App Format",
    endpoint: "/api/sms-webhook/forwarder",
    body: {
      sender: "+919876543210",
      message: "CRITICAL EMERGENCY ALERT!\nWearer: Aanya Sharma\nTrigger: Hardware SOS Button\nLocation: https://maps.google.com/?q=28.613900,77.209000\nTime: 2026/09/27 22:50:00",
      timestamp: Date.now(),
      device_id: "SHESHIELD_WATCH_01"
    }
  }
};

async function sendSimulation(type) {
  const sample = SAMPLES[type] || SAMPLES.fall;
  console.log(`\n🚀 [TEST HARNESS] Firing Simulation: "${sample.name}"`);
  console.log(`📡 Target: POST http://localhost:${BACKEND_PORT}${sample.endpoint}`);

  let payloadData = '';
  let contentType = 'application/json';

  if (sample.isUrlEncoded) {
    contentType = 'application/x-www-form-urlencoded';
    payloadData = new URLSearchParams(sample.body).toString();
  } else {
    payloadData = JSON.stringify(sample.body);
  }

  const options = {
    hostname: 'localhost',
    port: BACKEND_PORT,
    path: sample.endpoint,
    method: 'POST',
    headers: {
      'Content-Type': contentType,
      'Content-Length': Buffer.byteLength(payloadData)
    }
  };

  const req = http.request(options, (res) => {
    let responseBody = '';
    res.on('data', (chunk) => { responseBody += chunk; });
    res.on('end', () => {
      console.log(`\n📥 [Response Status]: ${res.statusCode} ${res.statusMessage}`);
      console.log(`📦 [Response Body]:\n${responseBody}\n`);
      console.log(`✨ Check your Dashboard (http://localhost:5173) to see the live update!\n`);
    });
  });

  req.on('error', (e) => {
    console.error(`❌ [Error]: Failed to connect to backend on port ${BACKEND_PORT}. Is it running?`, e.message);
  });

  req.write(payloadData);
  req.end();
}

const arg = process.argv[2] || 'fall';
sendSimulation(arg);
