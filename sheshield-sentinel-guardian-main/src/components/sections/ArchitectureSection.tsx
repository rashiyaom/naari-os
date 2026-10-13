import { motion } from "framer-motion";
import { ChevronRight, Cloud, Cpu, Eye, Radio, Satellite, Users } from "lucide-react";

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
  { icon: Eye, name: "Sensors" },
  { icon: Cpu, name: "MCU" },
  { icon: Satellite, name: "GNSS" },
  { icon: Radio, name: "Cellular" },
  { icon: Cloud, name: "Cloud" },
  { icon: Users, name: "Contacts" },
];

const hardwareList = [
  "ESP32-S3 Dual-Core Xtensa LX7 @ 240MHz (16MB Flash, 8MB PSRAM)",
  "410×502 Curved AMOLED Multi-touch Display (QSPI)",
  "SIM800L Quad-Band GSM/GPRS Cellular Modem",
  "Neo-6M GNSS GPS Satellite Positioning Engine",
  "AXP2101 High-Precision PMU & Fuel Gauge (500mAh LiPo)",
  "ES8311 Hi-Res DAC + ES7210 4-Ch I2S ADC (105dB Siren)",
  "QMI8658 6-Axis Low-Noise IMU (Fall & Shake Detection)",
  "PCF85063 Ultra-Low Power I2C Real-Time Clock",
  "Rotary Digital Crown & Power Button Interface",
  "FreeRTOS Deterministic Multi-Task Firmware",
];

export function ArchitectureSection() {
  return (
    <section className="dark-section py-24 md:py-32" aria-label="System architecture and hardware">
      <div className="mx-auto max-w-7xl px-5 md:px-10">
        <Reveal>
          <Mono>System architecture</Mono>
          <h2 className="section-title mt-5">Independent by design.</h2>
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
