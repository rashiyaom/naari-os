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
  "ESP32-S3 AMOLED watch interface",
  "Authenticated BLE pairing and handshake",
  "Manual SOS with deliberate cancellation",
  "Guardian write-and-readback sync",
  "Voice memo library and phone transfer protocol",
];

const futurePhases = [
  "Real-world fall dataset collection and threshold calibration",
  "Physical Android end-to-end BLE test matrix",
  "A7672S power, LTE and GNSS bench validation",
  "Signed release APK and Play Store distribution",
  "Battery and reconnection endurance testing",
  "Accessibility and multilingual emergency flows",
  "Pilot study with documented safety protocol",
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
              ESP32-S3 · 410×502 AMOLED · QMI8658 IMU · ES7210 microphone · ES8311 audio · Android companion
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
            <span>NEXT VALIDATION</span>
            <h3>Prove reliability in the field.</h3>
            <p>
              Tune fall detection from recorded motion traces, test BLE across Android vendors, validate power behavior, and bench-test the optional A7672S modem path.
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
