import { motion } from "framer-motion";
import { Check, LockKeyhole } from "lucide-react";

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

const privacyItems = [
  "Bonded BLE plus an app-level protocol handshake",
  "User-configured guardian contacts stored locally",
  "No cloud account or server required for the current workflow",
  "Recordings remain on the watch until the user transfers them",
  "Phone-side recordings can be deleted or exported by the owner",
  "No continuous location or ambient audio streaming",
  "Every phone action is tied to an explicit watch request",
];

export function PrivacySection() {
  return (
    <section id="privacy" className="cream-section py-24 md:py-32" aria-label="Privacy and security commitments">
      <div className="mx-auto grid max-w-7xl gap-12 px-5 md:grid-cols-[.8fr_1.2fr] md:px-10">
        <Reveal>
          <LockKeyhole size={42} className="text-primary" />
          <h2 className="section-title mt-8">Privacy is part of safety.</h2>
          <p className="mt-5 max-w-sm text-muted-foreground leading-relaxed">
            Naari OS keeps its core profile and recordings on devices. The phone acts only on authenticated
            watch requests and Android permissions the user has explicitly granted.
          </p>
        </Reveal>

        <div className="privacy-list">
          {privacyItems.map((p, i) => (
            <Reveal key={p} className="privacy-row">
              <Mono>{String(i + 1).padStart(2, "0")}</Mono>
              <p>{p}</p>
              <Check className="text-safe" size={20} />
            </Reveal>
          ))}
        </div>
      </div>
    </section>
  );
}
