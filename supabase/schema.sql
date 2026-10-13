-- ==============================================================================
-- SheShield Sentinel: Supabase Database Schema & Realtime Setup
-- ==============================================================================
-- Run this script in the Supabase SQL Editor (https://app.supabase.com)

-- 1. Enable UUID Extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 2. Create Users Table (Wearers)
CREATE TABLE IF NOT EXISTS public.users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    full_name TEXT NOT NULL,
    phone TEXT NOT NULL,
    email TEXT UNIQUE,
    invite_code TEXT UNIQUE NOT NULL, -- Used by trusted contacts to pair
    avatar_url TEXT,
    emergency_notes TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 3. Create Contacts Table (Trusted Contacts linked to Wearers)
CREATE TABLE IF NOT EXISTS public.contacts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    wearer_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    full_name TEXT NOT NULL,
    phone TEXT NOT NULL,
    email TEXT,
    relation TEXT NOT NULL, -- "Mother", "Father", "Sister", "Friend", "Partner"
    is_primary BOOLEAN DEFAULT FALSE,
    notify_sms BOOLEAN DEFAULT TRUE,
    notify_call BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 4. Create Devices Table (Watch Hardware Telemetry & State)
CREATE TABLE IF NOT EXISTS public.devices (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    wearer_id UUID UNIQUE NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    device_code TEXT UNIQUE NOT NULL, -- e.g. "SHESHIELD_WATCH_01"
    current_state TEXT NOT NULL DEFAULT 'safe' CHECK (current_state IN ('safe', 'watch', 'emergency')),
    battery_level INT DEFAULT 100 CHECK (battery_level BETWEEN 0 AND 100),
    gsm_signal INT DEFAULT 0 CHECK (gsm_signal BETWEEN 0 AND 31), -- CSQ Signal Scale
    last_lat DOUBLE PRECISION DEFAULT 28.6139,
    last_lng DOUBLE PRECISION DEFAULT 77.2090,
    address TEXT,
    firmware_version TEXT DEFAULT 'v1.0.0-esp32s3',
    is_online BOOLEAN DEFAULT TRUE,
    last_heartbeat TIMESTAMPTZ DEFAULT NOW(),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 5. Create Events Table (Alerts, Location breadcrumbs, SOS triggers)
CREATE TABLE IF NOT EXISTS public.events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id UUID REFERENCES public.devices(id) ON DELETE SET NULL,
    wearer_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    event_type TEXT NOT NULL CHECK (
        event_type IN (
            'watch_started', 
            'watch_cancelled', 
            'emergency_triggered', 
            'emergency_resolved', 
            'location_update',
            'low_battery_warning'
        )
    ),
    lat DOUBLE PRECISION,
    lng DOUBLE PRECISION,
    address TEXT,
    payload JSONB DEFAULT '{}'::jsonb, -- Includes trigger (voice/fall/button), audio_url, raw_sms, imu_g, etc.
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Create Indexes for fast queries
CREATE INDEX IF NOT EXISTS idx_events_wearer_id ON public.events(wearer_id);
CREATE INDEX IF NOT EXISTS idx_events_created_at ON public.events(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_users_invite_code ON public.users(invite_code);
CREATE INDEX IF NOT EXISTS idx_devices_wearer_id ON public.devices(wearer_id);

-- 6. Enable Supabase Realtime Replication on Events & Devices
ALTER PUBLICATION supabase_realtime ADD TABLE public.events;
ALTER PUBLICATION supabase_realtime ADD TABLE public.devices;

-- 7. Row Level Security (RLS) Configuration
ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.contacts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.events ENABLE ROW LEVEL SECURITY;

-- Allow public / anon read access for dashboard paired via invite code
CREATE POLICY "Public read for users" ON public.users FOR SELECT USING (true);
CREATE POLICY "Public read for contacts" ON public.contacts FOR SELECT USING (true);
CREATE POLICY "Public read for devices" ON public.devices FOR SELECT USING (true);
CREATE POLICY "Public read for events" ON public.events FOR SELECT USING (true);

-- Allow backend service role (or anon during demo) to insert and update
CREATE POLICY "Service insert events" ON public.events FOR INSERT WITH CHECK (true);
CREATE POLICY "Service update devices" ON public.devices FOR UPDATE USING (true);
CREATE POLICY "Service insert contacts" ON public.contacts FOR INSERT WITH CHECK (true);

-- ==============================================================================
-- 8. Seed Initial Demo Data
-- ==============================================================================
INSERT INTO public.users (id, full_name, phone, email, invite_code, avatar_url, emergency_notes)
VALUES (
    'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d',
    'Aanya Sharma',
    '+91 98765 43210',
    'aanya.sharma@example.com',
    'SENTINEL-7892',
    'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200&auto=format&fit=crop&q=80',
    'Blood Group: B+. Regular route: Central Metro to Cyber City.'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO public.contacts (id, wearer_id, full_name, phone, email, relation, is_primary, notify_sms, notify_call)
VALUES
    ('c1111111-1111-1111-1111-111111111111', 'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d', 'Rajesh Sharma', '+91 98765 00001', 'rajesh.sharma@example.com', 'Father', true, true, true),
    ('c2222222-2222-2222-2222-222222222222', 'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d', 'Maya Sharma', '+91 98765 00002', 'maya.sharma@example.com', 'Sister', false, true, false),
    ('c3333333-3333-3333-3333-333333333333', 'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d', 'Arjun Kapoor', '+91 98765 00003', 'arjun.k@example.com', 'Friend', false, true, false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.devices (id, wearer_id, device_code, current_state, battery_level, gsm_signal, last_lat, last_lng, address, firmware_version, is_online, last_heartbeat)
VALUES (
    'd1e2f3a4-b5c6-7d8e-9f0a-1b2c3d4e5f6a',
    'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d',
    'SHESHIELD_WATCH_01',
    'safe',
    88,
    26,
    28.6139,
    77.2090,
    'Connaught Place, Inner Circle, New Delhi',
    'v1.0.0-esp32s3',
    true,
    NOW()
) ON CONFLICT (id) DO NOTHING;

INSERT INTO public.events (id, device_id, wearer_id, event_type, lat, lng, address, payload, created_at)
VALUES 
    (
        uuid_generate_v4(),
        'd1e2f3a4-b5c6-7d8e-9f0a-1b2c3d4e5f6a',
        'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d',
        'watch_started',
        28.6135,
        77.2085,
        'Metro Gate 4, Connaught Place',
        '{"trigger": "USER_MANUAL", "mode": "Night Walk Guard", "battery": 92}'::jsonb,
        NOW() - INTERVAL '45 minutes'
    ),
    (
        uuid_generate_v4(),
        'd1e2f3a4-b5c6-7d8e-9f0a-1b2c3d4e5f6a',
        'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d',
        'location_update',
        28.6142,
        77.2092,
        'Barakhamba Road Crossing, New Delhi',
        '{"speed_kmh": 4.5, "battery": 90, "gsm_csq": 26}'::jsonb,
        NOW() - INTERVAL '25 minutes'
    ),
    (
        uuid_generate_v4(),
        'd1e2f3a4-b5c6-7d8e-9f0a-1b2c3d4e5f6a',
        'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d',
        'watch_cancelled',
        28.6139,
        77.2090,
        'Connaught Place, Inner Circle, New Delhi',
        '{"reason": "SAFE_CONFIRMED", "duration_sec": 1200}'::jsonb,
        NOW() - INTERVAL '5 minutes'
    );
