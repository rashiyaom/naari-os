import { motion } from "framer-motion";
import { ChevronRight, Eye, Radio, ShieldCheck } from "lucide-react";

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

const states = [
  {
    name: "SAFE",
    icon: ShieldCheck,
    text: "Normal operation. Low power, ready, with trusted contacts already configured on device.",
  },
  {
    name: "WATCH",
    icon: Eye,
    text: "A discreet triple press shares live location. No loud alarm, completely silent, and always cancellable.",
  },
  {
    name: "EMERGENCY",
    icon: Radio,
    text: "A long press locks GNSS, records available evidence and alerts trusted contacts via Cellular (LTE-M).",
  },
];

export function ModelSection() {
  return (
    <section id="model" className="blush-section py-24 md:py-32" aria-label="Three-state proactive safety model">
      <div className="mx-auto max-w-7xl px-5 md:px-10">
        <Reveal>
          <Mono>A proactive safety model</Mono>
          <h2 className="section-title mt-5 max-w-3xl">
            One quiet device.
            <br />
            Three clear states.
          </h2>
        </Reveal>

        {/* Three State Cards */}
        <div className="state-cards mt-14">
          {states.map(({ name, icon: Icon, text }, i) => (
            <Reveal key={name} className={`state-card state-card-${name.toLowerCase()}`}>
              <div className="flex items-center justify-between">
                <Icon size={25} />
                <Mono>0{i + 1}</Mono>
              </div>
              <h3>{name}</h3>
              <p>{text}</p>
            </Reveal>
          ))}
        </div>

        {/* 8:30 PM Scenario with Exact Messages */}
        <Reveal className="scenario mt-16">
          <div>
            <Mono>8:30 PM · Walk home</Mono>
            <p>Normal journey</p>
          </div>
          <ChevronRight size={24} aria-hidden />
          <div>
            <Mono>Triple press</Mono>
            <p>“Safety Watch activated. Live location available.”</p>
          </div>
          <ChevronRight size={24} aria-hidden />
          <div>
            <Mono>Long press</Mono>
            <p>“Emergency alert activated. Live location and available evidence are being shared.”</p>
          </div>
        </Reveal>
      </div>
    </section>
  );
}
