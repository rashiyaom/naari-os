import { motion, useScroll, useTransform } from "framer-motion";
import { Activity, ChevronRight, Expand, Eye, Radio, ShieldCheck, Sparkles } from "lucide-react";
import { useEffect, useState } from "react";
import { SentinelButton } from "@/components/ui/sentinel-button";

const Mono = ({ children }: { children: React.ReactNode }) => (
  <span className="font-mono text-[10px] uppercase tracking-widest">{children}</span>
);

export function Nav() {
  return (
    <nav
      className="absolute inset-x-0 top-0 z-40 mx-auto flex max-w-[1440px] items-center justify-between px-5 py-5 md:px-10"
      aria-label="Main navigation"
    >
      <a href="#top" className="flex items-center gap-2 text-sm font-bold tracking-tight">
        <span className="logo-pulse">
          <Activity size={13} />
        </span>
        SheShield
      </a>
      <div className="hidden items-center gap-7 text-[11px] font-medium md:flex">
        {["Problem", "Model", "Demo", "Features", "Privacy"].map((x) => (
          <a
            key={x}
            href={`#${x.toLowerCase()}`}
            className="text-muted-foreground transition hover:text-primary"
          >
            {x}
          </a>
        ))}
      </div>
      <a
        href="#demo"
        className="rounded-full border border-glass-edge bg-glass px-4 py-2 text-[11px] font-semibold text-foreground backdrop-blur-xl transition hover:bg-glass-strong hover:shadow-coral"
      >
        Try the demo
      </a>
    </nav>
  );
}

function HeroWatch({ state }: { state: "SAFE" | "WATCH" | "EMERGENCY" }) {
  const StateIcon = state === "SAFE" ? ShieldCheck : state === "WATCH" ? Eye : Radio;
  const stateColor =
    state === "SAFE"
      ? "var(--safe)"
      : state === "WATCH"
        ? "var(--watch)"
        : "var(--emergency)";

  return (
    <div
      className={`product-watch state-${state.toLowerCase()}`}
      style={{ "--state": stateColor } as React.CSSProperties}
      aria-label={`Rose-gold SheShield Sentinel smartwatch render currently in ${state} state`}
    >
      {/* Top Strap with Lug Connector and Soft Texture */}
      <div className="watch-strap watch-strap-top" aria-hidden="true">
        <div className="strap-lug" />
        <div className="strap-ridge" />
      </div>

      {/* Hardware details: Left Mic/Speaker & Antenna Notches */}
      <div className="watch-hardware-details" aria-hidden="true">
        <div className="watch-antenna-top" />
        <div className="watch-antenna-bottom" />
        <div className="watch-mic-slot" />
      </div>

      {/* Right Controls: Ribbed Rotary Crown & Slim Side Button */}
      <div className="watch-controls-right" aria-hidden="true">
        <div className="watch-digital-crown" />
        <div className="watch-side-button" />
      </div>

      {/* Curved metallic body */}
      <div className="watch-body">
        {/* Subtle glass reflection overlay & inner recessed bezel */}
        <div className="screen-glass-glare" aria-hidden="true" />
        <div className="watch-inner-bezel" aria-hidden="true" />

        {/* Glossy curved watch glass */}
        <div className="watch-glass">
          {/* Subtle curved light reflection overlay */}
          <div
            className="pointer-events-none absolute -left-12 -top-12 h-44 w-44 rounded-full opacity-30"
            style={{
              background:
                "radial-gradient(circle, rgba(255,255,255,0.7) 0%, rgba(255,255,255,0) 70%)",
            }}
          />

          <div className="watch-time">20:30</div>

          {/* Cycling glow pulse ring */}
          <div className="watch-pulse">
            <StateIcon />
          </div>

          <div className="watch-state">
            {state === "WATCH" ? "SAFETY WATCH" : state}
          </div>
          <div className="watch-caption">
            {state === "SAFE"
              ? "STANDBY · CELLULAR + GNSS"
              : state === "WATCH"
                ? "DISCREET STREAMING"
                : "PRIORITY EMERGENCY"}
          </div>

          <div className="mt-auto pb-6 font-mono text-[7px] text-cream/50 tracking-wider">
            PHONE-INDEPENDENT
          </div>
        </div>
      </div>

      {/* Bottom Strap with Lug Connector and Soft Texture */}
      <div className="watch-strap watch-strap-bottom" aria-hidden="true">
        <div className="strap-lug" />
        <div className="strap-ridge" />
      </div>
    </div>
  );
}

