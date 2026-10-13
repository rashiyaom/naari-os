/**
 * SheShield Sentinel - Structured SMS Payload Parser
 * Parses incoming alert SMS messages from SIM800L into structured event models.
 */

export function parseSmsPayload(rawSmsText, senderPhone = '') {
  const text = (rawSmsText || '').trim();

  // 1. Determine Event Type
  let event_type = 'location_update';

  if (
    /CRITICAL EMERGENCY|EMERGENCY ALERT|EMERGENCY SOS|\bSOS\b|HELP IS ON ITS WAY/i.test(text) &&
    !/EMERGENCY RESOLVED|CANCELLED/i.test(text)
  ) {
    event_type = 'emergency_triggered';
  } else if (/EMERGENCY RESOLVED|DISTRESS ALERT CANCELLED|CONFIRMED SAFE/i.test(text)) {
    event_type = 'emergency_resolved';
  } else if (/\[SAFETY WATCH ACTIVE\]|SAFETY WATCH ACTIVE|ENABLE SAFETY WATCH/i.test(text)) {
    event_type = 'watch_started';
  } else if (/\[SAFETY WATCH ENDED\]|SAFETY WATCH ENDED|SAFELY ENDED|WATCH CANCELLED/i.test(text)) {
    event_type = 'watch_cancelled';
  } else if (/SAFETY WATCH UPDATE|LOCATION PING|LOCATION UPDATE/i.test(text)) {
    event_type = 'location_update';
  }

  // 2. Extract Latitude and Longitude
  let lat = null;
  let lng = null;

  // Google Maps URL pattern: https://maps.google.com/?q=28.613928,77.209021
  const googleMapsMatch = text.match(/maps\.google\.com\/\?q=(-?\d+\.\d+),(-?\d+\.\d+)/i);
  if (googleMapsMatch) {
    lat = parseFloat(googleMapsMatch[1]);
    lng = parseFloat(googleMapsMatch[2]);
  } else {
    // Explicit Lat/Lng: Lat: 28.6139, Lng: 77.2090
    const explicitCoordMatch = text.match(/(?:Lat|Latitude)[:=\s]+(-?\d+\.\d+)[,\s]+(?:Lon|Lng|Longitude)[:=\s]+(-?\d+\.\d+)/i);
    if (explicitCoordMatch) {
      lat = parseFloat(explicitCoordMatch[1]);
      lng = parseFloat(explicitCoordMatch[2]);
    } else {
      // Raw CIPGSMLOC format: +CIPGSMLOC: 0,77.209021,28.613928
      const gsmLocMatch = text.match(/\+CIPGSMLOC:\s*\d+,(-?\d+\.\d+),(-?\d+\.\d+)/i);
      if (gsmLocMatch) {
        lng = parseFloat(gsmLocMatch[1]);
        lat = parseFloat(gsmLocMatch[2]);
      }
    }
  }

  // Fallback coordinates if none found
  if (lat === null || isNaN(lat)) lat = 28.6139;
  if (lng === null || isNaN(lng)) lng = 77.2090;

  // 3. Extract Trigger Mechanism
  let trigger = 'SIM800L_CELLULAR_ALERT';
  const triggerMatch = text.match(/Trigger:\s*([^\n\r]+)/i);
  if (triggerMatch) {
    trigger = triggerMatch[1].trim();
  } else if (/Fall Shock|Fall Impact/i.test(text)) {
    trigger = 'QMI8658_FALL_SHOCK';
  } else if (/Voice Keyword|Help Me|Bachao/i.test(text)) {
    trigger = 'ESP_SR_VOICE_KEYWORD';
  } else if (/SOS Button/i.test(text)) {
    trigger = 'PHYSICAL_SOS_BUTTON';
  }

  // 4. Extract Shock / Movement info
  let shock_g = null;
  const shockMatch = text.match(/(\d+\.\d+)\s*G(?:\s*Impact|\s*Shock)?/i);
  if (shockMatch) {
    shock_g = `${shockMatch[1]}G Impact`;
  }

  // 5. Extract Wearer Name
  let wearer_name = null;
  const wearerMatch = text.match(/Wearer:\s*([^\n\r,]+)/i);
  if (wearerMatch) {
    wearer_name = wearerMatch[1].trim();
  }

  // 6. Extract Timestamp
  let timestamp_str = null;
  const timeMatch = text.match(/Time:\s*([^\n\r]+)/i);
  if (timeMatch) {
    timestamp_str = timeMatch[1].trim();
  }

  // 7. Extract Evidence / Audio note
  const has_audio_evidence = /Evidence:\s*Audio|Audio Recorded|Blackbox/i.test(text);

  return {
    event_type,
    lat,
    lng,
    wearer_name,
    timestamp_str,
    sender_phone: senderPhone,
    payload: {
      trigger,
      shock_g,
      raw_sms: text,
      has_audio_evidence,
      audio_url: has_audio_evidence ? `/evidence/audio_rec_${Date.now()}.wav` : null,
      parsed_at: new Date().toISOString()
    }
  };
}
