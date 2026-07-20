import * as React from "react"

import { cn } from "@/lib/utils"

function Input({ className, type, ...props }: React.ComponentProps<"input">) {
  return (
    <input
      type={type}
      data-slot="input"
      className={cn(
        "flex h-11 w-full min-w-0 rounded-2xl border border-white/10 bg-white/[0.04] px-4 text-sm text-[#f6f4ec] placeholder:text-[#6f6c62] outline-none transition-colors selection:bg-[#ffcb2b] selection:text-[#09090a]",
        "focus-visible:border-[#ffcb2b]/60 focus-visible:bg-white/[0.06] focus-visible:ring-4 focus-visible:ring-[#ffcb2b]/15",
        "aria-invalid:border-[#ff6b6b]/60 aria-invalid:ring-4 aria-invalid:ring-[#ff6b6b]/15",
        "disabled:cursor-not-allowed disabled:opacity-50",
        className
      )}
      {...props}
    />
  )
}

export { Input }
