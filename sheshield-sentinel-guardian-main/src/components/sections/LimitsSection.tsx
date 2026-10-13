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
  "Cannot predict attacks before intention exists",
  "Thermal sensors cannot identify individuals or faces",
  "No guaranteed cellular connectivity in deep basements",
  "No automated police dispatch without guardian escalation",
  "AI will not independently make life-critical judgements",
  "The core safety path never requires a paired smartphone",
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
