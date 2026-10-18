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

const flows = [
  {
    title: "Manual SOS",
    items: [
      "Wearer triggers SOS",
      "30-second grace window",
      "3-second hold can cancel",
      "Watch sends signed request",
      "Android calls, texts or locates",
      "Result returns to the watch",
    ],
  },
  {
    title: "Fall candidate",
    items: [
      "QMI8658 detects impact",
      "Post-impact motion is evaluated",
      "Watch asks the wearer",
      "Wearer can dismiss locally",
      "Unanswered event enters SOS flow",
      "Phone relays the guardian action",
    ],
  },
];

export function HowSection() {
  return (
    <section className="cream-section py-24 md:py-32" aria-label="How Naari OS works">
      <div className="mx-auto max-w-7xl px-5 md:px-10">
        <Reveal>
          <Mono>Signal, not spectacle</Mono>
          <h2 className="section-title mt-5">What happens after a trigger.</h2>
        </Reveal>

        <div className="flow-grid mt-14">
          {flows.map((flow) => (
            <Reveal key={flow.title} className="flow">
              <h3>{flow.title}</h3>
              {flow.items.map((item, i) => (
                <div className="flow-row" key={item}>
                  <span>{String(i + 1).padStart(2, "0")}</span>
                  <p>{item}</p>
                  {i < flow.items.length - 1 && <i />}
                </div>
              ))}
            </Reveal>
          ))}
        </div>
      </div>
    </section>
  );
}
