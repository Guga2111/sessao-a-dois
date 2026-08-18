import { afterEach } from "vitest"

import { useAuthStore } from "@/stores/useAuthStore"
import { useMatchStore } from "@/stores/useMatchStore"
import { useNotificationStore } from "@/stores/useNotificationStore"

// Zustand stores are module-level singletons: without this, an action a test
// calls (login, connect, pushIncoming, ...) leaks into every test that runs
// after it in the same file/worker. Imported (for its side effect) by
// setup.ts, so this afterEach runs for every test file, not just ones that
// import it directly.
afterEach(() => {
  useAuthStore.setState(useAuthStore.getInitialState(), true)
  useMatchStore.setState(useMatchStore.getInitialState(), true)
  useNotificationStore.setState(useNotificationStore.getInitialState(), true)
})
