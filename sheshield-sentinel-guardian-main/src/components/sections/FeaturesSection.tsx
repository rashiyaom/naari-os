import { motion } from "framer-motion";
import {
  BellRing,
  Flashlight,
  Palette,
  PhoneCall,
  ShieldAlert,
  Sliders,
  Activity,
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
    text: "A visible countdown gives the wearer time to cancel; stopping an active SOS requires a deliberate 3-second hold.",
  },
  {
    icon: PhoneCall,
    title: "Android Safety Relay",
    text: "The watch requests calls, SMS and location actions through the paired Android phone and receives a clear result.",
  },
  {
    icon: Sliders,
    title: "Remote Watch Control",
    text: "Guardians, SOS tone, watch settings and device state reconcile over the versioned BLE protocol.",
  },
  {
    icon: Palette,
    title: "5 AMOLED Watch Faces",
    text: "Five hardware-rendered faces and a compact app drawer designed for the 410×502 AMOLED display.",
  },
  {
    icon: BellRing,
    title: "Voice Memo Library",
    text: "Record, browse, play and delete up to six watch memos, then transfer verified audio to the phone.",
  },
  {
    icon: Flashlight,
    title: "3-Mode Attention Torch",
    text: "White light, warm light and a fast red-blue attention strobe for visibility during an incident.",
  },
  {
    icon: Activity,
    title: "Fall Monitoring",
    text: "QMI8658 motion sensing evaluates impact and post-impact posture; calibration and real-world validation remain active work.",
  },
  {
    icon: Users,
    title: "Guardian Sync",
    text: "Two trusted contacts are stored locally, written to the watch and read back so the app can confirm the profile actually arrived.",
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
