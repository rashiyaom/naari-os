import { Router } from 'express';

export const testHarnessRouter = Router();

// HTML Web Dashboard for Test Harness
testHarnessRouter.get('/', (req, res) => {
  const html = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>SheShield Sentinel - Inbound SMS Webhook Simulator</title>
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #070a11; color: #f3f4f6; margin: 0; padding: 32px; }
    .container { max-width: 800px; margin: 0 auto; }
    h1 { color: #2dd4bf; margin-bottom: 8px; font-size: 24px; }
    p { color: #94a3b8; font-size: 14px; margin-bottom: 24px; }
    .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; margin-bottom: 24px; }
    .btn { background: #121826; border: 1px solid #222d42; color: #fff; padding: 14px 18px; border-radius: 12px; font-size: 13.5px; font-weight: 600; text-align: left; cursor: pointer; transition: all 0.2s; display: flex; flex-direction: column; gap: 4px; }
    .btn:hover { background: #1a2234; border-color: #2dd4bf; transform: translateY(-1px); }
    .btn.emergency { border-color: rgba(239, 68, 68, 0.4); background: rgba(239, 68, 68, 0.1); color: #fca5a5; }
    .btn.emergency:hover { background: rgba(239, 68, 68, 0.2); border-color: #ef4444; }
    .btn.watch { border-color: rgba(45, 212, 191, 0.4); background: rgba(45, 212, 191, 0.08); color: #2dd4bf; }
    .btn.watch:hover { background: rgba(45, 212, 191, 0.15); border-color: #2dd4bf; }
    .btn small { font-size: 11px; color: #64748b; font-weight: 400; }
    .console-box { background: #000; border: 1px solid #222d42; border-radius: 12px; padding: 18px; font-family: monospace; font-size: 12px; color: #2dd4bf; max-height: 280px; overflow-y: auto; white-space: pre-wrap; }
    .status-tag { display: inline-block; padding: 4px 10px; border-radius: 6px; font-size: 11px; font-weight: 700; background: rgba(45, 212, 191, 0.15); color: #2dd4bf; margin-bottom: 16px; }
  </style>
</head>
<body>
  <div class="container">
    <div class="status-tag">SIM800L CELLULAR SMS WEBHOOK SIMULATOR</div>
    <h1>SheShield Sentinel Ingestion Test Harness</h1>
    <p>Click any button below to fire a simulated SMS alert text into the backend webhook. The event will be parsed, stored in Supabase, and broadcast live to the Guardian Dashboard (http://localhost:5173).</p>

    <div class="grid">
      <button class="btn emergency" onclick="fireAlert('fall')">
        🚨 1. Simulate Fall Shock (3.6G) SOS
        <small>QMI8658 IMU shock + GPS coordinates + Audio evidence tag</small>
      </button>

      <button class="btn emergency" onclick="fireAlert('voice')">
        🎙️ 2. Simulate Voice Keyword ("Help Me") SOS
        <small>ESP-SR offline speech distress trigger</small>
      </button>

      <button class="btn emergency" onclick="fireAlert('button')">
        🔴 3. Simulate Hardware SOS Button Long Press
        <small>Manual panic button held > 2.5 seconds</small>
      </button>

      <button class="btn watch" onclick="fireAlert('watch_start')">
        🛡️ 4. Simulate Safety Watch Mode Start
        <small>Night Walk guard session activated with live location</small>
      </button>

      <button class="btn" onclick="fireAlert('ping')">
        📍 5. Simulate Periodic GPS Ping
        <small>3-minute location breadcrumb update</small>
      </button>

      <button class="btn" onclick="fireAlert('cancel')">
        ✅ 6. Simulate Watch Cancelled / Safe
        <small>Wearer safely ended session / false alarm cleared</small>
      </button>

      <button class="btn" onclick="fireAlert('twilio')">
        📞 7. Simulate Twilio Format Webhook
        <small>Inbound SMS forwarded via Twilio Carrier Bridge</small>
      </button>

      <button class="btn" onclick="fireAlert('forwarder')">
        📱 8. Simulate Android Forwarder App
        <small>JSON format from paired Android SMS forwarder</small>
      </button>
    </div>

    <h3 style="font-size: 14px; color: #94a3b8; margin-bottom: 8px;">Live Ingestion Output:</h3>
    <div id="output" class="console-box">Ready to simulate. Click any button above...</div>
  </div>

  <script>
    async function fireAlert(type) {
      const output = document.getElementById('output');
      output.innerText = '📡 Firing SMS simulation for [' + type + ']...';

      let endpoint = '/api/sms-webhook/inbound';
      let payload = {};

      if (type === 'fall') {
        payload = {
          From: '+919876543210',
          Body: 'CRITICAL EMERGENCY ALERT!\\nWearer: Aanya Sharma\\nTrigger: Fall Shock (3.6G Impact) / Immobility Detected\\nEvidence: Audio Recorded\\nLocation: https://maps.google.com/?q=28.614520,77.209530\\nTime: ' + new Date().toLocaleTimeString()
        };
      } else if (type === 'voice') {
        payload = {
          From: '+919876543210',
          Body: 'CRITICAL EMERGENCY ALERT!\\nWearer: Aanya Sharma\\nTrigger: Voice Keyword ("HELP ME" 97% Conf)\\nEvidence: Audio Recorded\\nLocation: https://maps.google.com/?q=28.613800,77.208900\\nTime: ' + new Date().toLocaleTimeString()
        };
      } else if (type === 'button') {
        payload = {
          From: '+919876543210',
          Body: 'CRITICAL EMERGENCY ALERT!\\nWearer: Aanya Sharma\\nTrigger: SOS Button Long Press\\nEvidence: Audio Recorded\\nLocation: https://maps.google.com/?q=28.613200,77.208100\\nTime: ' + new Date().toLocaleTimeString()
        };
      } else if (type === 'watch_start') {
        payload = {
          From: '+919876543210',
          Body: '[SAFETY WATCH ACTIVE]\\nWearer: Aanya Sharma has enabled Safety Watch (Night Walk Guard).\\nLocation: https://maps.google.com/?q=28.612800,77.207500\\nTime: ' + new Date().toLocaleTimeString()
        };
      } else if (type === 'ping') {
        payload = {
          From: '+919876543210',
          Body: '[SAFETY WATCH UPDATE]\\nWearer: Aanya Sharma\\nStatus: Active Watch\\nLocation: https://maps.google.com/?q=' + (28.6139 + (Math.random()-0.5)*0.003).toFixed(6) + ',' + (77.2090 + (Math.random()-0.5)*0.003).toFixed(6) + '\\nTime: ' + new Date().toLocaleTimeString()
        };
      } else if (type === 'cancel') {
        payload = {
          From: '+919876543210',
          Body: '[SAFETY WATCH ENDED]\\nWearer Aanya Sharma has safely ended the Safety Watch session. All is well.'
        };
      } else if (type === 'forwarder') {
        endpoint = '/api/sms-webhook/forwarder';
        payload = {
          sender: '+919876543210',
          message: 'CRITICAL EMERGENCY ALERT!\\nWearer: Aanya Sharma\\nTrigger: Hardware SOS Button\\nLocation: https://maps.google.com/?q=28.614100,77.209100\\nTime: ' + new Date().toLocaleTimeString(),
          timestamp: Date.now(),
          device_id: 'SHESHIELD_WATCH_01'
        };
      } else if (type === 'twilio') {
        endpoint = '/api/sms-webhook/twilio';
        payload = {
          From: '+919876543210',
          To: '+18005550199',
          MessageSid: 'SM_TEST_' + Date.now(),
          Body: 'CRITICAL EMERGENCY ALERT!\\nWearer: Aanya Sharma\\nTrigger: Fall Shock (3.8G Impact)\\nLocation: https://maps.google.com/?q=28.614000,77.209200\\nTime: ' + new Date().toLocaleTimeString()
        };
      }

      try {
        const res = await fetch(endpoint, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload)
        });
        const text = await res.text();
        output.innerText = '✅ Ingestion Success [HTTP ' + res.status + ']:\\n\\n' + text + '\\n\\n👉 Now check http://localhost:5173 to see the live update!';
      } catch (e) {
        output.innerText = '❌ Failed to reach backend: ' + e.message;
      }
    }
  </script>
</body>
</html>`;

  res.send(html);
});
