import { Router } from 'express';
import { db } from '../db/database.js';
import { broadcastAlert } from '../ws/socket_server.js';

export const alertsRouter = Router();

// GET all incidents
alertsRouter.get('/', (req, res) => {
  res.json({ incidents: db.incidents });
});

// GET active status & emergency contacts
alertsRouter.get('/overview', (req, res) => {
  res.json({
    devices: db.devices,
    emergencyContacts: db.emergencyContacts,
    activeIncidents: db.incidents.filter(i => i.status === "ACTIVE"),
    recentIncidents: db.incidents.slice(0, 10)
  });
});

// POST new alert (from Wi-Fi, BLE Gateway, or Direct API)
alertsRouter.post('/trigger', (req, res) => {
  const { deviceId = "SHESHIELD_01", alertType = "MANUAL_SOS", latitude, longitude } = req.body;

  const incident = db.addIncident({
    deviceId,
    alertType,
    latitude: latitude || 28.6139,
    longitude: longitude || 77.2090,
    smsDispatched: db.emergencyContacts.length
  });

  broadcastAlert('SOS_ALERT_TRIGGERED', {
    incident,
    alertSource: "DIRECT_IP_TRIGGER"
  });

  res.status(201).json({ success: true, incident });
});

// POST resolve incident
alertsRouter.post('/:id/resolve', (req, res) => {
  const { id } = req.params;
  const { notes } = req.body;
  const inc = db.resolveIncident(id, notes);
  if (!inc) return res.status(404).json({ error: "Incident not found" });

  broadcastAlert('INCIDENT_RESOLVED', { incident: inc });
  res.json({ success: true, incident: inc });
});

// POST update device telemetry (battery, GSM signal, coordinates)
alertsRouter.post('/telemetry', (req, res) => {
  const { deviceId, batteryPercent, gsmSignal, latitude, longitude } = req.body;
  const updated = db.updateDeviceTelemetry(deviceId || "SHESHIELD_01", {
    batteryPercent,
    gsmSignal,
    lastLatitude: latitude,
    lastLongitude: longitude
  });

  broadcastAlert('TELEMETRY_UPDATE', { device: updated });
  res.json({ success: true, device: updated });
});
