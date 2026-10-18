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
    name: "READY",
    tone: "safe",
    icon: ShieldCheck,
    text: "The watch face, local tools and trusted contacts remain ready while the phone link reports its real connection state.",
  },
  {
    name: "GUARD",
    tone: "watch",
    icon: Eye,
    text: "Fall monitoring evaluates motion locally and the wearer can start help manually without navigating a phone.",
  },
  {
    name: "SOS",
    tone: "emergency",
    icon: Radio,
    text: "A 30-second grace window precedes approved call, SMS and location requests through the connected Android phone.",
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
          {states.map(({ name, tone, icon: Icon, text }, i) => (
            <Reveal key={name} className={`state-card state-card-${tone}`}>
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
              <p>Guard mode monitors motion and keeps the phone link visible.</p>
          </div>
          <ChevronRight size={24} aria-hidden />
          <div>
            <Mono>SOS trigger</Mono>
            <p>30-second cancel window, then the phone executes guardian actions.</p>
          </div>
        </Reveal>
      </div>
    </section>
  );
}
