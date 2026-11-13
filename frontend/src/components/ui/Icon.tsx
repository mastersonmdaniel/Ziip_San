import {
  AlertCircle,
  AlertTriangle,
  ArrowDownToLine,
  Banknote,
  BarChart3,
  Bell,
  Building2,
  CalendarCheck,
  Check,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  ClipboardList,
  CreditCard,
  Eye,
  EyeOff,
  FileCheck,
  FileText,
  Filter,
  FlaskConical,
  Gauge,
  Globe,
  HardHat,
  HelpCircle,
  Home,
  Info,
  Languages,
  LayoutDashboard,
  Layers,
  LifeBuoy,
  ListChecks,
  Loader2,
  Lock,
  LogIn,
  LogOut,
  Clock,
  Map,
  MapPin,
  Menu,
  Monitor,
  Moon,
  Package,
  Palette,
  Paperclip,
  Pencil,
  Plus,
  Printer,
  Receipt,
  Recycle,
  Repeat,
  RotateCcw,
  Save,
  ScrollText,
  Search,
  Settings,
  Sun,
  TestTube,
  Trash2,
  Truck,
  Upload,
  Download,
  User,
  Users,
  Boxes,
  FileSignature,
  Warehouse,
  Wrench,
  X,
  XCircle,
  type LucideIcon,
} from 'lucide-react'
import { CUSTOM_ICONS } from './customIcons'
import { ICON_LABELS } from '../../i18n/iconLabels'

/**
 * P0.46 + P1.29 (ICONES-E-SIMBOLOGIA §2, §4.1 e §4.2) — o UNICO lugar que
 * importa do lucide-react (mais os proprios de customIcons.ts, construidos
 * pela fabrica do Lucide com os mesmos atributos padrao). Nome em ingles,
 * minusculas com hifen; icone de entidade de saneamento com prefixo
 * `sanitation-` (§2.1); icone do nucleo sem prefixo, por compatibilidade
 * com o registro da P0.46 (o documento cita `core-search` como exemplo de
 * prefixo, mas o registro existente ja usava nomes sem prefixo e renomear
 * os 26 quebraria todo uso — o prefixo de pacote fica no `sanitation-`).
 * Cor so por currentColor e tamanho por className/size, nunca por cor
 * literal. Nome desconhecido e erro de compilacao.
 *
 * Restauracao R41-4-29/30 (Rodada 41): este ficheiro e o registo de
 * `941e3437` (P1.29), que o stub de `c0d1c6ac` substituiu, com mais 2
 * icones do menu GIS (Layout.tsx:34-37): `palette` (lucide Palette) e
 * `layers` (lucide Layers) — 82 icones no total. O contrato R2-F9
 * (Rodada 8, posterior a `941e3437`) e preservado: nome fora do
 * registo NAO parte a tela — em desenvolvimento avisa e renderiza o
 * glifo de recurso marcado `data-icon-unknown="true"` (a vitrine
 * Showcase.test.tsx prende este comportamento); todo icone conhecido
 * sai marcado `data-icon={name}`.
 *
 * 82 icones: 52 do nucleo (§4.1: navegacao, acao, estado, sistema) e 28
 * de saneamento (§4.2), mais `palette` e `layers` (menu GIS, genericos
 * o Lucide, com reuso declarado quando a metafora e a mesma (ex.:
 * `gauge` serve a navegacao de medicao e a `sanitation-reading`;
 * `life-buoy` serve a navegacao de chamados e a `sanitation-support-ticket`).
 * Nota fiscal cobre PDF e XML com um unico icone (`sanitation-invoice`):
 * o formato e variante de arquivo, nao icone distinto (§2.1: uma metafora
 * por icone). Guia de estilo §2.1: grade 24x24, traco 2px, pontas
 * arredondadas. Cobertura por categoria em iconSets.ts, conferida em CI
 * por iconSets.test.tsx; folha de contato em IconContactSheet.tsx.
 */
