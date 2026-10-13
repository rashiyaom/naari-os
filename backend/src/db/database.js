/**
 * SheShield Sentinel - In-Memory / Persistent Data Store
 */

export const db = {
  devices: [
    {
      id: "SHESHIELD_01",
      userName: "Aanya Sharma",
      status: "ARMED",
      batteryPercent: 88,
      gsmSignal: 24,
      lastLatitude: 28.6139,
      lastLongitude: 77.2090,
      updatedAt: new Date().toISOString()
    }
  ],
  emergencyContacts: [
    { id: "c1", name: "Papa (Primary)", phone: "+91 98765 43210", relation: "Father", notifySms: true, notifyCall: true },
    { id: "c2", name: "Rohan (Brother)", phone: "+91 98111 22334", relation: "Brother", notifySms: true, notifyCall: false },
    { id: "c3", name: "City PCR Helpline", phone: "112", relation: "Police", notifySms: true, notifyCall: true }
  ],
  incidents: [
    {
      id: "INC-8921",
      deviceId: "SHESHIELD_01",
      timestamp: new Date().toISOString(),
      alertType: "FALL_IMPACT",
      status: "RESOLVED",
      latitude: 28.6145,
      longitude: 77.2095,
      audioClipUrl: "/evidence/evidence_INC-8921.wav",
      smsDispatched: 3,
      notes: "False alarm - tripping on stairs, resolved via watch pass-code."
    }
  ],

  addIncident(incident) {
    const newInc = {
      id: `INC-${Math.floor(1000 + Math.random() * 9000)}`,
      timestamp: new Date().toISOString(),
      status: "ACTIVE",
      ...incident
    };
    this.incidents.unshift(newInc);
    return newInc;
  },

  resolveIncident(id, notes) {
    const inc = this.incidents.find(i => i.id === id);
    if (inc) {
      inc.status = "RESOLVED";
      inc.resolvedAt = new Date().toISOString();
      if (notes) inc.notes = notes;
    }
    return inc;
  },

  updateDeviceTelemetry(deviceId, data) {
    const dev = this.devices.find(d => d.id === deviceId);
    if (dev) {
      Object.assign(dev, data, { updatedAt: new Date().toISOString() });
      return dev;
    }
    return null;
  }
};
