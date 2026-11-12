import { lazy, Suspense, type ReactNode } from 'react'
import { Navigate, Route, Routes, useLocation, useParams } from 'react-router-dom'
import { ErrorBoundary } from '@components/ErrorBoundary'
import { Layout } from '@components/Layout'
import { VersionBanner } from '@components/VersionBanner'
import { WhatsNew } from '@components/WhatsNew'
import { Spinner } from '@components/ui/Alert'
import { useAuth } from '@hooks/useAuth'
import { useTheme } from '@hooks/useTheme'
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
const Templates = lazy(() => import('@pages/Templates').then((m) => ({ default: m.Templates })))
const SavedViews = lazy(() => import('@pages/search/SavedViews').then((m) => ({ default: m.SavedViews })))
const IconContactSheetPage = lazy(() =>
  import('@pages/dev/IconContactSheetPage').then((m) => ({ default: m.IconContactSheetPage })),
)
const GisCatalog = lazy(() => import('@pages/gis/GisCatalog').then((m) => ({ default: m.GisCatalog })))
const GisMap = lazy(() => import('@pages/gis/GisMap').then((m) => ({ default: m.GisMap })))
const GisStyles = lazy(() => import('@pages/gis/GisStyles').then((m) => ({ default: m.GisStyles })))
const ChangeSetReview = lazy(() => import('@pages/gis/ChangeSetReview').then((m) => ({ default: m.ChangeSetReview })))
// Frente 5 do momento final do front (decisao 65): editor web GIS-09/ED-53
// (export nomeado) e tematicos GIS-08 (export padrao) — mesmo padrao lazy
// por rota da P1.12 das demais telas GIS.
const GisWebEditor = lazy(() => import('@pages/gis/GisWebEditor').then((m) => ({ default: m.GisWebEditor })))
const GisThematics = lazy(() => import('@pages/gis/GisThematics'))
// Telas da Onda II (decisões 57/65): P-II3-TELA (ciclo da tarifa social)
// e P-II4-TELA (SLA/exigências das OS) — portas tipadas, sem fiação HTTP
// (P-II3-INTEGRACAO / P-II4-FIACAO pendentes).
const SocialTariffCycle = lazy(() => import('@pages/comercial/SocialTariffCycle').then((m) => ({ default: m.SocialTariffCycle })))
const SlaOrders = lazy(() => import('@pages/os/SlaOrders').then((m) => ({ default: m.SlaOrders })))
const About = lazy(() => import('@pages/About').then((m) => ({ default: m.About })))
const Integration = lazy(() => import('@pages/Integration').then((m) => ({ default: m.Integration })))
const Central = lazy(() => import('@pages/platform/Central').then((m) => ({ default: m.Central })))
const DocumentosFiscais = lazy(() => import('@pages/fiscal/DocumentosFiscais').then((m) => ({ default: m.DocumentosFiscais })))
const MinhasTarefas = lazy(() => import('@pages/tarefas/MinhasTarefas').then((m) => ({ default: m.MinhasTarefas })))
const Agenda = lazy(() => import('@pages/agenda/Agenda').then((m) => ({ default: m.Agenda })))
// Telas II operacionais (P-II2-TELA/P-II5-TELA): rotas aditivas; sem menu
// porque nenhuma permissão destes núcleos está provada no servidor.
const ExceptionQueuePage = lazy(() => import('@pages/metering/ExceptionQueuePage').then((m) => ({ default: m.ExceptionQueuePage })))
const RenewalPlanPage = lazy(() => import('@pages/metering/RenewalPlanPage').then((m) => ({ default: m.RenewalPlanPage })))

function PrivateRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated, loading } = useAuth()
  const location = useLocation()

  if (loading) return <Spinner label="Carregando..." />
  if (!isAuthenticated) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return <>{children}</>
}

/**
 * Correção S1 (revisão geral de 08/11/2026): a tela de revisão de pacote da
 * GIS-04 recebe o id do pacote pela rota declarada no GIS-CADASTRO-TECNICO
 * §10.3 (/gis/change-sets); o detalhe é /gis/change-sets/:id. Sem id não há
 * pacote para revisar: volta para o mapa.
 */