export const ICONS = {
  // Nucleo existente (P0.46)
  'alert-circle': AlertCircle,
  'alert-triangle': AlertTriangle,
  'building-2': Building2,
  check: Check,
  'check-circle-2': CheckCircle2,
  'chevron-left': ChevronLeft,
  'chevron-right': ChevronRight,
  eye: Eye,
  'eye-off': EyeOff,
  info: Info,
  'list-checks': ListChecks,
  'loader-2': Loader2,
  'log-in': LogIn,
  'log-out': LogOut,
  map: Map,
  'map-pin': MapPin,
  menu: Menu,
  moon: Moon,
  pencil: Pencil,
  plus: Plus,
  search: Search,
  sun: Sun,
  'trash-2': Trash2,
  user: User,
  users: Users,
  x: X,
  // §4.1 navegacao (novos)
  home: Home,
  'clipboard-list': ClipboardList,
  receipt: Receipt,
  gauge: Gauge,
  banknote: Banknote,
  wrench: Wrench,
  warehouse: Warehouse,
  'bar-chart-3': BarChart3,
  'life-buoy': LifeBuoy,
  'help-circle': HelpCircle,
  settings: Settings,
  'layout-dashboard': LayoutDashboard,
  // R41-4-29/30: icones do menu GIS (Layout.tsx:34-37) — genericos do
  // nucleo (GENERIC_CORE_ICONS em iconSets.ts), fora do inventario §4.1.
  palette: Palette,
  layers: Layers,
  // §4.1 acoes (novos)
  save: Save,
  filter: Filter,
  upload: Upload,
  download: Download,
  'arrow-down-to-line': ArrowDownToLine,
  printer: Printer,
  paperclip: Paperclip,
  'x-circle': XCircle,
  'rotate-ccw': RotateCcw,
  // §4.1 estados (novos)
  lock: Lock,
  clock: Clock,
  // §4.1 sistema (novos)
  monitor: Monitor,
  languages: Languages,
  bell: Bell,
  // §4.2 entidades de saneamento via Lucide
  'sanitation-customer': Users,
  'sanitation-property': Building2,
  'sanitation-reading': Gauge,
  'sanitation-bill': Receipt,
  'sanitation-account': FileText,
  'sanitation-payment': CreditCard,
  'sanitation-direct-debit': Repeat,
  'sanitation-invoice': FileCheck,
  'sanitation-material': Package,
  'sanitation-material-request': ScrollText,
  'sanitation-reservation': CalendarCheck,
  'sanitation-inventory': Boxes,
  'sanitation-vehicle': Truck,
  'sanitation-field-team': HardHat,
  'sanitation-water-quality': FlaskConical,
  'sanitation-sample': TestTube,
  'sanitation-waste': Recycle,
  'sanitation-support-ticket': LifeBuoy,
  'sanitation-contract': FileSignature,
  'sanitation-consumer-portal': Globe,
  // §4.2 entidades de saneamento proprias
  ...CUSTOM_ICONS,
} satisfies Record<string, LucideIcon>

export type IconName = keyof typeof ICONS

interface IconProps {
  name: IconName
  className?: string
  size?: number | string
  /** true = rotulo do i18n; string = rotulo proprio; omitido = decorativo. */
  label?: boolean | string
}

export function Icon({ name, className, size, label }: IconProps) {
  const Component = (ICONS as Record<string, LucideIcon | undefined>)[name]
  const ariaLabel = label === true ? ICON_LABELS[name] : typeof label === 'string' ? label : undefined
  if (!Component) {
    // R2-F9 (preservado na restauracao R41-4-29/30): nome fora do
    // registo nao parte a tela — em desenvolvimento avisa, em qualquer
    // ambiente renderiza o glifo de recurso marcado data-icon-unknown.
    const isProduction = typeof process !== 'undefined' && process.env?.NODE_ENV === 'production'
    if (!isProduction && typeof console !== 'undefined') {
      console.warn(
        `[tema-front-web] Icon: nome desconhecido "${name}". Nomes conhecidos: ${Object.keys(ICONS).join(', ')}.`,
      )
    }
    return (
      <svg
        aria-hidden="true"
        data-icon={name}
        data-icon-unknown="true"
        className={['ui-icon', className ?? ''].filter(Boolean).join(' ')}
        width={size ?? 16}
        height={size ?? 16}
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      >
        <circle cx="12" cy="12" r="4" />
      </svg>
    )
  }
  return <Component data-icon={name} className={className} size={size} aria-hidden={ariaLabel ? undefined : true} aria-label={ariaLabel} />
}
