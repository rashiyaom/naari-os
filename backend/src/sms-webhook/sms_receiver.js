import { Router } from 'express';
import { db } from '../db/database.js';
import { broadcastAlert } from '../ws/socket_server.js';
import { pushSupabaseEvent } from '../db/supabase_client.js';
import { parseSmsPayload } from '../parser/sms_parser.js';
import { DeviceService } from '../services/device_service.js';

export const smsRouter = Router();

/**
 * Helper function to process any incoming SMS alert text
 */
async function processInboundSms({ rawBody, senderPhone, sourceChannel, customDeviceCode }) {
  console.log(`\n=======================================================`);
  console.log(`📩 [SMS INGESTION] Source: ${sourceChannel} | From: ${senderPhone}`);
  console.log(`📝 Raw Message: "${rawBody}"`);

  // 1. Parse structured SMS into normalized event object
  const parsed = parseSmsPayload(rawBody, senderPhone);
  console.log(`🔍 [Parsed Event] Type: ${parsed.event_type} | Lat: ${parsed.lat}, Lng: ${parsed.lng} | Trigger: ${parsed.payload.trigger}`);

  // 2. Resolve Wearer and Device attribution
  const { wearerId, deviceId } = await DeviceService.resolveWearerAndDevice({
    senderPhone,
    deviceCode: customDeviceCode
  });
  console.log(`👤 [Attribution] Wearer ID: ${wearerId} | Device ID: ${deviceId}`);

  // 3. Store in local in-memory DB
  const localIncident = db.addIncident({
    deviceId,
    wearerId,
    alertType: parsed.event_type,
    senderPhone,
    rawSms: rawBody,
    latitude: parsed.lat,
    longitude: parsed.lng,
    trigger: parsed.payload.trigger,
    audioClipUrl: parsed.payload.audio_url
  });

  // 4. Broadcast live over WebSocket to all open dashboards
  broadcastAlert('SOS_ALERT_TRIGGERED', {
    incident: localIncident,
    parsedEvent: parsed,
    sourceChannel,
    timestamp: new Date().toISOString()
  });

  // 5. Write to Supabase Postgres 'events' table -> triggers Realtime subscription!
  const supabaseEvent = await pushSupabaseEvent({
    wearerId,
    deviceId,
    eventType: parsed.event_type,
    lat: parsed.lat,
    lng: parsed.lng,
    address: 'Connaught Place, Inner Circle, New Delhi',
    payload: {
      ...parsed.payload,
      source_channel: sourceChannel,
      sender_phone: senderPhone
    }
  });

  console.log(`✅ [Ingestion Completed] Event ID: ${supabaseEvent?.id || localIncident.id}`);
  console.log(`=======================================================\n`);

  return { localIncident, supabaseEvent, parsed };
}

/**
 * 1. Twilio Inbound SMS Webhook
 * Twilio sends form-urlencoded data: { From, Body, MessageSid, To, AccountSid }
 */
smsRouter.post('/twilio', async (req, res) => {
  try {
    const { From, Body, MessageSid, To } = req.body;

    if (!Body) {
      return res.status(400).send('<Response><Message>Empty Body</Message></Response>');
    }

    await processInboundSms({
      rawBody: Body,
      senderPhone: From || '+919876543210',
      sourceChannel: 'TWILIO_INBOUND_SMS'
    });

    // Return valid TwiML response
    res.set('Content-Type', 'text/xml');
    res.send('<?xml version="1.0" encoding="UTF-8"?><Response></Response>');
  } catch (err) {
    console.error('❌ [Twilio Webhook Error]:', err);
    res.status(500).send('<Response><Message>Internal Error</Message></Response>');
  }
});

/**
 * 2. Android SMS-to-Webhook Forwarder App (e.g. SMS Forwarder / IFTTT / Tasker)
 * Accepts JSON: { from/sender, message/text/body, timestamp, device_id }
 */
smsRouter.post('/forwarder', async (req, res) => {
  try {
    const body = req.body || {};
    const rawBody = body.message || body.text || body.body || body.content || '';
    const senderPhone = body.from || body.sender || body.phone || '+919876543210';
    const deviceCode = body.device_id || body.deviceCode;

    if (!rawBody) {
      return res.status(400).json({ error: 'Missing message body' });
    }

    const result = await processInboundSms({
      rawBody,
      senderPhone,
      sourceChannel: 'ANDROID_SMS_FORWARDER_APP',
      customDeviceCode: deviceCode
    });

    res.status(200).json({
      success: true,
      status: 'PROCESSED',
      eventType: result.parsed.event_type,
      incidentId: result.localIncident.id,
      supabaseEventId: result.supabaseEvent?.id || null
    });
  } catch (err) {
    console.error('❌ [Forwarder Webhook Error]:', err);
    res.status(500).json({ error: err.message });
  }
});

/**
 * 3. Unified Inbound Webhook (Handles any generic HTTP payload)
 */
smsRouter.post('/inbound', async (req, res) => {
  try {
    const body = req.body || {};
    const rawBody = body.Body || body.message || body.text || body.body || body.rawSms || '';
    const senderPhone = body.From || body.from || body.sender || '+919876543210';
    const deviceCode = body.deviceId || body.deviceCode;

    if (!rawBody) {
      return res.status(400).json({ error: 'Missing SMS text payload' });
    }

    const result = await processInboundSms({
      rawBody,
      senderPhone,
      sourceChannel: 'UNIFIED_WEBHOOK',
      customDeviceCode: deviceCode
    });

    res.status(200).json({
      success: true,
      status: 'PROCESSED',
      eventType: result.parsed.event_type,
      incidentId: result.localIncident.id,
      supabaseEventId: result.supabaseEvent?.id || null
    });
  } catch (err) {
    console.error('❌ [Inbound Webhook Error]:', err);
    res.status(500).json({ error: err.message });
  }
});

/**
 * 4. Direct SIM800L GPRS HTTP Ingestion
 */
smsRouter.post('/gprs-direct', async (req, res) => {
  try {
    const { deviceId, wearerId, eventType, lat, lng, payload } = req.body;

    const supabaseEvent = await pushSupabaseEvent({
      wearerId,
      deviceId,
      eventType: eventType || 'emergency_triggered',
      lat: parseFloat(lat) || 28.6139,
      lng: parseFloat(lng) || 77.2090,
      payload: payload || { trigger: 'GPRS_DIRECT_UPLINK' }
    });

    broadcastAlert('SOS_ALERT_TRIGGERED', {
      eventType: eventType || 'emergency_triggered',
      lat,
      lng,
      sourceChannel: 'SIM800L_GPRS_HTTP'
    });

    res.status(200).json({ success: true, supabaseEventId: supabaseEvent?.id || null });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});
