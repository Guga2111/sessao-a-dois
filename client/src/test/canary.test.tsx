import { describe, expect, it } from "vitest"
import { render, screen } from "@testing-library/react"

function CanaryButton() {
  return <button type="button">Canario</button>
}

describe("canary", () => {
  it("renders a real React component in jsdom", () => {
    render(<CanaryButton />)

    expect(screen.getByRole("button", { name: "Canario" })).toBeInTheDocument()
  })
})
