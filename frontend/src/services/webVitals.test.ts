import { afterEach, describe, expect, it, vi } from 'vitest'
import { observeWebVitals } from './webVitals'

type Callback = (list: { getEntries: () => Array<Record<string, unknown>> }) => void

describe('observeWebVitals (P1.12)', () => {
  const original = (globalThis as { PerformanceObserver?: unknown }).PerformanceObserver

  afterEach(() => {
    if (original === undefined) delete (globalThis as { PerformanceObserver?: unknown }).PerformanceObserver
    else (globalThis as { PerformanceObserver?: unknown }).PerformanceObserver = original
    vi.restoreAllMocks()
  })

  function installFake(capture: Record<string, Callback>): void {
    class FakeObserver {
      constructor(private callback: Callback) {}
      observe(options: { type: string }): void {
        capture[options.type] = this.callback
      }
      disconnect(): void {}
    }
    ;(globalThis as { PerformanceObserver?: unknown }).PerformanceObserver = FakeObserver
  }

  it('LCP usa a última entrada; CLS soma só sem input recente; INP usa o pior evento', () => {
    const capture: Record<string, Callback> = {}
    installFake(capture)
    const seen: Array<{ name: string; value: number }> = []
    const stop = observeWebVitals((vital) => seen.push(vital))
    capture['largest-contentful-paint']!({ getEntries: () => [{ startTime: 100 }, { startTime: 250 }] })
    capture['layout-shift']!({
      getEntries: () => [
        { value: 0.05, hadRecentInput: false },
        { value: 0.5, hadRecentInput: true },
        { value: 0.02, hadRecentInput: false },
      ],
    })
    capture['event']!({ getEntries: () => [{ duration: 40 }, { duration: 120 }] })
    expect(seen).toContainEqual({ name: 'LCP', value: 250 })
    expect(seen).toContainEqual({ name: 'CLS', value: 0.07 })
    expect(seen).toContainEqual({ name: 'INP', value: 120 })
    stop()
  })

  it('sem PerformanceObserver, não faz nada e não quebra', () => {
    delete (globalThis as { PerformanceObserver?: unknown }).PerformanceObserver
    const stop = observeWebVitals(() => {
      throw new Error('não deveria reportar')
    })
    expect(() => stop()).not.toThrow()
  })
})
