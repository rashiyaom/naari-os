import { useState } from "react";
import { RotateCcw, Monitor, Watch as WatchIcon } from "lucide-react";
import type { useSentinelDemo, WatchScreen } from "@/hooks/use-sentinel-demo";
import { Watch } from "@/components/demo/Watch";
import { Companion } from "@/components/demo/Companion";
import { WatchShowcase } from "@/components/watch/WatchShowcase";

type Props = {
  demo: ReturnType<typeof useSentinelDemo>;
};

const Mono = ({ children }: { children: React.ReactNode }) => (
  <span className="font-mono text-[10px] uppercase tracking-widest">{children}</span>
);

export function DemoSection({ demo }: Props) {
  const [viewMode, setViewMode] = useState<"paired" | "studio">("paired");

  const screens: WatchScreen[] = [
    "home",
    "watch",
    "pending",
    "emergency",
    "contacts",
    "config",
    "network",
  ];

  const demoStateClass =
    demo.state === "SAFE"
      ? "demo-state-safe"
      : demo.state === "WATCH"
        ? "demo-state-watch"
        : "demo-state-emergency";

  return (
    <section
      id="demo"
      className={`demo-section ${demoStateClass} py-24 md:py-32`}
      aria-label="Interactive Naari OS demo section"
    >
      <div className="mx-auto max-w-[1440px] px-4 md:px-10">
        <div className="mb-12 flex flex-wrap items-end justify-between gap-5">
          <div>
            <Mono>Interactive linked prototype</Mono>
            <h2 className="mt-4 max-w-4xl text-4xl font-bold leading-none md:text-6xl">
              Wearable unit & guardian companion dashboard.
            </h2>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            {/* View Mode Toggle */}
            <div className="flex bg-[rgba(255,255,255,0.06)] p-1 rounded-2xl border border-[rgba(255,255,255,0.12)]">
              <button
                type="button"
                className={`flex items-center gap-2 px-3.5 py-1.5 rounded-xl text-xs font-semibold transition-all ${
                  viewMode === "paired"
                    ? "bg-[#2dd4bf] text-black shadow-lg"
                    : "text-white/70 hover:text-white"
                }`}
                onClick={() => setViewMode("paired")}
              >
                <WatchIcon size={14} />
                <span>Linked Companion</span>
              </button>
              <button
                type="button"
                className={`flex items-center gap-2 px-3.5 py-1.5 rounded-xl text-xs font-semibold transition-all ${
                  viewMode === "studio"
                    ? "bg-[#2dd4bf] text-black shadow-lg"
                    : "text-white/70 hover:text-white"
                }`}
                onClick={() => setViewMode("studio")}
              >
                <Monitor size={14} />
                <span>Watch OS Studio</span>
              </button>
            </div>

            <span className={`live-pill ${!demo.connected ? "offline" : ""}`}>
              <i />
              {demo.connected ? "Simulated BLE Link Active" : "Waiting for connection…"}
            </span>
          </div>
        </div>

        {viewMode === "studio" ? (
          /* Studio View: Full Watch Showcase with Wallpaper Picker, Screen Jumpers, Soundboard */
          <div className="rounded-3xl border border-white/10 bg-black/40 p-4 md:p-8 backdrop-blur-xl">
            <WatchShowcase
              onStateChange={(s) => {
                if (s === "EMERGENCY") demo.beginEmergency();
                else if (s === "SAFE") demo.cancel();
                else if (s === "WATCH") demo.activateWatch();
              }}
            />
          </div>
        ) : (
          /* Paired View: Watch Hardware Screen + Companion */
          <div className="demo-grid">
            {/* Panel 1: Watch Hardware Screen */}
            <div className="demo-panel">
              <div className="panel-label">
                <Mono>Wearable hardware display</Mono>
                <span>410×502 AMOLED · 14 SCREENS</span>
              </div>

              <Watch demo={demo} />

              {/* Quick jump screen dots */}
              <div className="mt-3.5 flex justify-center">
                <div
                  className="screen-dots"
                  role="tablist"
                  aria-label="Jump to watch screen"
                  onPointerDown={(e) => {
                    const target = (
                      document.elementFromPoint(e.clientX, e.clientY) as HTMLElement | null
                    )?.closest("button[data-screen]") as HTMLElement | null;
                    if (target?.dataset['screen']) {
                      demo.setScreen(target.dataset['screen'] as WatchScreen);
                    }
                  }}
                  onPointerMove={(e) => {
                    if (e.buttons === 1) {
                      const target = (
                        document.elementFromPoint(e.clientX, e.clientY) as HTMLElement | null
                      )?.closest("button[data-screen]") as HTMLElement | null;
                    if (target?.dataset['screen'] && target.dataset['screen'] !== demo.screen) {
                      demo.setScreen(target.dataset['screen'] as WatchScreen);
                      }
                    }
                  }}
                >
                  {screens.map((s) => (
                    <button
                      key={s}
                      type="button"
                      data-screen={s}
                      role="tab"
                      aria-selected={demo.screen === s}
                      aria-label={`Jump to ${s} screen`}
                      onClick={() => demo.setScreen(s)}
                      className={demo.screen === s ? "active" : ""}
                    />
                  ))}
                </div>
              </div>

              {/* Hardware Fallback Buttons */}
              <div className="fallbacks" aria-label="Hardware gesture fallback controls">
                <button
                  type="button"
                  onClick={demo.activateWatch}
                  aria-label="Trigger Triple press gesture for Safety Watch"
                >
                  Triple press
                </button>
                <button
                  type="button"
                  onClick={demo.beginEmergency}
                  aria-label="Trigger Long press gesture for Emergency alert"
                >
                  Long press (SOS)
                </button>
                <button
                  type="button"
                  onClick={demo.cancel}
                  aria-label="Cancel active alert and mark safe"
                >
                  Hold to Disarm
                </button>
                <button
                  type="button"
                  onClick={demo.reset}
                  aria-label="Reset prototype to initial state"
                >
                  <RotateCcw size={12} /> Reset demo
                </button>
              </div>

              <p className="demo-hint">
                App drawer · 5 watch faces · voice memos · SOS controls
              </p>
            </div>

            {/* Panel 2: Guardian Companion View */}
            <div className="demo-panel">
              <div className="panel-label">
                <Mono>Guardian companion view</Mono>
                <span>SIMULATED LIVE SYNC</span>
              </div>

              <Companion demo={demo} />
            </div>
          </div>
        )}
      </div>
    </section>
  );
}
export default DemoSection;
