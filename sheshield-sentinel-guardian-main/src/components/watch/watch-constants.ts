export const SCREENS = {
  SAFE: 'safe',
  EMERGENCY: 'emergency',
  WATCH: 'watch',
  CANCEL: 'cancel',
  CALLING: 'calling',
  SMS_SENT: 'sms_sent',
  LAUNCHPAD: 'launchpad',
  CONTROL_CENTER: 'control_center',
  DIALER: 'dialer',
  RECORDER: 'recorder',
  FLASHLIGHT: 'flashlight',
  SOUND_STUDIO: 'sound_studio',
  WALLPAPERS: 'wallpapers',
  TELEMETRY: 'telemetry',
  CONTACTS: 'contacts',
  SYSTEM: 'system',
} as const;

export type WatchScreenType = typeof SCREENS[keyof typeof SCREENS];

// ─── 5 AMOLED Watch Face Wallpapers ──────────────────────────────────────────
export const WALLPAPERS = [
  { id: 'oled_black',  name: 'Obsidian Minimal',   type: 'color',    value: '#000000', thumb: '#000000', sub: 'Pure OLED Pitch Black' },
  { id: 'topographic', name: 'Ultra Topographic',  type: 'image',    value: '/wallpapers/topo.jpg', thumb: '#0f291e', sub: 'Neon Emerald & Cyan Contours' },
  { id: 'aurora',      name: 'Cosmic Aurora',      type: 'image',    value: '/wallpapers/aurora.jpg', thumb: '#160d38', sub: 'Ethereal Violet & Glow' },
  { id: 'sunset',      name: 'Sunset Gradient',    type: 'image',    value: '/wallpapers/sunset.jpg', thumb: '#2e1430', sub: 'Warm Amber Dusk Horizon' },
  { id: 'cyberpunk',   name: 'Cyber Matrix',       type: 'gradient', value: 'radial-gradient(ellipse at 50% 30%, #0d2822 0%, #04090b 60%, #000000 100%)', thumb: '#081c17', sub: 'Tactical Radar Grid' }
];

// ─── 12 Apps across 3 Stacks of 4 Apps ───────────────────────────────────────
export const DRAWER_APPS = [
  // ── Page 0 : Core Safety ──────────────────────────────────────
  {
    id: 'sos',
    name: 'Emergency SOS',
    sub: '1-Tap Urgent Alert',
    iconSrc: '/icons/icon_sos.png',
    screen: SCREENS.EMERGENCY,
    badge: 'PRIORITY',
    badgeCol: '#ff453a'
  },
  {
    id: 'phone',
    name: 'Phone & Dialer',
    sub: 'Keypad & Outgoing Calls',
    iconSrc: '/icons/icon_phone.png',
    screen: SCREENS.DIALER,
    badge: 'READY',
    badgeCol: '#30d158'
  },
  {
    id: 'guard',
    name: 'Night Walk Guard',
    sub: 'Fall & Walk Monitor',
    iconSrc: '/icons/icon_guard.png',
    screen: SCREENS.WATCH,
    badge: 'ACTIVE',
    badgeCol: '#ffd60a'
  },
  {
    id: 'recorder',
    name: 'Voice Recorder',
    sub: 'Record & Hear Audio Memo',
    iconSrc: '/icons/icon_sounds.png',
    screen: SCREENS.RECORDER,
    badge: 'MIC',
    badgeCol: '#af52de'
  },

  // ── Page 1 : Utilities & Guardians ────────────────────────────
  {
    id: 'sounds',
    name: 'Sound Studio',
    sub: 'SOS Tone Studio',
    iconSrc: '/icons/icon_sounds.png',
    screen: SCREENS.SOUND_STUDIO,
    badge: 'ARMED',
    badgeCol: '#ff9f0a'
  },
  {
    id: 'torch',
    name: 'Flashlight Torch',
    sub: 'Full-Screen Pure Illumination',
    iconSrc: '/icons/icon_torch.png',
    screen: SCREENS.FLASHLIGHT,
    badge: 'OLED',
    badgeCol: '#ffffff'
  },
  {
    id: 'contacts',
    name: 'Guardians Directory',
    sub: '4 Contacts Synced',
    iconSrc: '/icons/icon_contacts.png',
    screen: SCREENS.CONTACTS,
    badge: 'SYNCED',
    badgeCol: '#0a84ff'
  },
  {
    id: 'system',
    name: 'System Health',
    sub: 'Sensors, SIM & Battery',
    iconSrc: '/icons/icon_system.png',
    screen: SCREENS.SYSTEM,
    badge: 'HEALTHY',
    badgeCol: '#30d158'
  },

  // ── Page 2 : Preferences & Tools ──────────────────────────────
  {
    id: 'wallpapers',
    name: 'Wallpapers',
    sub: '5 AMOLED Faces',
    iconSrc: '/icons/icon_control.png',
    screen: SCREENS.WALLPAPERS,
    badge: 'FACES',
    badgeCol: '#30b0c7'
  },
  {
    id: 'compass',
    name: 'Compass & IMU',
    sub: 'Heading & Motion Tilt',
    iconSrc: '/icons/icon_compass.png',
    screen: SCREENS.TELEMETRY,
    badge: '042° NE',
    badgeCol: '#30b0c7'
  },
  {
    id: 'control',
    name: 'Control Center',
    sub: 'Radios, Battery & Torch',
    iconSrc: '/icons/icon_control.png',
    screen: SCREENS.CONTROL_CENTER,
    badge: 'QUICK',
    badgeCol: '#8e8e93'
  },
  {
    id: 'cancel',
    name: 'Disarm PIN Pad',
    sub: 'Cancel Distress Window',
    iconSrc: '/icons/icon_keypad.png',
    screen: SCREENS.CANCEL,
    badge: 'SECURE',
    badgeCol: '#64d2ff'
  }
];
