import { createClient } from '@supabase/supabase-js';
import dotenv from 'dotenv';
dotenv.config();

const supabaseUrl = process.env.SUPABASE_URL || process.env.VITE_SUPABASE_URL;
const supabaseKey = process.env.SUPABASE_SERVICE_ROLE_KEY || process.env.SUPABASE_ANON_KEY || process.env.VITE_SUPABASE_ANON_KEY;

export const isSupabaseConfigured = Boolean(supabaseUrl && supabaseKey && !supabaseUrl.includes('demo-sheshield'));

export const supabase = isSupabaseConfigured
  ? createClient(supabaseUrl, supabaseKey)
  : null;

/**
 * Ingest an alert event directly into the Supabase database.
 * This immediately triggers Realtime notifications on all connected dashboard clients!
 */
export async function pushSupabaseEvent({ wearerId, deviceId, eventType, lat, lng, address, payload }) {
  if (!supabase) {
    console.log('ℹ️ [Backend] Supabase credentials not set, event stored in local database.');
    return null;
  }

  try {
    const { data: event, error: eventErr } = await supabase
      .from('events')
      .insert([
        {
          wearer_id: wearerId || 'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d',
          device_id: deviceId || 'd1e2f3a4-b5c6-7d8e-9f0a-1b2c3d4e5f6a',
          event_type: eventType,
          lat: lat || 28.6139,
          lng: lng || 77.2090,
          address: address || 'Connaught Place, Inner Circle, New Delhi',
          payload: payload || {}
        }
      ])
      .select()
      .single();

    if (eventErr) {
      console.error('❌ [Backend] Supabase event insert error:', eventErr);
      return null;
    }

    // Update Device State
    let nextState = 'safe';
    if (eventType === 'emergency_triggered') nextState = 'emergency';
    else if (eventType === 'watch_started') nextState = 'watch';

    await supabase
      .from('devices')
      .update({
        current_state: nextState,
        last_lat: lat,
        last_lng: lng,
        address: address,
        last_heartbeat: new Date().toISOString()
      })
      .eq('id', deviceId || 'd1e2f3a4-b5c6-7d8e-9f0a-1b2c3d4e5f6a');

    console.log(`✅ [Backend] Successfully inserted event into Supabase (ID: ${event.id}) -> Broadcasted to Realtime`);
    return event;
  } catch (err) {
    console.error('❌ [Backend] Unexpected error pushing to Supabase:', err);
    return null;
  }
}
