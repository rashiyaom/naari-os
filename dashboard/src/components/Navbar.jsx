import React from 'react';
import { Shield, LogOut, Radio, Battery, BatteryCharging, Signal, RefreshCw, Database, User, Bell } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { isSupabaseConfigured } from '../services/supabase';

export default function Navbar({ onOpenDbConfig, onSwitchToWatch }) {
  const { wearer, contact, device, logout, refreshEvents } = useAuth();

  const currentState = device?.current_state || 'safe';

  return (
    <header className="main-navbar glass-panel">
      {/* Brand & Wearer Association */}
      <div className="nav-left">
        <div className="nav-brand-logo">
          <Shield size={20} strokeWidth={2.4} />
        </div>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span className="brand-title">
              Naari <span style={{ color: 'var(--watch-mint)' }}>OS</span>
            </span>
            <span className="guardian-tag">
              GUARDIAN PORTAL
            </span>
          </div>
          <div className="nav-wearer-subtitle">
            Guarding: <strong>{wearer?.full_name || 'Wearer'}</strong> ({contact?.relation || 'Guardian'})
          </div>
        </div>
      </div>

      {/* Real-time State & Live Status Pills */}
      <div className="nav-center">
        {/* State Pill */}
        <div className={`nav-state-pill state-${currentState}`}>
          <span className="state-pulse-dot" />
          <span className="state-text">
            {currentState === 'emergency' && 'EMERGENCY DISTRESS'}
            {currentState === 'watch' && 'SAFETY WATCH ACTIVE'}
            {currentState === 'safe' && 'ALL CLEAR · SAFE'}
          </span>
        </div>

        {/* Battery & GSM Signal */}
        <div className="nav-telemetry-pills">
          <div className="pill-item" title="AXP2101 PMIC Battery Gauge">
            <BatteryCharging size={15} color="var(--watch-mint)" />
            <span>{device?.battery_level || 88}%</span>
          </div>
          <div className="pill-item" title="SIM800L Cellular GSM Signal (CSQ scale 0-31)">
            <Signal size={15} color="var(--watch-soft)" />
            <span>{device?.gsm_signal || 26}/31</span>
          </div>
        </div>
      </div>

      {/* Actions & Profile */}
      <div className="nav-right">
        {/* Switch to Full Watch OS Display */}
        {onSwitchToWatch && (
          <button
            onClick={onSwitchToWatch}
            className="nav-icon-btn"
            style={{ background: 'rgba(0, 232, 168, 0.15)', borderColor: 'rgba(0, 232, 168, 0.35)', color: '#00e8a8' }}
            title="Switch to Fullscreen Watch OS Display"
          >
            <span style={{ fontSize: '13px' }}>⌚</span>
            <span style={{ fontSize: '11px', fontWeight: 700 }}>Watch OS</span>
          </button>
        )}

        {/* Supabase Connection Status Button */}
        <button
          onClick={onOpenDbConfig}
          className="nav-icon-btn"
          title="Supabase Schema & External Backend Config"
        >
          <Database size={17} color={isSupabaseConfigured ? 'var(--watch-mint)' : '#f59e0b'} />
          <span style={{ fontSize: '11px', fontWeight: 600 }}>
            {isSupabaseConfigured ? 'Supabase' : 'Schema/API'}
          </span>
        </button>

        {/* Refresh Live Events */}
        <button
          onClick={refreshEvents}
          className="nav-icon-btn"
          title="Refresh Events"
        >
          <RefreshCw size={17} />
        </button>

        {/* User / Logout */}
        <div className="nav-user-dropdown">
          <div className="user-avatar-badge">
            {contact?.full_name?.charAt(0) || 'G'}
          </div>
          <button
            onClick={logout}
            className="nav-logout-btn"
            title="Log Out"
          >
            <LogOut size={16} />
          </button>
        </div>
      </div>
    </header>
  );
}
