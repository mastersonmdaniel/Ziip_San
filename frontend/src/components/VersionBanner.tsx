import { useEffect, useState } from 'react'
import { Alert } from '@components/ui/Alert'
import { Button } from '@components/ui/Button'
import { VERSION_OUTDATED_EVENT, type VersionOutdatedDetail } from '@services/versionInfo'

/**
 * P0.39: aviso de recarga do SPA antigo. O banner só aparece quando o interceptor de
 * resposta (api.ts) detecta Ziip-Min-Client-Version acima da versão deste build — nunca
 * por conta própria. O botão recarrega a página, buscando o bundle novo.
 */
export function VersionBanner({ onReload }: { onReload?: () => void }) {
  const [detail, setDetail] = useState<VersionOutdatedDetail | null>(null)

  useEffect(() => {
    const handler = (event: Event) => {
      setDetail((event as CustomEvent<VersionOutdatedDetail>).detail)
    }
    window.addEventListener(VERSION_OUTDATED_EVENT, handler)
    return () => window.removeEventListener(VERSION_OUTDATED_EVENT, handler)
  }, [])

  if (!detail) return null

  return (
    <div className="p-4 pb-0">
      <Alert tone="warning" title="Há uma versão nova do Ziip SAN">
        <p>
          Você está usando uma versão antiga do aplicativo (o servidor está na versão{' '}
          {detail.serverVersion}). Recarregue a página para continuar com a versão atual.
        </p>
        <Button
          type="button"
          onClick={() => (onReload ? onReload() : window.location.reload())}
        >
          Recarregar agora
        </Button>
      </Alert>
    </div>
  )
}
