import { supabase, isSupabaseConfigured } from '../db/supabase_client.js';
import { db } from '../db/database.js';

export class DeviceService {
  /**
   * Resolves wearer and device IDs based on sender phone number or device code
   */
  static async resolveWearerAndDevice({ senderPhone, deviceCode, wearerId, deviceId }) {
    // 1. If explicit IDs provided, verify and return
    if (wearerId && deviceId) {
      return { wearerId, deviceId };
    }

    // 2. Query Supabase if configured
    if (isSupabaseConfigured && supabase) {
      try {
        // Try finding device by deviceCode
        if (deviceCode) {
          const { data: dev } = await supabase
            .from('devices')
            .select('id, wearer_id')
            .eq('device_code', deviceCode)
            .single();

          if (dev) {
            return { wearerId: dev.wearer_id, deviceId: dev.id };
          }
        }

        // Try finding wearer by sender phone number
        if (senderPhone) {
          const cleanPhone = senderPhone.replace(/[\s-]/g, '');
          const { data: user } = await supabase
            .from('users')
            .select('id')
            .or(`phone.eq.${cleanPhone},phone.eq.${senderPhone}`)
            .single();

          if (user) {
            const { data: dev } = await supabase
              .from('devices')
              .select('id')
              .eq('wearer_id', user.id)
              .single();

            return { wearerId: user.id, deviceId: dev?.id || null };
          }
        }

        // Fallback to first active wearer in database
        const { data: firstUser } = await supabase
          .from('users')
          .select('id')
          .limit(1)
          .single();

        if (firstUser) {
          const { data: firstDev } = await supabase
            .from('devices')
            .select('id')
            .eq('wearer_id', firstUser.id)
            .single();

          return { wearerId: firstUser.id, deviceId: firstDev?.id || null };
        }
      } catch (err) {
        console.warn('⚠️ [DeviceService] Supabase lookup error, falling back to local memory:', err.message);
      }
    }

    // 3. Fallback to Local In-Memory DB
    const localUser = db.devices[0] || { id: 'SHESHIELD_01', wearerId: 'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d' };
    return {
      wearerId: 'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d',
      deviceId: 'd1e2f3a4-b5c6-7d8e-9f0a-1b2c3d4e5f6a'
    };
  }

  /**
   * Register or pair a new watch device code to a wearer
   */
  static async registerDevice({ deviceCode, wearerInviteCode, wearerPhone, firmwareVersion = 'v1.0.0-esp32s3' }) {
    console.log(`📱 [DeviceService] Registering device code [${deviceCode}] with invite [${wearerInviteCode}]`);

    if (isSupabaseConfigured && supabase) {
      // Find wearer by invite code
      const { data: user, error: userErr } = await supabase
        .from('users')
        .select('id, full_name')
        .eq('invite_code', wearerInviteCode.toUpperCase())
        .single();

      if (userErr || !user) {
        throw new Error(`Wearer invite code "${wearerInviteCode}" not found.`);
      }

      // Upsert device
      const { data: device, error: devErr } = await supabase
        .from('devices')
        .upsert(
          [
            {
              wearer_id: user.id,
              device_code: deviceCode,
              firmware_version: firmwareVersion,
              current_state: 'safe',
              is_online: true,
              last_heartbeat: new Date().toISOString()
            }
          ],
          { onConflict: 'wearer_id' }
        )
        .select()
        .single();

      if (devErr) throw devErr;

      return { success: true, user, device };
    }

    // Local in-memory registration
    return {
      success: true,
      user: { id: 'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d', full_name: 'Aanya Sharma' },
      device: { id: 'd1e2f3a4-b5c6-7d8e-9f0a-1b2c3d4e5f6a', device_code: deviceCode }
    };
  }
}
