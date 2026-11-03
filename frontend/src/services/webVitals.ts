/**
 * P1.12: Web Vitals medidos no navegador (LCP, CLS e INP aproximado por
 * PerformanceObserver). Sem dependência nova e sem endpoint inventado: o
 * observador entrega cada medição ao callback, e o repórter padrão guarda o
 * valor mais recente em `window.__ziipsanWebVitals` e dispara o evento
 * `ziipsan:web-vital` (a ingestão no servidor é trabalho futuro, fora desta
 * frente; nada é enviado para uma URL que não existe).
 */

export type WebVitalName = 'LCP' | 'CLS' | 'INP'

export interface WebVital {
  name: WebVitalName
  /** LCP/INP em milissegundos; CLS sem unidade. */
  value: number
}

export type WebVitalReporter = (vital: WebVital) => void

declare global {
  interface Window {
    __ziipsanWebVitals?: Partial<Record<WebVitalName, number>>
  }
}

interface PerformanceEntryWithExtras extends PerformanceEntry {
  hadRecentInput?: boolean
  processingStart?: number
  value?: number
}

type PerformanceObserverConstructor = new (
  callback: (list: { getEntries: () => PerformanceEntryWithExtras[] }) => void,
) => { observe: (options: { type: string; buffered?: boolean }) => void; disconnect: () => void }

function getObserverCtor(): PerformanceObserverConstructor | null {
  const ctor = (globalThis as { PerformanceObserver?: PerformanceObserverConstructor }).PerformanceObserver
  return typeof ctor === 'function' ? ctor : null
}

/** Observa LCP, CLS e INP. Devolve a função de parada. Sem PerformanceObserver, não faz nada. */
export function observeWebVitals(onReport: WebVitalReporter): () => void {
  const Observer = getObserverCtor()
  if (!Observer) return () => undefined
  const observers: Array<{ disconnect: () => void }> = []
  try {
    const lcp = new Observer((list) => {
      const entries = list.getEntries()
      const last = entries[entries.length - 1]
      if (last) onReport({ name: 'LCP', value: last.startTime })
    })
    lcp.observe({ type: 'largest-contentful-paint', buffered: true })
    observers.push(lcp)
  } catch {
    /* tipo não suportado: segue sem LCP */
  }
  try {
    let cls = 0
    const clsObserver = new Observer((list) => {
      for (const entry of list.getEntries()) {
        if (!entry.hadRecentInput) cls += entry.value ?? 0
      }
      onReport({ name: 'CLS', value: cls })
    })
    clsObserver.observe({ type: 'layout-shift', buffered: true })
    observers.push(clsObserver)
  } catch {
    /* segue sem CLS */
  }
  try {
    let inp = 0
    const inpObserver = new Observer((list) => {
      for (const entry of list.getEntries()) {
        const latency = entry.duration
        if (latency > inp) inp = latency
      }
      onReport({ name: 'INP', value: inp })
    })
    inpObserver.observe({ type: 'event', buffered: true })
    observers.push(inpObserver)
  } catch {
    /* segue sem INP */
  }
  return () => {
    for (const observer of observers) observer.disconnect()
  }
}

/** Repórter padrão: guarda o valor e anuncia por evento DOM. */
export function defaultWebVitalReporter(vital: WebVital): void {
  window.__ziipsanWebVitals = { ...window.__ziipsanWebVitals, [vital.name]: vital.value }
  window.dispatchEvent(new CustomEvent('ziipsan:web-vital', { detail: vital }))
}

/** Últimas medições guardadas pelo repórter padrão (vazio antes da primeira medição). */
export function getWebVitalsSnapshot(): Partial<Record<WebVitalName, number>> {
  return { ...(window.__ziipsanWebVitals ?? {}) }
}

/** Liga a medição com o repórter padrão. Chamado uma vez em main.tsx. */
export function initWebVitals(): () => void {
  return observeWebVitals(defaultWebVitalReporter)
}
