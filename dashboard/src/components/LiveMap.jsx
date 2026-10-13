import React, { useEffect, useRef, useState } from 'react';
import { MapPin, Navigation, ExternalLink, RefreshCw, Compass, Route, Layers } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

export default function LiveMap() {
  const { device, events } = useAuth();
  const mapContainerRef = useRef(null);
  const mapInstanceRef = useRef(null);
  const markerRef = useRef(null);
  const pathPolylineRef = useRef(null);
  const historyMarkersRef = useRef([]);

  const [showPath, setShowPath] = useState(true);

  const currentLat = device?.last_lat || 28.6139;
  const currentLng = device?.last_lng || 77.2090;
  const currentState = device?.current_state || 'safe';

  // Extract coordinate breadcrumbs from recent events
  const locationHistory = events
    .filter(e => e.lat && e.lng)
    .slice(0, 15)
    .map(e => ({
      lat: e.lat,
      lng: e.lng,
      type: e.event_type,
      time: new Date(e.created_at).toLocaleTimeString(),
      address: e.address
    }));

  // Initialize and update Leaflet Map
  useEffect(() => {
    if (!mapContainerRef.current) return;

    // Create Map instance if not created
    if (!mapInstanceRef.current) {
      const map = L.map(mapContainerRef.current, {
        center: [currentLat, currentLng],
        zoom: 15,
        zoomControl: false,
        attributionControl: false
      });

      // Dark theme map tiles (CartoDB DarkMatter or OSM)
      L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
        maxZoom: 19,
        subdomains: 'abcd',
      }).addTo(map);

      // Add custom zoom control on bottom right
      L.control.zoom({ position: 'bottomright' }).addTo(map);

      mapInstanceRef.current = map;
    }

    const map = mapInstanceRef.current;

    // Update center
    map.setView([currentLat, currentLng], 15, { animate: true });

    // Custom pulsed icon for current location
    const pulseClass = currentState === 'emergency' ? 'emergency-pulse' : (currentState === 'watch' ? 'watch-pulse' : 'safe-pulse');
    const markerColor = currentState === 'emergency' ? '#ef4444' : (currentState === 'watch' ? '#f59e0b' : '#2dd4bf');

    const customIcon = L.divIcon({
      className: 'custom-map-pin',
      html: `
        <div class="map-pulsing-marker ${pulseClass}">
          <div class="marker-core" style="background-color: ${markerColor}"></div>
        </div>
      `,
      iconSize: [32, 32],
      iconAnchor: [16, 16]
    });

    if (markerRef.current) {
      markerRef.current.setLatLng([currentLat, currentLng]);
      markerRef.current.setIcon(customIcon);
    } else {
      markerRef.current = L.marker([currentLat, currentLng], { icon: customIcon }).addTo(map);
    }

    // Clean up old history markers
    historyMarkersRef.current.forEach(m => map.removeLayer(m));
    historyMarkersRef.current = [];

    // Draw historical breadcrumbs trail
    if (pathPolylineRef.current) {
      map.removeLayer(pathPolylineRef.current);
    }

    if (locationHistory.length > 1 && showPath) {
      const latlngs = locationHistory.map(p => [p.lat, p.lng]);
      pathPolylineRef.current = L.polyline(latlngs, {
        color: '#2dd4bf',
        weight: 3,
        opacity: 0.7,
        dashArray: '4, 8'
      }).addTo(map);

      // Add small dots for past breadcrumb locations
      locationHistory.slice(1).forEach((p, idx) => {
        const dotIcon = L.divIcon({
          className: 'breadcrumb-dot',
          html: `<div style="width: 8px; height: 8px; border-radius: 50%; background: #94a3b8; border: 1px solid #1e293b;" title="${p.time}"></div>`,
          iconSize: [8, 8],
          iconAnchor: [4, 4]
        });
        const dot = L.marker([p.lat, p.lng], { icon: dotIcon }).addTo(map);
        historyMarkersRef.current.push(dot);
      });
    }

    return () => {
      // Cleanup on unmount if needed
    };
  }, [currentLat, currentLng, currentState, showPath, events]);

  const handleRecenter = () => {
    if (mapInstanceRef.current) {
      mapInstanceRef.current.setView([currentLat, currentLng], 16, { animate: true });
    }
  };

  return (
    <div className="glass-panel live-map-card">
      <div className="map-card-header">
        <div className="header-left">
          <MapPin size={18} color="var(--watch-mint)" />
          <div>
            <h3 className="map-title">Location Map & Alert Breadcrumbs</h3>
            <span className="map-subtitle">
              Cell-tower triangulation via SIM800L AT+CIPGSMLOC • Demo mode: fixed seed coords
            </span>
          </div>
        </div>

        <div className="map-header-actions">
          <button
            onClick={() => setShowPath(!showPath)}
            className={`map-tool-btn ${showPath ? 'active' : ''}`}
            title="Toggle Historical Path"
          >
            <Route size={15} />
            <span>{showPath ? 'Hide Trail' : 'Show Trail'}</span>
          </button>

          <button
            onClick={handleRecenter}
            className="map-tool-btn"
            title="Recenter Map on Wearer"
          >
            <Compass size={15} />
            <span>Recenter</span>
          </button>

          <a
            href={`https://maps.google.com/?q=${currentLat},${currentLng}`}
            target="_blank"
            rel="noreferrer"
            className="map-external-link"
          >
            <span>Open Google Maps</span>
            <ExternalLink size={13} />
          </a>
        </div>
      </div>

      {/* Real Interactive Leaflet Map Container */}
      <div className="map-viewport-wrapper">
        <div ref={mapContainerRef} className="leaflet-map-canvas" />

        {/* Floating Address Bar */}
        <div className="map-floating-overlay glass-panel">
          <div className="address-row">
            <Navigation size={15} color="var(--watch-mint)" />
            <div className="address-text">
              <strong>{device?.address || 'Connaught Place, Inner Circle, New Delhi'}</strong>
              <div className="coords-text mono">
                Lat: {currentLat.toFixed(4)}° N • Lng: {currentLng.toFixed(4)}° E • Est. accuracy: ~500m–1.5km
              </div>
            </div>
          </div>
          <div className="history-pill mono">
            {locationHistory.length} cell-tower location ping{locationHistory.length !== 1 ? 's' : ''} this session
          </div>
        </div>
      </div>
    </div>
  );
}
