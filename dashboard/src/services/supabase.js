import { createClient } from '@supabase/supabase-js';

// Environment variables or fallback defaults
const supabaseUrl = import.meta.env.VITE_SUPABASE_URL || 'https://demo-sheshield.supabase.co';
const supabaseAnonKey = import.meta.env.VITE_SUPABASE_ANON_KEY || 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.demo';

export const isSupabaseConfigured = Boolean(
  import.meta.env.VITE_SUPABASE_URL && 
  import.meta.env.VITE_SUPABASE_ANON_KEY &&
  !import.meta.env.VITE_SUPABASE_URL.includes('demo-sheshield')
);

// Create Supabase client instance
export const supabase = createClient(supabaseUrl, supabaseAnonKey, {
  auth: {
    persistSession: true,
    autoRefreshToken: true,
  },
  realtime: {
    params: {
      eventsPerSecond: 10,
    },
  },
});

// Fallback in-memory storage for offline / zero-config demo
const LOCAL_STORAGE_KEY = 'sheshield_demo_store_v1';

export const initialMockState = {
  user: {
    id: 'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d',
    full_name: 'Aanya Sharma',
    phone: '+91 98765 43210',
    email: 'aanya.sharma@example.com',
    invite_code: 'SENTINEL-7892',
    avatar_url: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200&auto=format&fit=crop&q=80',
    emergency_notes: 'Blood Group: B+. Commutes between Central Metro and Cyber City.',
  },
  contact: {
    id: 'c1111111-1111-1111-1111-111111111111',
    full_name: 'Rajesh Sharma',
    phone: '+91 98765 00001',
    email: 'rajesh.sharma@example.com',
    relation: 'Father',
    is_primary: true,
  },
  device: {
    id: 'd1e2f3a4-b5c6-7d8e-9f0a-1b2c3d4e5f6a',
    wearer_id: 'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d',
    device_code: 'SHESHIELD_WATCH_01',
    current_state: 'safe', // 'safe' | 'watch' | 'emergency'
    battery_level: 88,
    gsm_signal: 26, // 0 - 31 CSQ scale
    last_lat: 28.6139,
    last_lng: 77.2090,
    address: 'Connaught Place, Inner Circle, New Delhi',
    firmware_version: 'v1.0.0-esp32s3',
    is_online: true,
    last_heartbeat: new Date().toISOString(),
  },
  contacts: [
    { id: 'c1', full_name: 'Rajesh Sharma', phone: '+91 98765 00001', relation: 'Father', is_primary: true, notify_sms: true, notify_call: true },
    { id: 'c2', full_name: 'Maya Sharma', phone: '+91 98765 00002', relation: 'Sister', is_primary: false, notify_sms: true, notify_call: false },
    { id: 'c3', full_name: 'Arjun Kapoor', phone: '+91 98765 00003', relation: 'Friend', is_primary: false, notify_sms: true, notify_call: false },
    { id: 'c4', full_name: 'Police Emergency Response', phone: '112', relation: 'Authorities', is_primary: false, notify_sms: true, notify_call: true }
  ],
  events: [
    {
      id: 'evt-001',
      device_id: 'd1e2f3a4-b5c6-7d8e-9f0a-1b2c3d4e5f6a',
      wearer_id: 'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d',
      event_type: 'watch_started',
      lat: 28.6128,
      lng: 77.2075,
      address: 'Rajiv Chowk Metro Gate 2, New Delhi',
      payload: {
        trigger: 'MANUAL_ACTIVATION',
        mode: 'Night Walk Guard Mode',
        battery: 94,
        gsm_csq: 28
      },
      created_at: new Date(Date.now() - 42 * 60 * 1000).toISOString()
    },
    {
      id: 'evt-002',
      device_id: 'd1e2f3a4-b5c6-7d8e-9f0a-1b2c3d4e5f6a',
      wearer_id: 'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d',
      event_type: 'location_update',
      lat: 28.6133,
      lng: 77.2082,
      address: 'Janpath Road Crossing, New Delhi',
      payload: {
        speed_kmh: 4.8,
        battery: 92,
        gsm_csq: 27
      },
      created_at: new Date(Date.now() - 25 * 60 * 1000).toISOString()
    },
    {
      id: 'evt-003',
      device_id: 'd1e2f3a4-b5c6-7d8e-9f0a-1b2c3d4e5f6a',
      wearer_id: 'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d',
      event_type: 'location_update',
      lat: 28.6139,
      lng: 77.2090,
      address: 'Connaught Place, Inner Circle, New Delhi',
      payload: {
        speed_kmh: 4.2,
        battery: 88,
        gsm_csq: 26
      },
      created_at: new Date(Date.now() - 5 * 60 * 1000).toISOString()
    }
  ]
};

function getLocalStore() {
  try {
    const raw = localStorage.getItem(LOCAL_STORAGE_KEY);
    if (!raw) {
      localStorage.setItem(LOCAL_STORAGE_KEY, JSON.stringify(initialMockState));
      return initialMockState;
    }
    return JSON.parse(raw);
  } catch (e) {
    return initialMockState;
  }
}

function saveLocalStore(state) {
  try {
    localStorage.setItem(LOCAL_STORAGE_KEY, JSON.stringify(state));
  } catch (e) {
    console.error('Failed to save to localStorage', e);
  }
}

