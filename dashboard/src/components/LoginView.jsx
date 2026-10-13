import React, { useState } from 'react';
import { Shield, Lock, UserCheck, Key, ArrowRight, ShieldCheck, Smartphone, AlertCircle, Database } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { isSupabaseConfigured } from '../services/supabase';

export default function LoginView({ onOpenDbConfig }) {
  const { login, error } = useAuth();
  const [inviteCode, setInviteCode] = useState('');
  const [contactName, setContactName] = useState('Rajesh Sharma');
  const [relation, setRelation] = useState('Father');
  const [submitting, setSubmitting] = useState(false);
  const [localErr, setLocalErr] = useState('');

  const handleLogin = async (e) => {
    e?.preventDefault();
    if (!inviteCode.trim()) {
      setLocalErr('Please enter the wearer invite code.');
      return;
    }
    setSubmitting(true);
    setLocalErr('');
    try {
      await login(inviteCode, contactName, relation);
    } catch (err) {
      setLocalErr(err.message || 'Login failed. Please check the code.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleQuickDemo = (code = 'SENTINEL-7892') => {
    setInviteCode(code);
    setSubmitting(true);
    setLocalErr('');
    login(code, 'Rajesh Sharma', 'Father')
      .catch((err) => setLocalErr(err.message))
      .finally(() => setSubmitting(false));
  };

  return (
    <div className="login-container">
      <div className="login-card glass-panel">
        {/* Brand Header */}
        <div className="login-header">
          <div className="login-brand-badge">
            <Shield size={28} color="#2dd4bf" strokeWidth={2.4} />
          </div>
          <h2>Naari <span style={{ color: 'var(--watch-mint)' }}>OS</span></h2>
          <p className="login-subtext">
            Guardian Portal • Real-Time Safety & Distress Dashboard
          </p>
        </div>

        {/* Database Status Tag */}
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          background: 'rgba(255, 255, 255, 0.03)',
          padding: '8px 14px',
          borderRadius: '10px',
          border: '1px solid var(--border)',
          marginBottom: '20px',
          fontSize: '12px'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span style={{
              width: '8px',
              height: '8px',
              borderRadius: '50%',
              backgroundColor: isSupabaseConfigured ? 'var(--watch-mint)' : '#f59e0b',
              boxShadow: isSupabaseConfigured ? '0 0 8px #2dd4bf' : '0 0 8px #f59e0b'
            }} />
            <span style={{ color: 'var(--watch-soft)' }}>
              {isSupabaseConfigured ? 'Supabase Realtime Live' : 'Demo Mode (Local Realtime Active)'}
            </span>
          </div>
          <button
            type="button"
            onClick={onOpenDbConfig}
            style={{
              background: 'transparent',
              border: 'none',
              color: 'var(--watch-mint)',
              fontSize: '11px',
              fontWeight: 600,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: '4px'
            }}
          >
            <Database size={13} /> Schema & API Key
          </button>
        </div>

        {(localErr || error) && (
          <div className="login-error-box">
            <AlertCircle size={16} />
            <span>{localErr || error}</span>
          </div>
        )}

        {/* Login Form */}
        <form onSubmit={handleLogin} className="login-form">
          <div className="form-group">
            <label>
              <Key size={14} /> Wearer Invite Code
            </label>
            <input
              type="text"
              placeholder="e.g. SENTINEL-7892"
              value={inviteCode}
              onChange={(e) => setInviteCode(e.target.value.toUpperCase())}
              className="mono"
              required
            />
            <span className="input-hint">
              Ask the watch wearer for the invite code shown on their Naari OS watch or app.
            </span>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1.2fr 1fr', gap: '12px' }}>
            <div className="form-group">
              <label>Your Name</label>
              <input
                type="text"
                placeholder="e.g. Rajesh Sharma"
                value={contactName}
                onChange={(e) => setContactName(e.target.value)}
                required
              />
            </div>
            <div className="form-group">
              <label>Relationship</label>
              <select
                value={relation}
                onChange={(e) => setRelation(e.target.value)}
                style={{
                  background: 'var(--watch-surface)',
                  border: '1px solid var(--watch-line)',
                  borderRadius: '10px',
                  color: '#fff',
                  height: '42px',
                  padding: '0 12px',
                  outline: 'none',
                  fontSize: '13px'
                }}
              >
                <option value="Father">Father</option>
                <option value="Mother">Mother</option>
                <option value="Sister">Sister</option>
                <option value="Brother">Brother</option>
                <option value="Partner">Partner / Spouse</option>
                <option value="Friend">Friend</option>
                <option value="Guardian">Guardian</option>
              </select>
            </div>
          </div>

          <button
            type="submit"
            disabled={submitting}
            className="login-btn-primary"
          >
            {submitting ? 'Authenticating...' : (
              <>
                <span>Access Guardian Dashboard</span>
                <ArrowRight size={18} />
              </>
            )}
          </button>
        </form>

        {/* Quick Demo Login Preset */}
        <div className="login-demo-section">
          <div className="demo-divider">
            <span />
            <small>INSTANT EVALUATION</small>
            <span />
          </div>

          <button
            type="button"
            onClick={() => handleQuickDemo('SENTINEL-7892')}
            className="demo-preset-btn"
          >
            <UserCheck size={16} color="var(--watch-mint)" />
            <div style={{ textAlign: 'left' }}>
              <strong>Log In as Guardian for Aanya Sharma</strong>
              <div className="mono" style={{ fontSize: '11px', color: 'var(--watch-muted)' }}>
                Code: SENTINEL-7892 • Shake-to-SOS • Watch Mode • Live Telemetry
              </div>
            </div>
          </button>
        </div>

        {/* Trust Badges */}
        <div className="login-footer-badges">
          <div><ShieldCheck size={14} color="var(--watch-mint)" /> E2E Encrypted Telemetry</div>
          <div><Smartphone size={14} color="var(--watch-soft)" /> SIM800L Cellular Redundancy</div>
        </div>
      </div>
    </div>
  );
}
