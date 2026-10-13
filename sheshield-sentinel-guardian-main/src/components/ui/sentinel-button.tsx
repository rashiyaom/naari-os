import { type ButtonHTMLAttributes } from "react";
import { cn } from "@/lib/utils";

type Props = ButtonHTMLAttributes<HTMLButtonElement> & { variant?: "coral" | "glass" | "dark" | "ghost"; size?: "sm" | "md" | "icon" };

export function SentinelButton({ className, variant = "coral", size = "md", ...props }: Props) {
  return <button className={cn("inline-flex items-center justify-center gap-2 rounded-full font-semibold transition duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-50", variant === "coral" && "bg-primary text-primary-foreground shadow-coral hover:-translate-y-0.5", variant === "glass" && "border border-glass-edge bg-glass text-foreground backdrop-blur-xl hover:bg-glass-strong", variant === "dark" && "bg-foreground text-background hover:opacity-85", variant === "ghost" && "text-inherit hover:bg-glass", size === "sm" && "h-9 px-4 text-xs", size === "md" && "h-12 px-6 text-sm", size === "icon" && "size-10 p-0", className)} {...props} />;
}