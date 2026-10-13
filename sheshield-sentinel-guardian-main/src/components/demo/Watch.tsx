import { useEffect, useState } from "react";
import type { useSentinelDemo } from "@/hooks/use-sentinel-demo";
import WatchSimulator, { SCREENS } from "@/components/watch/WatchSimulator";

type Props = {
  demo: ReturnType<typeof useSentinelDemo>;
};

export function Watch({ demo }: Props) {
  const [targetScreen, setTargetScreen] = useState<string | undefined>(undefined);

  // Keep the hardware preview aligned with both state transitions and the
  // quick-jump dots below the watch.
  useEffect(() => {
    const screenMap = {
      home: SCREENS.SAFE,
      watch: SCREENS.WATCH,
      pending: SCREENS.EMERGENCY,
      emergency: SCREENS.EMERGENCY,
      contacts: SCREENS.CONTACTS,
      config: SCREENS.CONTROL_CENTER,
      network: SCREENS.SYSTEM,
    } as const;
    setTargetScreen(screenMap[demo.screen]);
  }, [demo.screen]);

  const handleStateChange = (newState: "SAFE" | "WATCH" | "EMERGENCY") => {
    if (newState === "EMERGENCY") {
      demo.beginEmergency();
    } else if (newState === "SAFE") {
      demo.cancel();
    } else if (newState === "WATCH") {
      demo.activateWatch();
    }
  };

  return (
    <div className="flex justify-center items-center py-2">
      <WatchSimulator
        targetScreen={targetScreen}
        onStateChange={handleStateChange}
      />
    </div>
  );
}
export default Watch;
