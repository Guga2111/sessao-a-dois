import { cva, type VariantProps } from "class-variance-authority"

import { cn } from "@/lib/utils"

const badgeVariants = cva(
  "inline-flex flex-none items-center gap-1.5 rounded-full border px-2.5 py-1 text-[11px] font-semibold tracking-[.02em] whitespace-nowrap",
  {
    variants: {
      tone: {
        neutral: "border-border bg-white/5 text-pill-foreground",
        primary: "border-primary/30 bg-primary/10 text-primary",
        coral: "border-coral/30 bg-coral/8 text-coral-chip",
        muted: "border-transparent bg-white/[0.05] text-muted-foreground",
      },
    },
    defaultVariants: {
      tone: "neutral",
    },
  }
)

function Badge({
  className,
  tone = "neutral",
  ...props
}: React.ComponentProps<"span"> & VariantProps<typeof badgeVariants>) {
  return (
    <span
      data-slot="badge"
      className={cn(badgeVariants({ tone, className }))}
      {...props}
    />
  )
}

// eslint-disable-next-line react-refresh/only-export-components -- shadcn convention: co-locate cva variants with the component
export { Badge, badgeVariants }
