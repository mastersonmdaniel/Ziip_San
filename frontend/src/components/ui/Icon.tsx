import {
  AlertCircle,
  AlertTriangle,
  Building2,
  Check,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Eye,
  EyeOff,
  Info,
  ListChecks,
  Loader2,
  LogIn,
  LogOut,
  Map,
  MapPin,
  Menu,
  Moon,
  Pencil,
  Plus,
  Search,
  Sun,
  Trash2,
  User,
  Users,
  X,
  type LucideIcon,
} from 'lucide-react'
import { ICON_LABELS } from '../../i18n/iconLabels'

/**
 * P0.46 (ICONES-E-SIMBOLOGIA §2 e §4.1) — o UNICO lugar que importa do
 * lucide-react. Nome em ingles, minusculas com hifen; cor so por
 * currentColor (o Lucide ja herda a cor do texto) e tamanho por className,
 * nunca por cor literal. Nome desconhecido e erro de compilacao.
 * Sao 26 icones, nao 25: o documento §4.1 diz 25, mas o codigo usa 26
 * (a lista abaixo e o inventario real, conferido por icon.test.tsx).
 * Guia de estilo §2.1: grade 24x24, traco 2px, pontas arredondadas.
 */
export const ICONS = {
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
  const Component = ICONS[name]
  const ariaLabel = label === true ? ICON_LABELS[name] : typeof label === 'string' ? label : undefined
  return <Component className={className} size={size} aria-hidden={ariaLabel ? undefined : true} aria-label={ariaLabel} />
}
