import express from 'express';
import http from 'http';
import cors from 'cors';
import dotenv from 'dotenv';
dotenv.config();

import { alertsRouter } from './routes/alerts.js';
import { devicesRouter } from './routes/devices.js';
import { smsRouter } from './sms-webhook/sms_receiver.js';
import { testHarnessRouter } from './routes/test_harness.js';
import { initWebSocketServer } from './ws/socket_server.js';
import { isSupabaseConfigured } from './db/supabase_client.js';

const app = express();
const PORT = process.env.PORT || 4000;

app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// REST Routes
app.use('/api/alerts', alertsRouter);
app.use('/api/devices', devicesRouter);
app.use('/api/sms-webhook', smsRouter);

// Interactive Visual Test Harness
app.use('/test', testHarnessRouter);

// Root Health & System Status
app.get('/health', (req, res) => {
  res.json({
    status: 'ONLINE',
    system: 'SheShield Sentinel Alert Ingestion API',
    supabaseConnected: isSupabaseConfigured,
    uptime: process.uptime(),
    timestamp: new Date().toISOString()
  });
});

const server = http.createServer(app);

// Initialize live WebSocket streaming server for local/fallback dashboard sync
initWebSocketServer(server);

server.listen(PORT, () => {
  console.log(`=======================================================`);
  console.log(`🛡️  SheShield Sentinel Alert Ingestion Service running on port ${PORT}`);
  console.log(`📡 WebSocket endpoint:        ws://localhost:${PORT}/ws`);
  console.log(`📩 Unified SMS Webhook:       http://localhost:${PORT}/api/sms-webhook/inbound`);
  console.log(`📞 Twilio SMS Webhook:        http://localhost:${PORT}/api/sms-webhook/twilio`);
  console.log(`📱 Android Forwarder Webhook: http://localhost:${PORT}/api/sms-webhook/forwarder`);
  console.log(`🧪 Interactive Test Harness:  http://localhost:${PORT}/test`);
  console.log(`💾 Supabase Realtime:         ${isSupabaseConfigured ? 'CONNECTED' : 'STANDALONE (Local Mode)'}`);
  console.log(`=======================================================`);
});
