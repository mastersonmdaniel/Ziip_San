import type { HTMLAttributes } from 'react'
import { cn } from '@utils/cn'

export type BadgeVariant = 'default' | 'secondary' | 'destructive' | 'outline' | 'success' | 'warning' | 'info'

const variants: Record<BadgeVariant, string> = {
  default: 'border-transparent bg-primary text-primary-foreground',
  secondary: 'border-transparent bg-secondary text-secondary-foreground',
  destructive: 'border-transparent bg-destructive text-destructive-foreground',
  outline: 'text-foreground',
  success: 'border-transparent bg-green-100 text-green-900 dark:bg-green-900 dark:text-green-100',
  warning: 'border-transparent bg-amber-100 text-amber-900 dark:bg-amber-900 dark:text-amber-100',
  info: 'border-transparent bg-blue-100 text-blue-900 dark:bg-blue-900 dark:text-blue-100',
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
