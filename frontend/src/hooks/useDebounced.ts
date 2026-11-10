import { useEffect, useState } from 'react'

/**
 * useDebounced — API derivada do unico uso entregue:
 *   const debounced = useDebounced(q, 300)   (ED-08 CatalogCrud)
 * Devolve o valor estabilizado apos `delay` ms sem mudancas.
 * O valor por omissao do delay nao e determinado por nenhum uso (o unico
 * chamador passa 300 explicito); adopta-se 300 por ser esse o valor do
 * uso entregue — se outro chamador precisar de outro atraso, passa-o.
 */
export function useDebounced<T>(value: T, delay = 300): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay)
    return () => clearTimeout(timer)
  }, [value, delay])
  return debounced
}
