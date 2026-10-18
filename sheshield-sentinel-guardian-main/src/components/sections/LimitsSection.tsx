import { motion } from "framer-motion";
import { X } from "lucide-react";

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

const claims = [
  "Fall detection is a tunable heuristic, not a medically validated diagnosis",
  "Phone-based calls, SMS and location require Android permissions and a working BLE link",
  "The optional A7672S LTE/GNSS hardware path has not yet been physically validated",
  "No alert system can guarantee message delivery or guardian response",
  "The current modem interface does not carry live call audio over UART",
  "The browser simulator demonstrates flows; it is not evidence of hardware behavior",
];

export function LimitsSection() {
  return (
    <section className="dark-section py-24" aria-label="System limitations and explicit non-claims">
      <div className="mx-auto max-w-7xl px-5 md:px-10">
        <Reveal>
          <Mono>What we are not claiming</Mono>
          <h2 className="section-title mt-5 max-w-4xl">
            Credibility means naming the limits.
          </h2>
        </Reveal>

        <div className="limits-grid mt-12">
          {claims.map((claim, i) => (
            <Reveal className="limit-item" key={claim}>
              <X size={20} className="text-primary" />
              <span>{claim}</span>
              <Mono>0{i + 1}</Mono>
            </Reveal>
          ))}
        </div>

        <p className="mt-10 text-sm text-dark-muted font-medium">
          These technical limits make the prototype engineering credible, responsible, and practical for real deployment.
        </p>
      </div>
    </section>
  );
}
