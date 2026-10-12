import { useEffect, useRef, useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { Building2, ListChecks, LogOut, Map, Menu, Moon, Sun, User, Users, X } from 'lucide-react'
import { Button } from '@components/ui/Button'
import { useAuth } from '@hooks/useAuth'
import { useTheme } from '@hooks/useTheme'
import { cn } from '@utils/cn'

/** O menu só tem o que existe. Novas telas entram aqui quando forem implementadas. */
const navigation = [
  { name: 'Imóveis', href: '/cadastro/imoveis', icon: Building2 },
  { name: 'Clientes', href: '/cadastro/clientes', icon: Users },
  { name: 'Hierarquia territorial', href: '/cadastro/territorio', icon: Map },
  { name: 'Catálogos', href: '/cadastro/catalogos', icon: ListChecks },
]

export function Layout() {
  const { user, logout } = useAuth()
  const { theme, toggleTheme } = useTheme()
  const location = useLocation()
  const [menuOpen, setMenuOpen] = useState(false)
  const mainRef = useRef<HTMLElement>(null)
  const firstRender = useRef(true)

  // Ao trocar de tela, fecha o menu e leva o foco ao conteúdo (leitor de tela anuncia a mudança).
  useEffect(() => {
    setMenuOpen(false)
    if (firstRender.current) {
      firstRender.current = false
      return
    }
    mainRef.current?.focus()
  }, [location.pathname])

  useEffect(() => {
    if (!menuOpen) return undefined
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setMenuOpen(false)
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [menuOpen])

  return (
    <div className="min-h-screen bg-muted/40">
      <a
        href="#conteudo"
        className="sr-only focus:not-sr-only focus:fixed focus:left-2 focus:top-2 focus:z-50 focus:rounded-md focus:bg-primary focus:px-4 focus:py-2 focus:text-primary-foreground"
      >
        Pular para o conteúdo
      </a>

      <aside
        id="menu-principal"
        className={cn(
          'fixed inset-y-0 left-0 z-30 w-64 flex-col border-r bg-card',
          menuOpen ? 'flex' : 'hidden lg:flex',
        )}
      >
        <div className="flex h-16 items-center justify-between border-b px-4">
          <span className="text-xl font-bold text-primary">SIGSAN</span>
          <Button variant="ghost" size="icon" className="lg:hidden" onClick={() => setMenuOpen(false)} aria-label="Fechar menu">
            <X className="h-5 w-5" aria-hidden="true" />
          </Button>
        </div>

        <nav aria-label="Principal" className="flex-1 overflow-y-auto p-4">
          <p className="mb-2 px-3 text-xs font-semibold uppercase tracking-wide text-muted-foreground">Cadastro</p>
          <ul className="space-y-1">
            {navigation.map((item) => (
              <li key={item.href}>
                <NavLink
                  to={item.href}
                  className={({ isActive }) =>
                    cn(
                      'flex items-center rounded-lg px-3 py-2.5 text-sm font-medium transition-colors',
                      isActive ? 'bg-primary text-primary-foreground' : 'text-foreground hover:bg-accent',
                    )
                  }
                >
                  <item.icon className="mr-3 h-5 w-5" aria-hidden="true" />
                  {item.name}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>

        <div className="border-t p-4">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-full bg-primary/10" aria-hidden="true">
              <User className="h-5 w-5 text-primary" />
            </div>
            <div className="min-w-0">
              <p className="truncate text-sm font-medium">{user?.fullName || user?.username}</p>
              <p className="truncate text-xs text-muted-foreground">{user?.email}</p>
            </div>
          </div>
          <Button variant="outline" size="sm" className="mt-3 w-full" onClick={() => void logout()}>
            <LogOut className="h-4 w-4" aria-hidden="true" />
            Sair
          </Button>
        </div>
      </aside>

      {menuOpen ? <div className="fixed inset-0 z-20 bg-black/50 lg:hidden" aria-hidden="true" onClick={() => setMenuOpen(false)} /> : null}

      <div className="lg:pl-64">
        <header className="sticky top-0 z-20 flex h-16 items-center justify-between border-b bg-card px-4 sm:px-6">
          <Button
            variant="ghost"
            size="icon"
            className="lg:hidden"
            onClick={() => setMenuOpen((o) => !o)}
            aria-expanded={menuOpen}
            aria-controls="menu-principal"
            aria-label="Abrir menu"
          >
            <Menu className="h-6 w-6" aria-hidden="true" />
          </Button>
          <span className="hidden text-sm text-muted-foreground lg:inline">Sistema Integrado de Gestão de Saneamento</span>
          <Button variant="ghost" size="icon" onClick={toggleTheme} aria-label={theme === 'dark' ? 'Usar tema claro' : 'Usar tema escuro'}>
            {theme === 'dark' ? <Sun className="h-5 w-5" aria-hidden="true" /> : <Moon className="h-5 w-5" aria-hidden="true" />}
          </Button>
        </header>

        <main id="conteudo" ref={mainRef} tabIndex={-1} className="p-4 focus:outline-none sm:p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
