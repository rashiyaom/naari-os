import { motion } from "framer-motion";
import { Bluetooth, CheckCircle2, Download, LockKeyhole, ShieldCheck, Smartphone } from "lucide-react";
import { SentinelButton } from "@/components/ui/sentinel-button";

const APK_PATH = "/downloads/Naari-Companion-1.2-debug.apk";

const facts = [
  { icon: Bluetooth, label: "Authenticated BLE", text: "Secure watch discovery, bond, handshake and automatic reconnect." },
  { icon: Smartphone, label: "Android 8+", text: "Built for API 26 and newer Android phones." },
  { icon: LockKeyhole, label: "Local-first", text: "Guardian data and recordings stay on your devices by default." },
];

export function DownloadSection() {
  return (
    <section id="download" className="cream-section py-24 md:py-32" aria-label="Download the Naari Companion Android app">
      <div className="mx-auto max-w-7xl px-5 md:px-10">
        <motion.div
          className="download-shell"
          initial={{ opacity: 0, y: 18 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, margin: "-60px" }}
          transition={{ duration: 0.6 }}
        >
          <div className="download-copy">
            <span className="font-mono text-[10px] uppercase tracking-widest">Android companion · v1.2</span>
            <h2 className="section-title mt-5">The watch and phone, in one safety loop.</h2>
            <p className="mt-6 max-w-2xl text-sm leading-7 text-muted-foreground md:text-base">
              Pair a Naari Watch, manage guardians, relay approved call, SMS and location requests,
              control watch settings, and receive voice memos over the verified BLE protocol.
            </p>
            <div className="mt-8 flex flex-wrap items-center gap-3">
              <a href={APK_PATH} download>
                <SentinelButton aria-label="Download Naari Companion version 1.2 APK">
                  <Download size={17} /> Download APK
                </SentinelButton>
              </a>
              <span className="download-version">v1.2 · debug-signed · Android 8+</span>
            </div>
            <div className="mt-7 flex items-start gap-3 rounded-2xl border border-primary/15 bg-white/45 p-4 text-xs leading-5 text-muted-foreground">
              <ShieldCheck className="mt-0.5 shrink-0 text-primary" size={18} />
              <span>Install the APK, allow Nearby devices, then open <b>Phone Link → Start pairing</b> on the watch. Android will show a secure passkey confirmation.</span>
            </div>
          </div>

          <div className="download-facts">
            {facts.map(({ icon: Icon, label, text }) => (
              <div className="download-fact" key={label}>
                <span><Icon size={19} /></span>
                <div><b>{label}</b><p>{text}</p></div>
              </div>
            ))}
            <div className="download-ready">
              <CheckCircle2 size={18} />
              <span>Manifest, unit tests, lint and APK signature verified</span>
            </div>
          </div>
        </motion.div>
      </div>
    </section>
  );
}
