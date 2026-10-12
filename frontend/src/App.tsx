import type { ReactNode } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { Layout } from '@components/Layout'
import { Spinner } from '@components/ui/Alert'
import { useAuth } from '@hooks/useAuth'
import { AuthCallback } from '@pages/AuthCallback'
import { Login } from '@pages/Login'
import { NotFound } from '@pages/NotFound'
import { Catalogs } from '@pages/cadastro/Catalogs'
import { CustomerDetail } from '@pages/cadastro/CustomerDetail'
import { CustomerForm } from '@pages/cadastro/CustomerForm'
import { CustomerList } from '@pages/cadastro/CustomerList'
import { PropertyDetail } from '@pages/cadastro/PropertyDetail'
import { PropertySearch } from '@pages/cadastro/PropertySearch'
import { PropertyWizard } from '@pages/cadastro/PropertyWizard'
import { Territory } from '@pages/cadastro/Territory'

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
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/auth/callback" element={<AuthCallback />} />
      <Route
        path="/"
        element={
          <PrivateRoute>
            <Layout />
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
        <Route path="cadastro/catalogos/:tipo" element={<Catalogs />} />

        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  )
}

export default App
