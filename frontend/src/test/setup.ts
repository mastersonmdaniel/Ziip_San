import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, beforeEach, expect } from 'vitest'
import * as axeMatchers from 'vitest-axe/matchers'
import { installFakeAuth } from './auth'
import { setAuthClientForTests } from '@services/auth/oidc'

expect.extend(axeMatchers)

// Todo teste começa sem sessão e com um cliente OIDC falso: nenhum teste fala com um Keycloak de
// verdade nem herda o login de outro. Quem precisa de sessão chama signInForTests().
beforeEach(() => {
  installFakeAuth(null)
})

afterEach(() => {
  cleanup()
  setAuthClientForTests(null)
  sessionStorage.clear()
  localStorage.clear()
})

// jsdom não implementa matchMedia nem scrollIntoView.
if (typeof window.matchMedia !== 'function') {
  window.matchMedia = (query: string): MediaQueryList => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => undefined,
    removeListener: () => undefined,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
    dispatchEvent: () => false,
  })
}

// O axe tenta usar canvas para medir contraste; o jsdom não implementa e imprime ruído.
HTMLCanvasElement.prototype.getContext = () => null
