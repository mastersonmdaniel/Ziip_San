import { useCallback, useEffect, useState } from 'react'

/**
 * P1.33: densidade da tabela (confortável e compacta), preferência do dispositivo
 * guardada em localStorage. É só apresentação: confortável é o espaçamento atual
 * das tabelas; compacta reduz a altura das linhas. A visão salva pode fixar a
 * densidade dela (o servidor valida o domínio); fora de uma visão, vale esta
 * preferência. Falha de storage cai para confortável, sem quebrar a tela.
 */
export type TableDensity = 'comfortable' | 'compact'

const KEY = 'ziipsan:table-density:v1'

function read(): TableDensity {
  try {
    const raw = localStorage.getItem(KEY)
    return raw === 'compact' ? 'compact' : 'comfortable'
  } catch {
    return 'comfortable'
  }
}

export function useDensity() {
  const [density, setDensityState] = useState<TableDensity>(read)

  const setDensity = useCallback((next: TableDensity) => {
    setDensityState(next)
    try {
      localStorage.setItem(KEY, next)
    } catch {
      /* storage indisponível: a preferência vale só nesta renderização */
    }
  }, [])

  return { density, setDensity }
}

/** Mantém o document sincronizado (útil se outra aba mudar a preferência). */
export function useDensitySync() {
  const { density, setDensity } = useDensity()
  useEffect(() => {
    const onStorage = (event: StorageEvent) => {
      if (event.key === KEY) setDensity(event.newValue === 'compact' ? 'compact' : 'comfortable')
    }
    window.addEventListener('storage', onStorage)
    return () => window.removeEventListener('storage', onStorage)
  }, [setDensity])
  return { density, setDensity }
}
