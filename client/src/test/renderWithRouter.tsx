import type { ReactElement } from "react"
import { render, type RenderResult } from "@testing-library/react"
import { MemoryRouter } from "react-router-dom"

interface RenderWithRouterOptions {
  /** Initial URL the MemoryRouter boots on. Defaults to "/". */
  route?: string
}

/** Mounts `ui` inside a MemoryRouter, for components that call hooks like
 *  `useNavigate`/`useLocation` or render `Link`/`NavLink`. */
export function renderWithRouter(
  ui: ReactElement,
  { route = "/" }: RenderWithRouterOptions = {}
): RenderResult {
  return render(<MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>)
}
