import { cva, type VariantProps } from "class-variance-authority"

import { cn } from "@/lib/utils"

const chipVariants = cva(
  "inline-flex cursor-pointer items-center gap-2 rounded-full border px-4 py-2.5 text-[13px] font-semibold whitespace-nowrap transition-colors outline-none disabled:cursor-not-allowed disabled:pointer-events-none disabled:opacity-40",
  {
    variants: {
      active: {
        true: "border-primary/50 bg-primary/12 text-primary",
        false:
          "border-border bg-card text-foreground hover:bg-white/[0.06] data-[popup-open]:bg-white/[0.06]",
      },
    },
    defaultVariants: {
      active: false,
    },
  }
)

function Chip({
  className,
  active = false,
  type = "button",
  ...props
}: React.ComponentProps<"button"> & VariantProps<typeof chipVariants>) {
  return (
    <button
      data-slot="chip"
      type={type}
      aria-pressed={active ?? false}
      className={cn(chipVariants({ active, className }))}
      {...props}
    />
  )
}

// eslint-disable-next-line react-refresh/only-export-components -- shadcn convention: co-locate cva variants with the component
export { Chip, chipVariants }
