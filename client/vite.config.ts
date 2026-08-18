import path from "path"
import tailwindcss from "@tailwindcss/vite"
import react from "@vitejs/plugin-react"
import { defineConfig } from "vitest/config"

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  define: {
    global: "globalThis",
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    globals: false,
    css: false,
    coverage: {
      provider: "v8",
      reporter: ["text", "html"],
      exclude: [
        "src/test/**",
        "src/components/ui/**",
        "src/main.tsx",
        "*.config.*",
        "src/types/**",
      ],
      // Patamar medido em 2026-08-11 (Epico 11, US-016), arredondado para baixo.
      // Subir este limiar e uma task propria, nao um efeito colateral de outro PR.
      thresholds: {
        statements: 52,
        branches: 37,
        functions: 40,
        lines: 55,
      },
    },
  },
})
