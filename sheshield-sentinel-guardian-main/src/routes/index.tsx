import { createFileRoute } from "@tanstack/react-router";
import { SentinelPage } from "@/components/sentinel-page";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "Naari OS — ESP32-S3 safety watch and Android companion" },
      { name: "description", content: "Naari OS is a working ESP32-S3 safety watch with authenticated Android BLE, SOS, fall monitoring, guardians and voice memo sync." },
      { property: "og:title", content: "Naari OS" },
      { property: "og:description", content: "A connected safety watch and Android companion built as one system." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: Index,
});

function Index() {
  return <SentinelPage />;
}
