import { motion } from "framer-motion";
import {
  BellRing,
  Compass,
  Flashlight,
  Palette,
  PhoneCall,
  Satellite,
  ShieldAlert,
  Sliders,
  TimerReset,
  Users,
} from "lucide-react";

const Reveal = ({ children, className = "" }: { children: React.ReactNode; className?: string }) => (
  <motion.div
    className={className}
    initial={{ opacity: 0, y: 16 }}
    whileInView={{ opacity: 1, y: 0 }}
    viewport={{ once: true, margin: "-60px" }}
    transition={{ duration: 0.6 }}
  >
    {children}
  </motion.div>
);

const Mono = ({ children }: { children: React.ReactNode }) => (
  <span className="font-mono text-[10px] uppercase tracking-widest">{children}</span>
);

const features = [
  {
    icon: ShieldAlert,
    title: "30s SOS Grace Window",
    text: "Real-time depleting countdown arc with hold-to-disarm slider to prevent false emergency dispatches.",
  },
  {
    icon: PhoneCall,
    title: "Cellular Dialer & Ringing Audio",
    text: "Direct Quad-Band SIM800L audio calls with 440Hz/480Hz dual-cadence telephone ringback synthesis.",
  },
  {
    icon: Sliders,
    title: "watchOS 10 Control Center",
    text: "High-density status bar (Jio 4G CSQ bars, battery pill), 6 frosted squircle tiles, volume slider & SOS strip.",
  },
  {
    icon: Palette,
    title: "5 AMOLED Wallpapers",
    text: "OLED Pitch Black, Ultra Topographic Contours, Cosmic Aurora, California Sunset Gradient & Cyber Matrix.",
  },
  {
    icon: BellRing,
    title: "105dB Sound Studio",
    text: "ES8311 DAC high-output sirens: Police Wail, 105dB Pulse, Ambulance Hi-Lo, Chirp Radar and Chimes.",
  },
  {
    icon: Flashlight,
    title: "3-Mode OLED Torch",
    text: "Ultra-bright White 100%, Amber Warm night reading, and Red Strobe Emergency beacon.",
  },
  {
    icon: Satellite,
    title: "Night Walk Guard",
    text: "Continuous Neo-6M GPS NMEA telemetry, heading 042° NE, and automated 30s guardian pings.",
  },
  {
    icon: Users,
    title: "Guardian Direct Sync",
    text: "Pre-authorized directory with 1-tap priority calling and instant GPS coordinates via SMS.",
  },
];

export function FeaturesSection() {
  return (
    <section id="features" className="blush-section py-24 md:py-32" aria-label="Key features">
      <div className="mx-auto max-w-7xl px-5 md:px-10">
        <Reveal>
          <Mono>Purpose-built capability</Mono>
          <h2 className="section-title mt-5">
            Quiet on the wrist.
            <br />
            Clear to guardians.
          </h2>
        </Reveal>

        <div className="feature-grid mt-14">
          {features.map(({ icon: Icon, title, text }, i) => (
            <Reveal className="feature-card" key={title}>
              <span className="feature-icon">
                <Icon />
              </span>
              <Mono>0{i + 1}</Mono>
              <h3>{title}</h3>
              <p>{text}</p>
            </Reveal>
          ))}
        </div>
      </div>
    </section>
  );
}
