import { Router } from 'express';
import { DeviceService } from '../services/device_service.js';
import { db } from '../db/database.js';
import { supabase, isSupabaseConfigured } from '../db/supabase_client.js';

export const devicesRouter = Router();

// List all registered devices
devicesRouter.get('/', async (req, res) => {
  if (isSupabaseConfigured && supabase) {
    const { data: devices, error } = await supabase.from('devices').select('*, users(full_name, phone, invite_code)');
    if (!error && devices) {
      return res.json({ devices });
    }
  }
  res.json({ devices: db.devices });
});

// Register / Pair a new watch device ID to a wearer using invite code
devicesRouter.post('/register', async (req, res) => {
  try {
    const { deviceCode, wearerInviteCode, wearerPhone, firmwareVersion } = req.body;

    if (!deviceCode || !wearerInviteCode) {
      return res.status(400).json({ error: 'deviceCode and wearerInviteCode are required.' });
    }

    const result = await DeviceService.registerDevice({
      deviceCode,
      wearerInviteCode,
      wearerPhone,
      firmwareVersion
    });

    res.status(201).json(result);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// Query single device status
devicesRouter.get('/:id/status', async (req, res) => {
  const { id } = req.params;

  if (isSupabaseConfigured && supabase) {
    const { data: device } = await supabase.from('devices').select('*').eq('id', id).single();
    if (device) return res.json({ device });
  }

  const dev = db.devices.find(d => d.id === id);
  if (!dev) return res.status(404).json({ error: 'Device not found' });
  res.json({ device: dev });
});
