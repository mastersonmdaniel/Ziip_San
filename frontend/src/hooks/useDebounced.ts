import { useEffect, useState } from 'react'

/** Devolve `value` só depois de `delayMs` sem mudanças (evita uma requisição por tecla). */
export function useDebounced<T>(value: T, delayMs = 300): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const id = window.setTimeout(() => setDebounced(value), delayMs)
    return () => window.clearTimeout(id)
  }, [value, delayMs])
  return debounced
}
