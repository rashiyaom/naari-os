import { useCallback, useEffect, useRef, useState } from "react";

export type SentinelState = "SAFE" | "WATCH" | "EMERGENCY_PENDING" | "EMERGENCY";
export type WatchScreen = "home" | "watch" | "pending" | "emergency" | "contacts" | "config" | "network";

export type DemoEvent = {
  id: string;
  time: string;
  text: string;
  buffered?: boolean;
};

const stamp = () =>
  new Date().toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });

export function useSentinelDemo() {
  const [state, setState] = useState<SentinelState>("SAFE");
  const [screen, setScreen] = useState<WatchScreen>("home");
  const [connected, setConnected] = useState(true);
  const [events, setEvents] = useState<DemoEvent[]>([
    { id: "init-0", time: stamp(), text: "Naari ready · authenticated BLE link active" },
  ]);
  const [buffered, setBuffered] = useState<DemoEvent[]>([]);
  const [confirmationWindow, setConfirmationWindow] = useState(30);
  const [countdown, setCountdown] = useState(30);
  const [voiceTrigger, setVoiceTrigger] = useState(true);
  const [vibration, setVibration] = useState(true);
  const [evidence, setEvidence] = useState(true);
  const [contextSensor, setContextSensor] = useState(true);

  const lastLoggedRef = useRef<{ text: string; time: number }>({ text: "", time: 0 });

  const log = useCallback(
    (text: string) => {
      const now = Date.now();
      // Deduplication guard: ignore identical log text within 1.2 seconds
      if (lastLoggedRef.current.text === text && now - lastLoggedRef.current.time < 1200) {
        return;
      }
      lastLoggedRef.current = { text, time: now };

      const item: DemoEvent = {
        id: `${now}-${Math.random().toString(36).slice(2, 6)}`,
        time: stamp(),
        text,
      };

      if (connected) {
        setEvents((current) => [item, ...current].slice(0, 14));
      } else {
        setBuffered((current) => [...current, { ...item, buffered: true }]);
      }
    },
    [connected],
  );

  const activateWatch = useCallback(() => {
    setState("WATCH");
    setScreen("watch");
    log("Guard mode activated · motion monitoring active");
  }, [log]);

  const activateEmergency = useCallback(() => {
    setState("EMERGENCY");
    setScreen("emergency");
    const newLogs = [
      "Emergency mode activated",
      "Authenticated watch request received by Android",
      "Guardian actions requested from the phone",
      "Phone result returned to the watch",
    ];
    newLogs.forEach(log);
  }, [log]);

  const beginEmergency = useCallback(() => {
    if (state === "EMERGENCY") return;
    setState("EMERGENCY_PENDING");
    setScreen("pending");
    setCountdown(confirmationWindow);
    log(`Emergency trigger detected · ${confirmationWindow}s silent confirmation started`);
  }, [state, confirmationWindow, log]);

  // Pure countdown timer effect: decrements countdown without embedded side-effects
  useEffect(() => {
    if (screen !== "pending") return;
    const interval = setInterval(() => {
      setCountdown((prev) => {
        if (prev <= 1) {
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(interval);
  }, [screen]);

  // Separate effect to trigger emergency once countdown hits zero
  useEffect(() => {
    if (screen === "pending" && countdown === 0) {
      activateEmergency();
    }
  }, [screen, countdown, activateEmergency]);

  const cancel = useCallback(() => {
    setState("SAFE");
    setScreen("home");
    setCountdown(confirmationWindow);
    log("Cancelled by wearer · marked safe");
  }, [confirmationWindow, log]);

  const reset = useCallback(() => {
    setState("SAFE");
    setScreen("home");
    setConnected(true);
    setBuffered([]);
    setCountdown(confirmationWindow);
    setEvents([
      {
        id: `reset-${Date.now()}`,
        time: stamp(),
        text: "Demo reset · Naari ready · authenticated BLE link active",
      },
    ]);
  }, [confirmationWindow]);

  const toggleNetwork = useCallback(() => {
    setConnected((wasConnected) => {
      const willBeConnected = !wasConnected;
      if (willBeConnected) {
        setBuffered((currentBuffered) => {
          if (currentBuffered.length > 0) {
            const flushNotice: DemoEvent = {
              id: `flush-${Date.now()}`,
              time: stamp(),
              text: `BLE reconnected · ${currentBuffered.length} buffered event${currentBuffered.length === 1 ? "" : "s"} restored`,
            };
            const unbuffered = currentBuffered.map((ev) => ({ ...ev, buffered: false }));
            setEvents((curr) => [flushNotice, ...unbuffered.reverse(), ...curr].slice(0, 14));
          } else {
            const reconnectNotice: DemoEvent = {
              id: `reconnect-${Date.now()}`,
              time: stamp(),
              text: "Authenticated BLE link restored",
            };
            setEvents((curr) => [reconnectNotice, ...curr].slice(0, 14));
          }
          return [];
        });
      } else {
        const item: DemoEvent = {
          id: `offline-${Date.now()}`,
          time: stamp(),
          text: "BLE link lost · phone actions are unavailable",
          buffered: true,
        };
        setBuffered((curr) => [...curr, item]);
      }
      return willBeConnected;
    });
  }, []);

  const triggerVoiceSimulation = useCallback(() => {
    if (!voiceTrigger) {
      log("Voice keyword ignored · Voice trigger disabled in config");
      return;
    }
    if (state === "SAFE") {
      log("Discreet voice phrase detected ('Sentinel Watch')");
      activateWatch();
    } else {
      log("Discreet voice phrase detected ('Sentinel Help')");
      beginEmergency();
    }
  }, [voiceTrigger, state, activateWatch, beginEmergency, log]);

  return {
    state,
    setState,
    screen,
    setScreen,
    connected,
    events,
    buffered,
    confirmationWindow,
    setConfirmationWindow,
    countdown,
    voiceTrigger,
    setVoiceTrigger,
    vibration,
    setVibration,
    evidence,
    setEvidence,
    contextSensor,
    setContextSensor,
    activateWatch,
    beginEmergency,
    activateEmergency,
    cancel,
    reset,
    toggleNetwork,
    triggerVoiceSimulation,
    log,
  };
}
