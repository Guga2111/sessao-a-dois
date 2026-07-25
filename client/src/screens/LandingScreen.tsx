import { DashboardPreview } from "@/components/landing/DashboardPreview"
import { FaqSection } from "@/components/landing/FaqSection"
import { FeatureGrid } from "@/components/landing/FeatureGrid"
import { FinalCta } from "@/components/landing/FinalCta"
import { HowItWorks } from "@/components/landing/HowItWorks"
import { LandingFooter } from "@/components/landing/LandingFooter"
import { LandingHeader } from "@/components/landing/LandingHeader"
import { LandingHero } from "@/components/landing/LandingHero"
import { MatchShowcase } from "@/components/landing/MatchShowcase"
import { Testimonials } from "@/components/landing/Testimonials"

export function LandingScreen() {
  return (
    <div
      className="font-auth-body min-h-screen text-[#f6f4ec]"
      style={{
        background:
          "radial-gradient(1200px 700px at 78% -8%, rgba(255,203,43,.16), transparent 55%), radial-gradient(1000px 600px at 5% 8%, rgba(255,158,44,.10), transparent 50%), #09090a",
      }}
    >
      <LandingHeader />
      <LandingHero />
      <HowItWorks />
      <FeatureGrid />
      <MatchShowcase />
      <DashboardPreview />
      <Testimonials />
      <FaqSection />
      <FinalCta />
      <LandingFooter />
    </div>
  )
}
