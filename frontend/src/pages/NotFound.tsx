import { Link } from 'react-router-dom'
import { PageHeader } from '@components/ui/PageHeader'

export function NotFound() {
  return (
    <div className="space-y-4">
      <PageHeader title="Página não encontrada" description="O endereço não corresponde a nenhuma tela do sistema." />
      <Link to="/cadastro/imoveis" className="text-sm font-medium text-primary underline underline-offset-4">
        Ir para Imóveis
      </Link>
    </div>
  )
}
