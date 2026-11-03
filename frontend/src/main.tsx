import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Toaster } from 'react-hot-toast'
import { App } from './App'
import { AuthProvider } from '@hooks/useAuth'
import { ThemeProvider } from '@hooks/useTheme'
import { BrandProvider } from './brand/BrandProvider'
import { loadRuntimeConfig } from '@services/runtimeConfig'
import { queryRetry } from '@services/registry/errors'
import { initWebVitals } from '@services/webVitals'
import '@theme/palettes.css'
import './index.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60,
      gcTime: 1000 * 60 * 10,
      // P1.12: nova tentativa por tipo de erro — rede/timeout/5xx/429, até 3 vezes.
      // Erro de validação, 403, 404 ou 409 não é tentado de novo.
      retry: queryRetry,
      refetchOnWindowFocus: false,
    },
  },
})

// P1.12: Web Vitals medidos desde o arranque (LCP, CLS, INP; ver webVitals.ts).
initWebVitals()

const rootElement = document.getElementById('root')
if (!rootElement) throw new Error('Elemento #root não encontrado')

// P0.5: a config de runtime (/config.json ou window.__ZIIPSAN_CONFIG__) é lida ANTES
// do app renderizar — e, portanto, antes de qualquer chamada de API ou login OIDC.
// Sem config (dev, ou deploy sem config.json), os fallbacks VITE_* valem como antes.
async function start(): Promise<void> {
  await loadRuntimeConfig()
  render()
}

function render(): void {
  // O narrowing do `if (!rootElement) throw` acima não atravessa a fronteira da função
  // para o TypeScript: reconfere aqui em vez de confiar no valor do módulo.
  const el = document.getElementById('root')
  if (!el) throw new Error('Elemento #root não encontrado')
  ReactDOM.createRoot(el).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <ThemeProvider>
          <BrandProvider>
            <AuthProvider>
              <App />
              <Toaster position="top-right" toastOptions={{ duration: 4000, style: { background: 'hsl(var(--card))', color: 'hsl(var(--card-foreground))', border: '1px solid hsl(var(--border))' } }} />
            </AuthProvider>
          </BrandProvider>
        </ThemeProvider>
      </BrowserRouter>
    </QueryClientProvider>
  </React.StrictMode>,
  )
}

void start()
