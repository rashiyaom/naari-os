import { motion } from "framer-motion";
import { ShieldCheck, Signal, Users } from "lucide-react";
import type { useSentinelDemo } from "@/hooks/use-sentinel-demo";

type Props = {
  demo: ReturnType<typeof useSentinelDemo>;
};

const Mono = ({ children }: { children: React.ReactNode }) => (
  <span className="font-mono text-[10px] uppercase tracking-widest">{children}</span>
);

export function Companion({ demo }: Props) {
  // Dynamic coordinates on SVNIT Campus route map
  const [dotX, dotY] =
    demo.state === "SAFE"
      ? [20, 67]
      : demo.state === "WATCH"
        ? [52, 44]
        : demo.state === "EMERGENCY_PENDING"
          ? [65, 35]
          : [77, 26];

  const guardianStateClass =
    demo.state === "SAFE"
      ? "state-safe"
      : demo.state === "WATCH"
        ? "state-watch"
        : "state-emergency";

  const isEmergencyOrPending =
    demo.state === "EMERGENCY" || demo.state === "EMERGENCY_PENDING";

  return (
    <div className={`guardian ${guardianStateClass}`} aria-label="Guardian companion dashboard view">
      {/* Header */}
      <div className="guardian-head">
        <div>
          <Mono>SVNIT Campus Route</Mono>
          <p>Interactive phone-link simulation</p>
        </div>
        <span className={demo.connected ? "text-safe" : "text-watch"}>
          <Signal size={12} /> {demo.connected ? "LIVE" : "OFFLINE"}
        </span>
      </div>

      {/* SVG Campus Route Map with smooth animated marker */}
      <div className="map">
        <svg viewBox="0 0 100 80" role="img" aria-label="Stylized SVNIT campus route map">
          {/* Main roads */}
          <path className="map-road" d="M-5 70 C20 55 35 60 48 44 S76 30 105 11" />
          <path className="map-road thin" d="M14 0v80M70 0v80M0 24h100M0 62h100" />

          {/* Planned journey trail */}
          <path className="map-trail" d="M7 72 C24 55 37 61 50 44 S75 31 89 14" />

          {/* Dynamic Wearer Location Dot with Framer Motion transition */}
          <motion.circle
            className="map-dot"
            initial={{ cx: dotX, cy: dotY }}
            animate={{ cx: dotX, cy: dotY }}
            transition={{ type: "spring", stiffness: 120, damping: 18 }}
            r="4"
          />
          <motion.circle
            className="map-pulse"
            initial={{ cx: dotX, cy: dotY }}
            animate={{ cx: dotX, cy: dotY }}
            transition={{ type: "spring", stiffness: 120, damping: 18 }}
            r="8"
          />
        </svg>
        <span className="map-label">SVNIT CAMPUS</span>
        <span className="map-label map-label-2">ICHCHHANATH</span>
      </div>

      {/* Guardian Content */}
      <div className="guardian-content">
        <div className="flex items-center justify-between">
          <span className="guardian-state">
            {demo.state === "WATCH"
              ? "SAFETY WATCH ACTIVE"
              : demo.state === "EMERGENCY_PENDING"
                ? `CONFIRMATION PENDING (${demo.countdown}s)`
                : demo.state === "EMERGENCY"
                  ? "EMERGENCY ACTIVE"
                  : "SAFE"}
          </span>
          <span className="font-mono text-[8px] text-dark-muted">
            {demo.connected ? "BLE: authenticated" : "BLE: disconnected"}
          </span>
        </div>

        {/* Dynamic Alert Card (in exact summit format) or Journey Normal Status */}
        {isEmergencyOrPending ? (
          <div className="alert-card">
            <Mono>NAARI OS — EMERGENCY</Mono>
            <h4>
              Status:{" "}
              {demo.state === "EMERGENCY"
                ? "Emergency Mode Activated"
                : `Silent Confirmation Active (${demo.countdown}s)`}
            </h4>
            <p>Time: {new Date().toLocaleTimeString()}</p>
            <p>Location: Android location request {demo.connected ? "queued" : "unavailable while offline"}</p>
            <p>Transport: Authenticated BLE watch request</p>
            <b>Action: Contact wearer · proceed to location · contact appropriate emergency services</b>
          </div>
        ) : (
          <div className="status-summary">
            <ShieldCheck />
            <div>
              <b>{demo.state === "SAFE" ? "Journey is normal" : "Guardian monitoring active"}</b>
              <p>
                {demo.state === "SAFE"
                  ? "Safe perimeter maintained · No anomalies"
                  : "Fall monitoring active · phone link visible"}
              </p>
            </div>
          </div>
        )}

        {/* Guardian People List */}
        <div className="guardian-people" aria-label="Notified guardians list">
          {["Mom · Primary", "Police · 112", "Women Helpline · 1091"].map((person) => (
            <span key={person}>
              <Users /> {person}
            </span>
          ))}
        </div>

        {/* Timestamped Event Log */}
        <div className="event-log">
          <div className="flex items-center justify-between">
            <Mono>Event log</Mono>
            {demo.buffered.length > 0 && (
              <span className="font-mono text-[8px] text-watch">
                {demo.buffered.length} buffered offline
              </span>
            )}
          </div>
          {demo.events.slice(0, 5).map((event) => (
            <p key={event.id}>
              <time suppressHydrationWarning>{event.time}</time>
              <span>{event.text}</span>
            </p>
          ))}
        </div>

        {/* Footer */}
        <footer>
          <span>Battery Sync: 94% · Normal</span>
          <span>
            {demo.state === "SAFE"
              ? "Safe Perimeter Maintained"
              : demo.state === "WATCH"
                ? "Discreet Monitoring Active"
                : "Priority Emergency Channel Open"}
          </span>
        </footer>
      </div>
    </div>
  );
}
