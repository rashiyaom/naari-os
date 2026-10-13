import { motion } from "framer-motion";

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

const problems = [
  "No time to reach a phone",
  "An obvious SOS can escalate danger",
  "No support before emergency",
  "Dead, lost or inaccessible phone",
  "Contacts receive too little context",
];

export function GapSection() {
  return (
    <section id="problem" className="dark-section py-24 md:py-32" aria-label="The safety gap analysis">
      <div className="mx-auto max-w-7xl px-5 md:px-10">
        <Reveal>
          <Mono>The missing middle</Mono>
          <h2 className="section-title mt-5 max-w-4xl">
            Most safety devices start after the attack begins.
          </h2>
        </Reveal>

        <div className="mt-14 grid gap-4 lg:grid-cols-[1fr_1.4fr]">
          <Reveal className="dark-panel p-7">
            <p className="text-xs text-dark-muted font-mono uppercase tracking-wider">
              Conventional response
            </p>
            <div className="state-path state-path-short">
              <span>Normal</span>
              <i />
              <b>SOS</b>
            </div>

            <p className="mt-10 text-xs text-dark-muted font-mono uppercase tracking-wider">
              SheShield response
            </p>
            <div className="state-path">
              <span>Normal</span>
              <i />
              <em>Watch</em>
              <i />
              <b>Emergency</b>
            </div>
          </Reveal>

          <div className="grid gap-3 sm:grid-cols-2">
            {problems.map((p, i) => (
              <Reveal
                key={p}
                className={`dark-panel flex min-h-28 items-end p-5 ${i === 4 ? "sm:col-span-2" : ""}`}
              >
                <span className="mr-auto max-w-xs text-base font-semibold">{p}</span>
                <Mono>0{i + 1}</Mono>
              </Reveal>
            ))}
          </div>
        </div>

        <Reveal>
          <blockquote className="mx-auto mt-20 max-w-4xl text-center text-2xl font-semibold leading-tight md:text-4xl text-cream/90">
            “She simply wants someone she trusts to know where she is and be ready.”
          </blockquote>
        </Reveal>
      </div>
    </section>
  );
}
