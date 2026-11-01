import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, expect } from 'vitest'
import * as axeMatchers from 'vitest-axe/matchers'
import { clearXsrfCookie } from './auth'

expect.extend(axeMatchers)

// Todo teste começa sem sessão: no fluxo BFF (P0.14) não há cliente OIDC falso nem
// token em storage — a sessão é um cookie HttpOnly do servidor, fora do alcance do
// jsdom. Quem precisa de sessão usa o adaptador falso do axios (/api/me) ou o cookie
// anti-CSRF legível (setXsrfCookie). Nenhum teste fala com um Keycloak de verdade.
afterEach(() => {
  cleanup()
  clearXsrfCookie()
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
