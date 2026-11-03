import type { HTMLAttributes } from 'react'
import { cn } from '@utils/cn'

export type BadgeVariant = 'default' | 'secondary' | 'destructive' | 'outline' | 'success' | 'warning' | 'info'

const variants: Record<BadgeVariant, string> = {
  default: 'border-transparent bg-primary text-primary-foreground',
  secondary: 'border-transparent bg-secondary text-secondary-foreground',
  destructive: 'border-transparent bg-destructive text-destructive-foreground',
  outline: 'text-foreground',
  success: 'border-transparent bg-success text-success-foreground',
  warning: 'border-transparent bg-warning text-warning-foreground',
  info: 'border-transparent bg-info text-info-foreground',
}

/** O significado deve estar no TEXTO do selo, nunca só na cor. */
export function Badge({
  className,
  variant = 'default',
  ...props
}: HTMLAttributes<HTMLSpanElement> & { variant?: BadgeVariant }) {
  return (
    <span
      className={cn('inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold', variants[variant], className)}
      {...props}
    />
  )
}
