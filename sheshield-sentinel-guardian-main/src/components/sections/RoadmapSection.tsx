import { motion } from "framer-motion";
import { Check } from "lucide-react";

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

const mvpFeatures = [
  "Watch + Emergency activation",
  "GPS / GNSS transmission",
  "Contact notification via Cellular",
  "Live location companion interface",
  "Timestamped telemetry logging",
];

const futurePhases = [
  "Integrated wristband enclosure",
  "Low-power edge camera module",
  "Geofenced commute safe zones",
  "Multi-tier trusted contact groups",
  "Hardware-encrypted evidence vault",
  "Authorized 112 emergency integration",
  "Solar-assisted 7-day battery life",
];

export function RoadmapSection() {
  return (
    <section className="cream-section py-24 md:py-32" aria-label="Development roadmap and future scope">
      <div className="mx-auto max-w-7xl px-5 md:px-10">
        <Reveal>
          <Mono>Prototype to product</Mono>
          <h2 className="section-title mt-5">Build the core first.</h2>
        </Reveal>

        {/* MVP and Optional Scope Cards */}
        <div className="roadmap-grid mt-14">
          <Reveal className="roadmap-card">
            <span>MVP STAGE</span>
            <h3>Prove the silent safety path.</h3>
            <p>
              ESP32-S3 class MCU · GNSS · Cellular (LTE-M) · Tactile Button · Vibration motor · MEMS Mic · Basic camera
            </p>
            <ul>
              {mvpFeatures.map((x) => (
                <li key={x}>
                  <Check size={14} className="shrink-0" />
                  <span>{x}</span>
                </li>
              ))}
            </ul>
          </Reveal>

          <Reveal className="roadmap-card optional">
            <span>OPTIONAL ADVANCED</span>
            <h3>Add context carefully.</h3>
            <p>
              Discreet voice keyword activation · Thermal human presence sensing · Edge ML classification · Encrypted cloud evidence storage · Guardian companion mobile app
            </p>
          </Reveal>
        </div>

        {/* Future Scope Timeline */}
        <div className="future-line mt-14" aria-label="Future roadmap milestones">
          {futurePhases.map((x, i) => (
            <div key={x}>
              <i />
              <Mono>{String(i + 1).padStart(2, "0")}</Mono>
              <span>{x}</span>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
