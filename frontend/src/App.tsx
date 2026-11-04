import { lazy, Suspense, type ReactNode } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { ErrorBoundary } from '@components/ErrorBoundary'
import { Layout } from '@components/Layout'
import { VersionBanner } from '@components/VersionBanner'
import { WhatsNew } from '@components/WhatsNew'
import { Spinner } from '@components/ui/Alert'
import { useAuth } from '@hooks/useAuth'
import { AuthCallback } from '@pages/AuthCallback'
import { Login } from '@pages/Login'
import { NotFound } from '@pages/NotFound'

// P1.12: code splitting por rota — cada tela do cadastro vira um chunk próprio,
// carregado sob demanda, em vez de ir todo para o bundle inicial. Login e
// callback continuam ansiosos (são o caminho de entrada).
const Catalogs = lazy(() => import('@pages/cadastro/Catalogs').then((m) => ({ default: m.Catalogs })))
const CustomerDetail = lazy(() => import('@pages/cadastro/CustomerDetail').then((m) => ({ default: m.CustomerDetail })))
const CustomerForm = lazy(() => import('@pages/cadastro/CustomerForm').then((m) => ({ default: m.CustomerForm })))
const CustomerList = lazy(() => import('@pages/cadastro/CustomerList').then((m) => ({ default: m.CustomerList })))
const PropertyDetail = lazy(() => import('@pages/cadastro/PropertyDetail').then((m) => ({ default: m.PropertyDetail })))
const PropertySearch = lazy(() => import('@pages/cadastro/PropertySearch').then((m) => ({ default: m.PropertySearch })))
const PropertyWizard = lazy(() => import('@pages/cadastro/PropertyWizard').then((m) => ({ default: m.PropertyWizard })))
const Territory = lazy(() => import('@pages/cadastro/Territory').then((m) => ({ default: m.Territory })))
const GeneratedFiles = lazy(() => import('@pages/GeneratedFiles').then((m) => ({ default: m.GeneratedFiles })))
const Importacoes = lazy(() => import('@pages/Importacoes').then((m) => ({ default: m.Importacoes })))
const Support = lazy(() => import('@pages/Support').then((m) => ({ default: m.Support })))
const SavedViews = lazy(() => import('@pages/search/SavedViews').then((m) => ({ default: m.SavedViews })))
const IconContactSheetPage = lazy(() =>
  import('@pages/dev/IconContactSheetPage').then((m) => ({ default: m.IconContactSheetPage })),
)
const GisCatalog = lazy(() => import('@pages/gis/GisCatalog').then((m) => ({ default: m.GisCatalog })))
const About = lazy(() => import('@pages/About').then((m) => ({ default: m.About })))
const Central = lazy(() => import('@pages/platform/Central').then((m) => ({ default: m.Central })))

function PrivateRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated, loading } = useAuth()
  const location = useLocation()

  if (loading) return <Spinner label="Carregando..." />
  if (!isAuthenticated) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return <>{children}</>
}

/**
 * Só existem rotas de telas que existem. Dashboard, Faturamento, Cobrança etc. foram removidas
 * porque as páginas nunca foram escritas. S7 (hidrômetros) e S8 (recadastramento) ficam para a P1.
 */
export function App() {
  const location = useLocation()
  return (
    <>
      {/* P0.39: aviso de recarga do SPA antigo (só aparece após o evento de versão). */}
      <VersionBanner />
      {/* P1.12: a fronteira de erro envolve as rotas (navegar limpa o erro) e o
          Suspense cobre o carregamento dos chunks lazy. */}
      <ErrorBoundary resetKey={location.pathname}>
      <Suspense fallback={<Spinner label="Carregando..." />}>
      <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/auth/callback" element={<AuthCallback />} />
      {/* P1.29: folha de contato, so em desenvolvimento; fora dele, login. */}
      <Route
        path="/dev/icones"
        element={import.meta.env.DEV ? <IconContactSheetPage /> : <Navigate to="/login" replace />}
      />
      <Route
        path="/"
        element={
          <PrivateRoute>
            <Layout />
            <WhatsNew />
          </PrivateRoute>
        }
      >
        <Route index element={<Navigate to="/cadastro/imoveis" replace />} />

        <Route path="cadastro/imoveis" element={<PropertySearch />} />
        <Route path="cadastro/imoveis/novo" element={<PropertyWizard />} />
        <Route path="cadastro/imoveis/:id" element={<PropertyDetail />} />
        <Route path="cadastro/imoveis/:id/editar" element={<PropertyWizard />} />

        <Route path="cadastro/clientes" element={<CustomerList />} />
        <Route path="cadastro/clientes/novo" element={<CustomerForm />} />
        <Route path="cadastro/clientes/:id" element={<CustomerDetail />} />
        <Route path="cadastro/clientes/:id/editar" element={<CustomerForm />} />

        <Route path="cadastro/territorio" element={<Territory />} />
        <Route path="cadastro/territorio/:nivel" element={<Territory />} />

        <Route path="cadastro/catalogos" element={<Catalogs />} />

        <Route path="gis/catalogo" element={<GisCatalog />} />
        <Route path="cadastro/catalogos/:tipo" element={<Catalogs />} />

        {/* P1.12: ponto de entrega das exportações assíncronas (e, no futuro, dos
            relatórios agendados da P3.22 — só o item de entrega, não a P3.22). */}
        <Route path="arquivos-gerados" element={<GeneratedFiles />} />

        {/* P1.24: Sobre completo. */}
        <Route path="about" element={<About />} />
        {/* P1.40: histórico de importações por planilha (JOR-26). */}
        <Route path="importacoes" element={<Importacoes />} />
        {/* P1.17: Central Ziip v0 — Visão geral, Plataforma, Segurança e Alertas. */}
        <Route path="platform/central" element={<Central />} />
        <Route path="support" element={<Support />} />
        {/* P1.33: visões salvas de filtros e colunas por usuário. */}
        <Route path="busca/visoes-salvas" element={<SavedViews />} />

        <Route path="*" element={<NotFound />} />
      </Route>
      </Routes>
      </Suspense>
      </ErrorBoundary>
    </>
  )
}

export default App
