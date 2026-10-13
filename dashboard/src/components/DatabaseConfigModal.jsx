import React, { useState } from 'react';
import { X, Database, Copy, Check, Terminal, Code2, Server, Shield } from 'lucide-react';
import { isSupabaseConfigured } from '../services/supabase';

export default function DatabaseConfigModal({ onClose }) {
  const [copiedSection, setCopiedSection] = useState(null);

  const envSnippet = `# .env (in dashboard/ and backend/)
VITE_SUPABASE_URL=https://your-project.supabase.co
VITE_SUPABASE_ANON_KEY=eyJhbGciOi...your_anon_key...
SUPABASE_SERVICE_ROLE_KEY=eyJhbGciOi...your_service_role_key...`;

  const nodeBackendSnippet = `// external-alert-service.js (Node.js backend alert ingestor)
import { createClient } from '@supabase/supabase-js';

const supabase = createClient(
  process.env.SUPABASE_URL,
  process.env.SUPABASE_SERVICE_ROLE_KEY // Service role bypasses RLS
);

// Function to call when an SMS or webhook arrives from SIM800L
export async function ingestWatchAlert({ deviceId, wearerId, alertType, lat, lng, payload }) {
  // 1. Insert into 'events' table -> immediately broadcasts over Supabase Realtime
  const { data: event, error: eventErr } = await supabase
    .from('events')
    .insert([
      {
        wearer_id: wearerId,
        device_id: deviceId,
        event_type: alertType, // 'emergency_triggered' | 'watch_started' | 'location_update'
        lat: lat,
        lng: lng,
        payload: payload // { trigger: 'VOICE_HELP_ME', audio_url: '...', battery: 88 }
      }
    ])
    .select()
    .single();

  if (eventErr) throw eventErr;

  // 2. Update the device's state
  await supabase
    .from('devices')
    .update({
      current_state: alertType === 'emergency_triggered' ? 'emergency' : (alertType === 'watch_started' ? 'watch' : 'safe'),
      last_lat: lat,
      last_lng: lng,
      last_heartbeat: new Date().toISOString()
    })
    .eq('id', deviceId);

  return event;
}`;

  const copyToClipboard = (text, sectionId) => {
    navigator.clipboard.writeText(text);
    setCopiedSection(sectionId);
    setTimeout(() => setCopiedSection(null), 2000);
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-content glass-panel db-config-modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <div className="modal-header-title">
            <div className="modal-icon-badge badge-watch">
              <Database size={22} color="var(--watch-mint)" />
            </div>
            <div>
              <h2>Supabase Database & External Backend Wiring</h2>
              <span className="modal-event-id">
                Schema Definition, Realtime Subscription, & Node.js External Ingestor
              </span>
            </div>
          </div>
          <button onClick={onClose} className="modal-close-btn">
            <X size={20} />
          </button>
        </div>

        <div className="modal-body">
          {/* Status Alert */}
          <div style={{
            background: isSupabaseConfigured ? 'rgba(45, 212, 191, 0.1)' : 'rgba(245, 158, 11, 0.1)',
            border: `1px solid ${isSupabaseConfigured ? 'var(--watch-mint)' : '#f59e0b'}`,
            padding: '12px 16px',
            borderRadius: '10px',
            marginBottom: '18px',
            fontSize: '13px'
          }}>
            <strong>Current Connection Status: </strong>
            <span>{isSupabaseConfigured ? 'Connected to live Supabase Postgres project with Realtime replication enabled.' : 'Operating in Standalone/Demo mode. Follow the 3 steps below to connect your real Supabase project.'}</span>
          </div>

          {/* Step 1: SQL Schema */}
          <div className="modal-section">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
              <h4 className="section-title" style={{ margin: 0 }}>
                <Terminal size={15} color="var(--watch-mint)" />
                1. Supabase SQL Schema (`supabase/schema.sql`)
              </h4>
            </div>
            <p style={{ fontSize: '12.5px', color: 'var(--watch-soft)', marginBottom: '8px' }}>
              Execute the complete schema file found in <code className="mono">/supabase/schema.sql</code> inside your Supabase Project SQL Editor. It creates the 4 tables (<code className="mono">users</code>, <code className="mono">contacts</code>, <code className="mono">devices</code>, <code className="mono">events</code>) and enables Realtime publication replication.
            </p>
          </div>

          {/* Step 2: Environment Variables */}
          <div className="modal-section">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
              <h4 className="section-title" style={{ margin: 0 }}>
                <Code2 size={15} color="var(--watch-mint)" />
                2. Frontend Environment Variables (`.env`)
              </h4>
              <button
                onClick={() => copyToClipboard(envSnippet, 'env')}
                className="code-copy-btn"
              >
                {copiedSection === 'env' ? <Check size={13} color="var(--watch-mint)" /> : <Copy size={13} />}
                <span>{copiedSection === 'env' ? 'Copied' : 'Copy'}</span>
              </button>
            </div>
            <pre className="code-block mono">{envSnippet}</pre>
          </div>

          {/* Step 3: Node.js External Service Integration */}
          <div className="modal-section">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
              <h4 className="section-title" style={{ margin: 0 }}>
                <Server size={15} color="var(--watch-mint)" />
                3. Node.js External Ingestor Service (SIM800L / SMS Webhook Relay)
              </h4>
              <button
                onClick={() => copyToClipboard(nodeBackendSnippet, 'node')}
                className="code-copy-btn"
              >
                {copiedSection === 'node' ? <Check size={13} color="var(--watch-mint)" /> : <Copy size={13} />}
                <span>{copiedSection === 'node' ? 'Copied' : 'Copy Code'}</span>
              </button>
            </div>
            <p style={{ fontSize: '12px', color: 'var(--watch-muted)', marginBottom: '8px' }}>
              When your separate backend service receives an alert or SMS from SIM800L, call this function. The Supabase Realtime channel will automatically broadcast the row insert to update this dashboard immediately.
            </p>
            <pre className="code-block mono" style={{ maxHeight: '200px', overflowY: 'auto' }}>
              {nodeBackendSnippet}
            </pre>
          </div>
        </div>
      </div>
    </div>
  );
}
