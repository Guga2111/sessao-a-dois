import { Slider as SliderPrimitive } from "@base-ui/react/slider"

import { cn } from "@/lib/utils"

function Slider({ className, ...props }: SliderPrimitive.Root.Props) {
  return (
    <SliderPrimitive.Root
      data-slot="slider"
      className={cn("relative flex w-full touch-none items-center", className)}
      {...props}
    />
  )
}

function SliderControl({
  className,
  ...props
}: SliderPrimitive.Control.Props) {
  return (
    <SliderPrimitive.Control
      data-slot="slider-control"
      className={cn(
        "relative flex h-4 w-full cursor-pointer items-center",
        className
      )}
      {...props}
    />
  )
}

function SliderTrack({ className, ...props }: SliderPrimitive.Track.Props) {
  return (
    <SliderPrimitive.Track
      data-slot="slider-track"
      className={cn(
        "h-1 w-full rounded-full bg-white/10",
        className
      )}
      {...props}
    />
  )
}

function SliderIndicator({
  className,
  ...props
}: SliderPrimitive.Indicator.Props) {
  return (
    <SliderPrimitive.Indicator
      data-slot="slider-indicator"
      className={cn("h-full rounded-full bg-[#ffcb2b]", className)}
      {...props}
    />
  )
}

function SliderThumb({ className, ...props }: SliderPrimitive.Thumb.Props) {
  return (
    <SliderPrimitive.Thumb
      data-slot="slider-thumb"
      className={cn(
        "block size-3.5 rounded-full border-2 border-[#ffcb2b] bg-[#ffcb2b] outline-none transition-shadow focus-visible:shadow-[0_0_0_4px_rgba(255,203,43,.3)]",
        className
      )}
      {...props}
    />
  )
}

export { Slider, SliderControl, SliderTrack, SliderIndicator, SliderThumb }
