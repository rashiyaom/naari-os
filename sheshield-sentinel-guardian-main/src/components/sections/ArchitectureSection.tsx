import { motion } from "framer-motion";
import { Bluetooth, ChevronRight, Cpu, Gauge, Smartphone, Users } from "lucide-react";

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

const nodes = [
  { icon: Gauge, name: "Sensors + UI" },
  { icon: Cpu, name: "ESP32-S3 Watch" },
  { icon: Bluetooth, name: "Secure BLE" },
  { icon: Smartphone, name: "Android Executor" },
  { icon: Users, name: "Guardians" },
];

const hardwareList = [
  "ESP32-S3 Dual-Core Xtensa LX7 @ 240MHz (16MB Flash, 8MB PSRAM)",
  "410×502 Curved AMOLED Multi-touch Display (QSPI)",
  "Authenticated BLE protocol with bonded transport and app-level handshake",
  "Android call, SMS, location, guardian and recording services",
  "AXP2101 PMU & battery telemetry",
  "ES8311 audio output + ES7210 microphone input",
  "QMI8658 6-Axis Low-Noise IMU (Fall & Shake Detection)",
  "PCF85063 Ultra-Low Power I2C Real-Time Clock",
  "Touch, crown and side-button input",
  "FreeRTOS firmware with optional A7672S LTE/GNSS integration path",
];

export function ArchitectureSection() {
  return (
    <section className="dark-section py-24 md:py-32" aria-label="System architecture and hardware">
      <div className="mx-auto max-w-7xl px-5 md:px-10">
        <Reveal>
          <Mono>System architecture</Mono>
          <h2 className="section-title mt-5">One verified path from wrist to guardian.</h2>
        </Reveal>

        <div className="architecture mt-14" aria-label="Data flow pipeline">
          {nodes.map(({ icon: Icon, name }, i) => (
            <div key={name} className="arch-node">
              <Icon size={24} />
              <span>{name}</span>
              {i < nodes.length - 1 && <ChevronRight className="arch-arrow" aria-hidden />}
            </div>
          ))}
        </div>

        <Reveal>
          <div className="hardware-list mt-8" aria-label="Hardware component inventory">
            {hardwareList.map((item) => (
              <span key={item}>{item}</span>
            ))}
          </div>
        </Reveal>
      </div>
    </section>
  );
}