function ChangeSetReviewRoute() {
  const { id } = useParams()
  if (!id) return <Navigate to="/gis" replace />
  return <ChangeSetReview changeSetId={id} />
}

/**
 * Só existem rotas de telas que existem. Dashboard, Faturamento, Cobrança etc. foram removidas
 * porque as páginas nunca foram escritas. S7 (hidrômetros) e S8 (recadastramento) ficam para a P1.
 */
export function App() {
  const location = useLocation()
  const { theme } = useTheme()
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
        {/* Correção S1: mapa web e estilos da GIS-06 — rotas declaradas no
            README-ROTAS da P3.25 (/gis e /gis/styles); o tema vem do useTheme,
            como o README-ROTAS pede, e não do padrão light do componente. */}
        <Route path="gis" element={<GisMap theme={theme} />} />
        <Route path="gis/styles" element={<GisStyles />} />
        {/* Correção S1: revisão de pacote da GIS-04 — rota declarada no
            GIS-CADASTRO-TECNICO §10.3 (/gis/change-sets), detalhe por id. */}
        <Route path="gis/change-sets/:id" element={<ChangeSetReviewRoute />} />
        {/* Frente 5 (decisao 65): editor GIS-09/ED-53 e tematicos GIS-08.
            Nenhuma fonte declarava os caminhos destas duas telas (o
            README-ROTAS so cobre /gis e /gis/styles; os servicos declaram
            bases de API, nao rotas): /gis/editor e /gis/tematicos sao
            decisao de executor pela convencao das rotas GIS irmas
            (/gis, /gis/styles, /gis/catalogo, /gis/change-sets/:id),
            registada no RELATORIO-GIS-ROTAS do pacote. */}
        <Route path="gis/editor" element={<GisWebEditor />} />
        <Route path="gis/tematicos" element={<GisThematics />} />
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
        {/* P2.26: credenciais da API pública e webhooks de saída do tenant. */}
        <Route path="integracao" element={<Integration />} />
        <Route path="support" element={<Support />} />
        {/* P2.30: Configurações > Modelos — o motor único de modelos de documento e mensagem. */}
        <Route path="configuracoes/modelos" element={<Templates />} />
        {/* P1.33: visões salvas de filtros e colunas por usuário. */}
        <Route path="busca/visoes-salvas" element={<SavedViews />} />
        {/* P2.33: fila de documentos fiscais do tenant (rota declarada no
            cabeçalho da página: /documentos-fiscais). */}
        <Route path="documentos-fiscais" element={<DocumentosFiscais />} />
        {/* P2.28: caixa de trabalho do usuário logado (rota declarada no
            cabeçalho da página: /minhas-tarefas). */}
        <Route path="minhas-tarefas" element={<MinhasTarefas />} />
        {/* P3.39: agenda do usuário (rota declarada no cabeçalho da página:
            /agenda). A página Início (/inicio, P3.39) NÃO entra neste lote:
            ela exige a prop profile e nenhuma fonte de perfil de fábrica
            existe no frontend da main (AuthUser de GET /me não tem o campo) —
            registrar com valor inventado violaria a regra de não inventar
            dados; fica para a integração resolver a fonte (pendência 3 da
            P3.39). */}
        <Route path="agenda" element={<Agenda />} />
        <Route path="operacoes/excecoes-leitura" element={<ExceptionQueuePage />} />
        <Route path="operacoes/plano-troca-hidrometros" element={<RenewalPlanPage planYear={new Date().getFullYear()} />} />
        {/* Onda II: P-II3-TELA e P-II4-TELA — rotas aditivas; sem entrada
            de menu porque nenhuma permissão destas telas está provada no
            servidor (sem prova, sem menu — pendência no relatório). */}
        <Route path="comercial/tarifa-social" element={<SocialTariffCycle />} />
        <Route path="os/sla" element={<SlaOrders />} />

        <Route path="*" element={<NotFound />} />
      </Route>
      </Routes>
      </Suspense>
      </ErrorBoundary>
    </>
  )
}

export default App

