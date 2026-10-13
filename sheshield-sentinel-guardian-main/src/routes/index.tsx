import { createFileRoute } from "@tanstack/react-router";
import { SentinelPage } from "@/components/sentinel-page";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "SheShield Sentinel — Safety, before emergency" },
      { name: "description", content: "A proactive, phone-independent women's safety wearable by Team Puraanmaya for ILS Summit 2026." },
      { property: "og:title", content: "SheShield Sentinel" },
      { property: "og:description", content: "A silent signal for help before danger becomes an emergency." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: Index,
});

function Index() {
  return <SentinelPage />;
}