// ----------------------------------------------------------------------
// Unified API layer (Works against Real Supabase or Instant Local Mock)
// ----------------------------------------------------------------------

export async function loginWithInviteCode(inviteCode) {
  const normalizedCode = inviteCode.trim().toUpperCase();

  if (isSupabaseConfigured) {
    try {
      const { data: user, error: userError } = await supabase
        .from('users')
        .select('*')
        .eq('invite_code', normalizedCode)
        .single();

      if (userError || !user) {
        throw new Error('Invalid invite code. Please check with the watch wearer.');
      }

      const { data: device } = await supabase
        .from('devices')
        .select('*')
        .eq('wearer_id', user.id)
        .single();

      const { data: contacts } = await supabase
        .from('contacts')
        .select('*')
        .eq('wearer_id', user.id);

      return {
        user,
        device: device || initialMockState.device,
        contacts: contacts || [],
        isSupabase: true
      };
    } catch (err) {
      console.warn('Supabase query failed, checking fallback code:', err);
    }
  }

  // Fallback demo matching
  if (normalizedCode === 'SENTINEL-7892' || normalizedCode === 'DEMO' || normalizedCode.startsWith('SENTINEL')) {
    const store = getLocalStore();
    return {
      user: store.user,
      device: store.device,
      contacts: store.contacts,
      isSupabase: false
    };
  }

  throw new Error('Invite code not found. Use demo code: SENTINEL-7892');
}

export async function fetchEventHistory(wearerId) {
  if (isSupabaseConfigured) {
    try {
      const { data, error } = await supabase
        .from('events')
        .select('*')
        .eq('wearer_id', wearerId)
        .order('created_at', { ascending: false })
        .limit(50);

      if (!error && data && data.length > 0) {
        return data;
      }
    } catch (e) {
      console.warn('Error fetching events from Supabase:', e);
    }
  }

  const store = getLocalStore();
  return store.events;
}

export async function insertEvent(eventData) {
  const newEvent = {
    id: `evt-${Date.now()}-${Math.floor(Math.random() * 1000)}`,
    created_at: new Date().toISOString(),
    ...eventData
  };

  // Determine target device state from event type
  let nextState = 'safe';
  if (eventData.event_type === 'emergency_triggered') {
    nextState = 'emergency';
  } else if (eventData.event_type === 'watch_started') {
    nextState = 'watch';
  } else if (eventData.event_type === 'watch_cancelled' || eventData.event_type === 'emergency_resolved') {
    nextState = 'safe';
  }

  // Real Supabase insert
  if (isSupabaseConfigured) {
    try {
      await supabase.from('events').insert([
        {
          wearer_id: eventData.wearer_id,
          device_id: eventData.device_id,
          event_type: eventData.event_type,
          lat: eventData.lat,
          lng: eventData.lng,
          address: eventData.address,
          payload: eventData.payload,
        }
      ]);

      if (eventData.device_id) {
        await supabase.from('devices').update({
          current_state: nextState,
          last_lat: eventData.lat,
          last_lng: eventData.lng,
          address: eventData.address,
          last_heartbeat: new Date().toISOString()
        }).eq('id', eventData.device_id);
      }
    } catch (e) {
      console.warn('Failed to insert event to Supabase directly', e);
    }
  }

  // Update local store
  const store = getLocalStore();
  store.events.unshift(newEvent);
  store.device.current_state = nextState;
  if (eventData.lat && eventData.lng) {
    store.device.last_lat = eventData.lat;
    store.device.last_lng = eventData.lng;
  }
  if (eventData.address) {
    store.device.address = eventData.address;
  }
  store.device.last_heartbeat = new Date().toISOString();
  saveLocalStore(store);

  // Dispatch custom browser event for realtime listeners in same window
  window.dispatchEvent(new CustomEvent('sheshield_local_event', { detail: { event: newEvent, device: store.device } }));

  return { event: newEvent, device: store.device };
}

export function subscribeToRealtimeUpdates(wearerId, onEvent, onDeviceChange) {
  let supabaseChannel = null;

  if (isSupabaseConfigured) {
    supabaseChannel = supabase
      .channel(`sheshield-${wearerId}`)
      .on(
        'postgres_changes',
        { event: 'INSERT', schema: 'public', table: 'events', filter: `wearer_id=eq.${wearerId}` },
        (payload) => {
          console.log('⚡ [Supabase Realtime] Inbound Event Insert:', payload.new);
          if (onEvent) onEvent(payload.new);
        }
      )
      .on(
        'postgres_changes',
        { event: 'UPDATE', schema: 'public', table: 'devices', filter: `wearer_id=eq.${wearerId}` },
        (payload) => {
          console.log('⚡ [Supabase Realtime] Device State Update:', payload.new);
          if (onDeviceChange) onDeviceChange(payload.new);
        }
      )
      .subscribe();
  }

  // Local window event listener fallback
  const handleLocalEvent = (e) => {
    const { event, device } = e.detail;
    if (onEvent && event) onEvent(event);
    if (onDeviceChange && device) onDeviceChange(device);
  };
  window.addEventListener('sheshield_local_event', handleLocalEvent);

  return () => {
    if (supabaseChannel) {
      supabase.removeChannel(supabaseChannel);
    }
    window.removeEventListener('sheshield_local_event', handleLocalEvent);
  };
}
