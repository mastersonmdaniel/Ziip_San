/**
 * Ajudantes de teste para o fluxo BFF (P0.14). Não há mais cliente OIDC falso: a
 * sessão é um cookie HttpOnly do servidor, que o jsdom não consegue pôr (HttpOnly
 * não existe aqui) — os testes de HTTP usam o adaptador falso do axios, e os
 * testes do módulo de auth usam o cookie anti-CSRF (legível) e o `browser.assign`
 * espionado. Nenhum token aparece em storage em nenhum teste.
 */

/** Põe o cookie anti-CSRF legível (o que o backend grava em /bff/csrf). */
export function setXsrfCookie(token = 'csrf-de-teste'): void {
  document.cookie = `XSRF-TOKEN=${encodeURIComponent(token)}; path=/`
}

/** Apaga o cookie anti-CSRF. */
export function clearXsrfCookie(): void {
  document.cookie = 'XSRF-TOKEN=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT'
}