export function Hero() {
  const { scrollY } = useScroll();
  const ghostY = useTransform(scrollY, [0, 700], [0, 90]);
  const watchY = useTransform(scrollY, [0, 700], [0, -35]);

  // Glow cycling between SAFE (sage) -> WATCH (peach) -> EMERGENCY (coral)
  const [cyclingState, setCyclingState] = useState<"SAFE" | "WATCH" | "EMERGENCY">("SAFE");

  useEffect(() => {
    const cycle: Array<"SAFE" | "WATCH" | "EMERGENCY"> = ["SAFE", "WATCH", "EMERGENCY"];
    let index = 0;
    const interval = setInterval(() => {
      index = (index + 1) % cycle.length;
      setCyclingState(cycle[index] ?? "SAFE");
    }, 3500);

    return () => clearInterval(interval);
  }, []);

  return (
    <header id="top" className="hero relative min-h-[760px] overflow-hidden md:min-h-[880px]">
      <Nav />

      {/* Giant Ghost Backdrop Word */}
      <motion.div style={{ y: ghostY }} className="ghost-word select-none" aria-hidden>
        SHIELD
      </motion.div>

      <div className="relative z-10 mx-auto grid min-h-[760px] max-w-[1440px] items-center px-5 pt-24 md:min-h-[880px] md:grid-cols-[.82fr_1.18fr] md:px-10">
        <div className="z-20 pt-10 md:pt-0">
          <Mono>ILS Summit 2026 · Surat · Track 3 SheLeads · P6 · Team Puraanmaya</Mono>
          <h1 className="mt-7 text-[clamp(4.1rem,9vw,9rem)] font-extrabold leading-[.78]">
            Silent.
            <br />
            Signal.
            <br />
            <span className="text-primary">Sentinel.</span>
          </h1>
          <p className="mt-7 max-w-md text-sm leading-6 text-muted-foreground md:text-base">
            SheShield Sentinel is a phone-independent wearable that lets her alert trusted people
            before a situation becomes an emergency. Dedicated Cellular (LTE-M) and GNSS right on the
            wrist.
          </p>
          <div className="mt-7 flex flex-wrap gap-3">
            <a href="#demo">
              <SentinelButton variant="glass" aria-label="Scroll to live interactive prototype">
                Try the live demo <ChevronRight size={16} />
              </SentinelButton>
            </a>
            <a href="#model">
              <SentinelButton aria-label="Learn how SheShield works">
                See how it works
              </SentinelButton>
            </a>
          </div>

          {/* Cycling State Indicator Pill */}
          <div className="mt-8 flex items-center gap-3">
            <span className="font-mono text-[9px] uppercase tracking-wider text-muted-foreground">
              Cycle state:
            </span>
            <div className="flex gap-1.5">
              {(["SAFE", "WATCH", "EMERGENCY"] as const).map((s) => (
                <button
                  key={s}
                  type="button"
                  onClick={() => setCyclingState(s)}
                  className={`rounded-full px-2.5 py-1 font-mono text-[8px] font-semibold transition ${
                    cyclingState === s
                      ? s === "SAFE"
                        ? "bg-safe/20 text-safe border border-safe/40"
                        : s === "WATCH"
                          ? "bg-watch/20 text-watch border border-watch/40"
                          : "bg-emergency/20 text-emergency border border-emergency/40"
                      : "bg-glass text-muted-foreground hover:text-foreground"
                  }`}
                >
                  {s}
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* Watch Render cropped at the right */}
        <motion.div style={{ y: watchY }} className="hero-watch-wrap">
          {/* Animated Glow Orbit reflecting cycling state */}
          <div
            className="pulse-orbit transition-all duration-700"
            style={{
              borderColor:
                cyclingState === "SAFE"
                  ? "color-mix(in oklab, var(--safe) 40%, transparent)"
                  : cyclingState === "WATCH"
                    ? "color-mix(in oklab, var(--watch) 40%, transparent)"
                    : "color-mix(in oklab, var(--emergency) 40%, transparent)",
              boxShadow:
                cyclingState === "SAFE"
                  ? "0 0 50px color-mix(in oklab, var(--safe) 25%, transparent)"
                  : cyclingState === "WATCH"
                    ? "0 0 50px color-mix(in oklab, var(--watch) 25%, transparent)"
                    : "0 0 50px color-mix(in oklab, var(--emergency) 25%, transparent)",
            }}
          />

          <HeroWatch state={cyclingState} />
        </motion.div>
      </div>

      <div className="floating-tools">
        <a href="#demo" aria-label="Jump directly to interactive demo">
          <button type="button" aria-label="Jump to interactive demo">
            <Expand size={18} />
          </button>
        </a>
        <a href="#features" aria-label="Explore product features">
          <button type="button" aria-label="Explore product features">
            <Sparkles size={18} />
          </button>
        </a>
      </div>
    </header>
  );
}
