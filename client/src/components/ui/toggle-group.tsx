import { Toggle as TogglePrimitive } from "@base-ui/react/toggle"
import { ToggleGroup as ToggleGroupPrimitive } from "@base-ui/react/toggle-group"

import { cn } from "@/lib/utils"

function ToggleGroup<Value extends string>({
  className,
  ...props
}: ToggleGroupPrimitive.Props<Value>) {
  return (
    <ToggleGroupPrimitive
      data-slot="toggle-group"
      className={cn("flex flex-wrap gap-2", className)}
      {...props}
    />
  )
}

function ToggleGroupItem<Value extends string>({
  className,
  ...props
}: TogglePrimitive.Props<Value>) {
  return (
    <TogglePrimitive
      data-slot="toggle-group-item"
      className={cn(
        "cursor-pointer rounded-full border border-white/10 bg-transparent px-3.5 py-1.5 text-[13px] font-semibold text-[#f6f4ec] transition-colors outline-none select-none hover:bg-white/[0.06] data-[pressed]:border-[#ffcb2b] data-[pressed]:bg-[#ffcb2b] data-[pressed]:text-[#09090a] disabled:pointer-events-none disabled:opacity-50",
        className
      )}
      {...props}
    />
  )
}

export { ToggleGroup, ToggleGroupItem }
