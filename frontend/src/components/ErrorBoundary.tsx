import { Component, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { Alert } from '@components/ui/Alert'
import { Button } from '@components/ui/Button'

interface ErrorBoundaryProps {
  children: ReactNode
  /** Mudar o valor limpa um erro anterior (a App passa o caminho atual). */
  resetKey?: string
  onError?: (error: Error) => void
}

interface ErrorBoundaryState {
  error: Error | null
}

/**
 * P1.12: fronteira de erro do SPA. Um erro de renderização numa tela não derruba
 * a aplicação inteira: mostra uma mensagem, oferece nova tentativa (que remonta a
 * tela) e um caminho de volta. O erro em si não é exibido em detalhe ao usuário
 * (stack não vaza para a tela); vai para o onError (padrão: console.error).
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  override state: ErrorBoundaryState = { error: null }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { error }
  }

  override componentDidCatch(error: Error): void {
    if (this.props.onError) this.props.onError(error)
    else console.error(error)
  }

  override componentDidUpdate(prevProps: ErrorBoundaryProps): void {
    if (prevProps.resetKey !== this.props.resetKey && this.state.error) {
      this.setState({ error: null })
    }
  }

  private retry = (): void => {
    this.setState({ error: null })
  }

  override render(): ReactNode {
    if (this.state.error) {
      return (
        <div className="space-y-4 p-4 sm:p-6">
          <Alert tone="error" title="Algo deu errado nesta tela">
            <p>Não foi possível exibir esta tela. Tente novamente; se o problema persistir, volte à pesquisa e recomece.</p>
          </Alert>
          <div className="flex flex-wrap gap-2">
            <Button onClick={this.retry}>Tentar novamente</Button>
            <Link to="/cadastro/imoveis" className="inline-flex h-10 items-center rounded-lg border px-4 text-sm font-medium hover:bg-accent">
              Voltar à pesquisa
            </Link>
          </div>
        </div>
      )
    }
    return this.props.children
  }
}
