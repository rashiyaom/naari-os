import { motion } from "framer-motion";
import { RotateCcw } from "lucide-react";
import { SentinelButton } from "@/components/ui/sentinel-button";

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

export function Closing() {
  return (
    <section className="closing relative overflow-hidden py-28 md:py-40" aria-label="Closing call to action">
      {/* Ghost Background Word */}
      <div className="closing-ghost select-none" aria-hidden>
        SAFE
      </div>

      <Reveal className="relative z-10 mx-auto max-w-5xl px-5 text-center">
        <Mono>SheShield Sentinel</Mono>
        <h2 className="mt-7 text-5xl font-extrabold leading-[.92] md:text-8xl">
          Don’t wait for danger to become an emergency before someone knows.
        </h2>
        <p className="mx-auto mt-7 max-w-2xl text-muted-foreground text-sm md:text-base leading-relaxed">
          SheShield Sentinel turns “I feel unsafe” into a silent signal for help, before it becomes an emergency.
        </p>
        <div className="mt-8">
          <a href="#demo">
            <SentinelButton variant="glass" aria-label="Scroll back up to test the interactive demo again">
              Replay the demo <RotateCcw size={16} />
            </SentinelButton>
          </a>
        </div>
      </Reveal>
    </section>
  );
}
