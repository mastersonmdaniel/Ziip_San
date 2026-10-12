import { clsx, type ClassValue } from 'clsx'

/** Junta classes condicionais. (tailwind-merge não é usado: as variantes não se sobrepõem.) */
export function cn(...inputs: ClassValue[]): string {
  return clsx(inputs)
}
