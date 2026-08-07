import { Header } from "@/components/Header"
import { AccountSection, ReachChip } from "@/screens/account/AccountSection"
import type { SectionReach } from "@/screens/account/AccountSection"
import { PasswordSection } from "@/screens/account/PasswordSection"
import { ProfileSection } from "@/screens/account/ProfileSection"

type SectionDef = {
  id: string
  title: string
  description: string
  reach: SectionReach
  destructive?: boolean
  /** Ausente quando a secao ja tem conteudo funcional (Perfil, US-009). */
  placeholder?: string
}

const SECTIONS: SectionDef[] = [
  {
    id: "perfil",
    title: "Perfil",
    description:
      "Seu nome e e-mail. O nome é como você aparece para quem divide o app com você.",
    reach: "you",
  },
  {
    id: "senha",
    title: "Senha",
    description:
      "Trocar a senha encerra todas as sessões abertas, inclusive esta — você entra de novo com a senha nova.",
    reach: "you",
  },
  {
    id: "casal",
    title: "Casal",
    description:
      "Desfazer o vínculo libera vocês dois para formar um casal novo. O histórico fica guardado, mas sai do alcance dos dois.",
    reach: "both",
    destructive: true,
    placeholder: "A ação de desfazer o vínculo entra aqui.",
  },
  {
    id: "excluir",
    title: "Excluir conta",
    description:
      "Apaga sua conta, suas avaliações e suas notificações. Não dá para voltar atrás.",
    reach: "both",
    destructive: true,
    placeholder: "A ação de excluir a conta entra aqui.",
  },
]

export function AccountScreen() {
  return (
    <div
      className="font-auth-body min-h-svh text-[#f6f4ec]"
      style={{
        background:
          "radial-gradient(1200px 700px at 78% -8%, rgba(255,203,43,.16), transparent 55%), radial-gradient(1000px 600px at 5% 8%, rgba(255,158,44,.10), transparent 50%), #09090a",
      }}
    >
      <Header />
      <main className="mx-auto max-w-[1240px] px-5 pt-10 pb-32 sm:px-8 sm:pt-11">
        <div className="mb-7">
          <div className="mb-2 text-[13px] font-semibold tracking-[.14em] text-[#ff9e2c] uppercase">
            Conta e Casal
          </div>
          <h1 className="font-display text-[clamp(28px,4vw,40px)] font-bold tracking-tight">
            Ajustes e caminhos de volta
          </h1>
          <p className="mt-2 max-w-[58ch] text-[15px] text-[#a6a39a]">
            Corrija seus dados, troque a senha, desfaça o vínculo do casal ou saia
            do app. Cada seção diz de antemão quem ela alcança.
          </p>
        </div>

        <div className="grid grid-cols-1 gap-6 lg:grid-cols-[220px_minmax(0,1fr)] lg:gap-8">
          <nav
            aria-label="Seções da conta"
            className="hidden lg:sticky lg:top-24 lg:block lg:self-start"
          >
            <ul className="m-0 flex list-none flex-col gap-1 p-0">
              {SECTIONS.map((section) => (
                <li key={section.id}>
                  <a
                    href={`#${section.id}`}
                    className="flex items-center gap-2.5 rounded-[10px] px-3 py-2 text-sm font-semibold text-[#a6a39a] transition-colors hover:bg-white/[0.05] hover:text-[#f6f4ec] focus-visible:ring-2 focus-visible:ring-[#ffcb2b] focus-visible:outline-none"
                  >
                    <span
                      aria-hidden="true"
                      className={
                        section.reach === "both"
                          ? "size-1.5 flex-none rounded-full bg-[#ff5c47]"
                          : "size-1.5 flex-none rounded-full bg-[#ffcb2b]"
                      }
                    />
                    {section.title}
                  </a>
                </li>
              ))}
            </ul>

            <div className="mt-4 flex flex-col gap-2 border-t border-white/[0.07] pt-4">
              <ReachChip reach="you" />
              <ReachChip reach="both" />
            </div>
          </nav>

          <div className="flex min-w-0 flex-col gap-5">
            {SECTIONS.map((section) => (
              <AccountSection
                key={section.id}
                id={section.id}
                title={section.title}
                description={section.description}
                reach={section.reach}
                destructive={section.destructive}
              >
                {section.id === "perfil" ? (
                  <ProfileSection />
                ) : section.id === "senha" ? (
                  <PasswordSection />
                ) : (
                  <p className="m-0 rounded-[12px] border border-dashed border-white/10 px-4 py-3.5 text-[13px] text-[#a6a39a]">
                    {section.placeholder}
                  </p>
                )}
              </AccountSection>
            ))}
          </div>
        </div>
      </main>
    </div>
  )
}
