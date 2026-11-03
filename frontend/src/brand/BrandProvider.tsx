import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { resolveApiBaseUrl } from '@services/runtimeConfig'
import { BRAND } from './brand.generated'

/**
 * P0.34: marca do produto na interface. Fonte da verdade e o pacote
 * branding/; o modulo gerado (brand.generated.ts) e o fallback de build e
 * o que o servidor devolve em GET /api/v1/public/brand vence quando chega.
 * O fetch usa fetch() puro (nao o cliente api): a rota e publica e um 401
 * aqui nao pode redirecionar para o login — a marca aparece justamente na
 * tela de entrada. Sem provider, useBrand() devolve o fallback: telas
 * renderizadas fora do provider (testes) continuam funcionando.
 */
export type Brand = typeof BRAND

const BrandContext = createContext<Brand>(BRAND)

async function fetchBrand(): Promise<Partial<Brand> | null> {
  try {
    const response = await fetch(`${resolveApiBaseUrl()}/public/brand`, { credentials: 'include' })
    if (!response.ok) return null
    const data = (await response.json()) as Record<string, unknown>
    const pick = (key: string): string | undefined => (typeof data[key] === 'string' ? (data[key] as string) : undefined)
    const productName = pick('productName')
    if (!productName) return null
    return {
      productName,
      shortName: pick('shortName') ?? productName,
      description: pick('description') ?? BRAND.description,
      brandPrimary: pick('brandPrimary') ?? BRAND.brandPrimary,
      themeColor: {
        light: pick('themeColorLight') ?? BRAND.themeColor.light,
        dark: pick('themeColorDark') ?? BRAND.themeColor.dark,
      },
      footer: pick('footer') ?? BRAND.footer,
      emailSignature: pick('emailSignature') ?? BRAND.emailSignature,
      aboutText: pick('aboutText') ?? BRAND.aboutText,
    } as Partial<Brand>
  } catch {
    return null
  }
}

export function BrandProvider({ children }: { children: ReactNode }) {
  const [brand, setBrand] = useState<Brand>(BRAND)
  useEffect(() => {
    let active = true
    void fetchBrand().then((remote) => {
      if (active && remote) setBrand({ ...BRAND, ...remote })
    })
    return () => {
      active = false
    }
  }, [])
  return <BrandContext.Provider value={brand}>{children}</BrandContext.Provider>
}

export function useBrand(): Brand {
  return useContext(BrandContext)
}
