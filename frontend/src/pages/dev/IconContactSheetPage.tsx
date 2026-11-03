import { IconContactSheet } from '@components/ui/IconContactSheet'

/**
 * P1.29 — pagina da folha de contato (so em desenvolvimento, rota
 * /dev/icones em App.tsx). Serve ao aceite do dono (§6): abrir a pagina,
 * trocar modo (claro/escuro) e paleta (Azul Agua/Verde Agua) na
 * interface e conferir cada icone em 16, 20 e 24 px. Nao e item de menu
 * nem rota de produto: nao entra na navegacao.
 */
export function IconContactSheetPage() {
  return (
    <main className="mx-auto max-w-6xl px-4 py-6">
      <h1 className="mb-2 text-2xl font-bold text-foreground">Folha de contato de icones (P1.29)</h1>
      <p className="mb-6 text-sm text-muted-foreground">
        Conjunto inicial das secoes 4.1 e 4.2 do documento de icones e simbologia. A folha segue o tema vigente: troque o
        modo e a paleta para conferir claro/escuro, Azul Agua e Verde Agua.
      </p>
      <IconContactSheet />
    </main>
  )
}
