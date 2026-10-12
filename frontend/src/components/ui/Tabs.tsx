import { useRef, type KeyboardEvent, type ReactNode } from 'react'
import { cn } from '@utils/cn'

export interface TabItem {
  id: string
  label: string
}

interface TabsProps {
  tabs: TabItem[]
  active: string
  onChange: (id: string) => void
  /** Prefixo para ligar aba e painel por id (único na página). */
  idPrefix: string
  children: ReactNode
  label: string
}

/**
 * Abas acessíveis (padrão WAI-ARIA): role=tablist/tab/tabpanel, aba ativa com tabindex 0,
 * setas, Home e End movem o foco E ativam a aba.
 */
export function Tabs({ tabs, active, onChange, idPrefix, children, label }: TabsProps) {
  const refs = useRef<Record<string, HTMLButtonElement | null>>({})

  const move = (index: number) => {
    const target = tabs[(index + tabs.length) % tabs.length]
    if (!target) return
    onChange(target.id)
    refs.current[target.id]?.focus()
  }

  const onKeyDown = (event: KeyboardEvent<HTMLButtonElement>, index: number) => {
    if (event.key === 'ArrowRight') {
      event.preventDefault()
      move(index + 1)
    } else if (event.key === 'ArrowLeft') {
      event.preventDefault()
      move(index - 1)
    } else if (event.key === 'Home') {
      event.preventDefault()
      move(0)
    } else if (event.key === 'End') {
      event.preventDefault()
      move(tabs.length - 1)
    }
  }

  return (
    <div>
      <div role="tablist" aria-label={label} className="flex gap-1 overflow-x-auto border-b">
        {tabs.map((tab, index) => {
          const selected = tab.id === active
          return (
            <button
              key={tab.id}
              ref={(el) => {
                refs.current[tab.id] = el
              }}
              role="tab"
              type="button"
              id={`${idPrefix}-tab-${tab.id}`}
              aria-selected={selected}
              aria-controls={`${idPrefix}-panel-${tab.id}`}
              tabIndex={selected ? 0 : -1}
              onClick={() => onChange(tab.id)}
              onKeyDown={(e) => onKeyDown(e, index)}
              className={cn(
                '-mb-px whitespace-nowrap border-b-2 px-4 py-2.5 text-sm font-medium',
                'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                selected ? 'border-primary text-primary' : 'border-transparent text-muted-foreground hover:text-foreground',
              )}
            >
              {tab.label}
            </button>
          )
        })}
      </div>
      <div role="tabpanel" id={`${idPrefix}-panel-${active}`} aria-labelledby={`${idPrefix}-tab-${active}`} tabIndex={0} className="pt-6 focus-visible:outline-none">
        {children}
      </div>
    </div>
  )
}
