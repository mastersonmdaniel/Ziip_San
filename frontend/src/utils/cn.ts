import { clsx, type ClassValue } from 'clsx'

/**
 * Junta classes condicionais. Copia fiel do espelho da ED-08
 * (ed-08/espelho/frontend/src/utils/cn.ts): tailwind-merge nao e usado
 * la — "as variantes nao se sobrepoem".
 */
export function cn(...inputs: ClassValue[]): string {
  return clsx(inputs)
}
