import { Heart } from "lucide-react"

import { Avatar, AvatarFallback } from "@/components/ui/avatar"

interface CoupleAvatarsProps {
  userInitial: string
  partnerInitial: string
}

export function CoupleAvatars({ userInitial, partnerInitial }: CoupleAvatarsProps) {
  return (
    <div className="relative h-[38px] w-[46px] flex-none">
      {/* Partner avatar (left, behind) */}
      <Avatar
        className="absolute left-0 top-[2px] size-[34px] border-2 border-[#09090a] after:border-0"
        style={{
          background: "linear-gradient(160deg, hsl(34 92% 42%), hsl(34 88% 24%))",
        }}
      >
        <div
          className="absolute inset-0 rounded-full"
          style={{
            background:
              "repeating-linear-gradient(135deg, rgba(255,255,255,.06) 0 5px, transparent 5px 10px)",
          }}
        />
        <AvatarFallback className="bg-transparent text-xs font-semibold text-white/90">
          {partnerInitial}
        </AvatarFallback>
      </Avatar>

      {/* User avatar (right, in front) */}
      <Avatar
        className="absolute right-0 top-[2px] size-[34px] border-2 border-[#09090a] after:border-0"
        style={{
          background: "linear-gradient(160deg, hsl(45 92% 42%), hsl(45 88% 24%))",
        }}
      >
        <div
          className="absolute inset-0 rounded-full"
          style={{
            background:
              "repeating-linear-gradient(135deg, rgba(255,255,255,.06) 0 5px, transparent 5px 10px)",
          }}
        />
        <AvatarFallback className="bg-transparent text-xs font-semibold text-white/90">
          {userInitial}
        </AvatarFallback>
      </Avatar>

      {/* Heart badge */}
      <div className="absolute -bottom-[3px] left-1/2 grid size-[18px] -translate-x-1/2 place-items-center rounded-full border-2 border-[#09090a] bg-[#ffcb2b]">
        <Heart className="size-[9px] fill-current text-[#111]" />
      </div>
    </div>
  )
}
