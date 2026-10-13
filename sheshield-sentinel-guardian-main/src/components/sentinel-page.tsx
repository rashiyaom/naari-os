import { useReducedMotion } from "framer-motion";
import { useEffect } from "react";
import { useSentinelDemo } from "@/hooks/use-sentinel-demo";
import { Hero } from "@/components/sections/Hero";
import { GapSection } from "@/components/sections/GapSection";
import { ModelSection } from "@/components/sections/ModelSection";
import { DemoSection } from "@/components/sections/DemoSection";
import { HowSection } from "@/components/sections/HowSection";
import { FeaturesSection } from "@/components/sections/FeaturesSection";
import { ArchitectureSection } from "@/components/sections/ArchitectureSection";
import { PrivacySection } from "@/components/sections/PrivacySection";
import { CasesAndRisks } from "@/components/sections/CasesAndRisks";
import { LimitsSection } from "@/components/sections/LimitsSection";
import { RoadmapSection } from "@/components/sections/RoadmapSection";
import { Closing } from "@/components/sections/Closing";
import { Footer } from "@/components/sections/Footer";

export function SentinelPage() {
  const demo = useSentinelDemo();
  const reduce = useReducedMotion();

  // Handle prefers-reduced-motion for smooth scrolling
  useEffect(() => {
    document.documentElement.style.scrollBehavior = reduce ? "auto" : "smooth";
    return () => {
      document.documentElement.style.scrollBehavior = "";
    };
  }, [reduce]);

  return (
    <main className="min-h-screen">
      <Hero />
      <GapSection />
      <ModelSection />
      <DemoSection demo={demo} />
      <HowSection />
      <FeaturesSection />
      <ArchitectureSection />
      <PrivacySection />
      <CasesAndRisks />
      <LimitsSection />
      <RoadmapSection />
      <Closing />
      <Footer />
    </main>
  );
}