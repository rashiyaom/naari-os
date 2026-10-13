import { AnimatePresence, motion } from "framer-motion";
import { useState } from "react";

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

const useCases = [
  {
    name: "Walk home",
    scenario: "Evening commute",
    text: "She feels followed after work. A discreet triple press quietly starts Safety Watch and streams live location to Mom without escalating tension.",
  },
  {
    name: "Cab / auto",
    scenario: "Unfamiliar detour",
    text: "A late-night cab route feels wrong. Guardian monitoring starts discreetly before the situation ever escalates into an emergency.",
  },
  {
    name: "Phone inaccessible",
    scenario: "Dead battery or bag out of reach",
    text: "The core safety path remains functional on her wrist, completely independent of a dead, lost, or snatched smartphone.",
  },
  {
    name: "False alarm",
    scenario: "Accidental button press",
    text: "A silent vibration and configurable 1-5s countdown window allow her to cancel instantly without false alarm panic.",
  },
];

const risks = [
  ["Accidental activation", "Silent cancellation window and haptic vibration feedback"],
  ["No cellular coverage", "Store-and-forward local event buffering flushed upon reconnection"],
  ["Battery drain", "Ultra-low-power standby mode, activating high-drain GPS only during events"],
  ["Wearer privacy", "Trigger-bound evidence capture, strict retention policies, no continuous audio"],
  ["Sensor ambiguity", "Contextual metadata only (thermal presence), never biometric identification"],
  ["Voice keyword failure", "Physical crown tactile gestures remain the primary, fail-safe trigger"],
];

export function CasesAndRisks() {
  const [tab, setTab] = useState(0);
  const activeCase = useCases[tab] ?? useCases[0];

  return (
    <section className="blush-section py-24 md:py-32" aria-label="Use cases and risk mitigation">
      <div className="mx-auto max-w-7xl px-5 md:px-10">
        <Reveal>
          <Mono>Real-world use</Mono>
          <h2 className="section-title mt-5">Designed around the moment.</h2>
        </Reveal>

        {/* Tab Controls */}
        <div className="case-tabs mt-12" role="tablist" aria-label="Real-world scenario tabs">
          {useCases.map((c, i) => (
            <button
              type="button"
              role="tab"
              aria-selected={tab === i}
              aria-controls={`case-panel-${i}`}
              id={`case-tab-${i}`}
              key={c.name}
              onClick={() => setTab(i)}
            >
              {c.name}
            </button>
          ))}
        </div>

        {/* Tab Panel */}
        <AnimatePresence mode="wait">
          <motion.div
            key={tab}
            id={`case-panel-${tab}`}
            role="tabpanel"
            aria-labelledby={`case-tab-${tab}`}
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -8 }}
            transition={{ duration: 0.3 }}
            className="case-content"
          >
            <Mono>
              Scenario {String(tab + 1).padStart(2, "0")} · {activeCase?.scenario}
            </Mono>
            <p>{activeCase?.text}</p>
          </motion.div>
        </AnimatePresence>

        {/* Risks Mitigation Table */}
        <Reveal>
          <div className="risks mt-20" aria-label="Risk assessment and design mitigations">
            <div className="risk-head">
              <Mono>Anticipated Risk</Mono>
              <Mono>Architectural Mitigation</Mono>
            </div>
            {risks.map(([risk, approach]) => (
              <div className="risk-row" key={risk}>
                <b>{risk}</b>
                <span>{approach}</span>
              </div>
            ))}
          </div>
        </Reveal>
      </div>
    </section>
  );
}
