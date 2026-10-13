import React, { createContext, useContext, useState, useEffect } from 'react';
import { loginWithInviteCode, subscribeToRealtimeUpdates, fetchEventHistory } from '../services/supabase';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [wearer, setWearer] = useState(null);
  const [contact, setContact] = useState(null);
  const [device, setDevice] = useState(null);
  const [contactsList, setContactsList] = useState([]);
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Restore stored session if exists
  useEffect(() => {
    try {
      const saved = localStorage.getItem('sheshield_auth_session');
      if (saved) {
        const parsed = JSON.parse(saved);
        setWearer(parsed.user);
        setContact(parsed.contact || { full_name: 'Rajesh Sharma', relation: 'Father' });
        setDevice(parsed.device);
        setContactsList(parsed.contacts || []);
        loadEvents(parsed.user.id);
      }
    } catch (e) {
      console.error('Session load error', e);
    } finally {
      setLoading(false);
    }
  }, []);

  async function loadEvents(wearerId) {
    try {
      const hist = await fetchEventHistory(wearerId);
      setEvents(hist);
    } catch (e) {
      console.warn('Failed to load events', e);
    }
  }

  // Subscribe to Realtime Postgres / Local updates whenever wearer changes
  useEffect(() => {
    if (!wearer) return;

    const unsubscribe = subscribeToRealtimeUpdates(
      wearer.id,
      (newEvent) => {
        setEvents((prev) => [newEvent, ...prev.filter(e => e.id !== newEvent.id)]);
      },
      (updatedDevice) => {
        setDevice((prev) => ({ ...prev, ...updatedDevice }));
      }
    );

    return () => unsubscribe();
  }, [wearer]);

  const login = async (inviteCode, customContactName = 'Trusted Guardian', customRelation = 'Guardian') => {
    setError(null);
    setLoading(true);
    try {
      const result = await loginWithInviteCode(inviteCode);
      const contactObj = {
        id: `c-${Date.now()}`,
        full_name: customContactName,
        relation: customRelation,
        phone: '+91 98765 00001'
      };

      setWearer(result.user);
      setContact(contactObj);
      setDevice(result.device);
      setContactsList(result.contacts);

      localStorage.setItem('sheshield_auth_session', JSON.stringify({
        user: result.user,
        contact: contactObj,
        device: result.device,
        contacts: result.contacts
      }));

      await loadEvents(result.user.id);
      setLoading(false);
      return result;
    } catch (err) {
      setLoading(false);
      setError(err.message || 'Login failed');
      throw err;
    }
  };

  const logout = () => {
    localStorage.removeItem('sheshield_auth_session');
    setWearer(null);
    setContact(null);
    setDevice(null);
    setEvents([]);
  };

  return (
    <AuthContext.Provider
      value={{
        wearer,
        contact,
        device,
        setDevice,
        contactsList,
        events,
        setEvents,
        loading,
        error,
        login,
        logout,
        refreshEvents: () => wearer && loadEvents(wearer.id)
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within an AuthProvider');
  return context;
}
