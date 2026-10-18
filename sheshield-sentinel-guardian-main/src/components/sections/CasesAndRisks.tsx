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
    text: "She can start SOS from the wrist. After the grace window, the paired phone can message or call a configured guardian and attach its current location.",
  },
  {
    name: "Cab / auto",
    scenario: "Unfamiliar detour",
    text: "The watch keeps guardian access close while the Android link makes its real connected or disconnected state visible.",
  },
  {
    name: "Fall candidate",
    scenario: "Impact followed by low movement",
    text: "The watch checks impact and post-impact motion, asks the wearer to dismiss, then escalates through the same SOS flow if unanswered.",
  },
  {
    name: "False alarm",
    scenario: "Accidental button press",
    text: "A 30-second countdown prevents accidental dispatch, while an active SOS requires a deliberate 3-second hold to stop.",
  },
];

const risks = [
  ["Accidental activation", "30-second grace period and 3-second hold-to-stop interaction"],
  ["BLE link loss", "Visible link status, reconnect path and explicit failed-action replies"],
  ["Battery drain", "Local event-driven sensing and no continuous phone streaming"],
  ["Wearer privacy", "On-device storage, explicit transfer and no cloud dependency"],
  ["Sensor ambiguity", "Multi-stage impact and posture heuristic with ongoing dataset calibration"],
  ["Phone permission denied", "Readiness checks block setup until required Android capabilities are granted"],
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
