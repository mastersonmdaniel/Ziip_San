import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { axe } from 'vitest-axe'
import { Badge } from '@components/ui/Badge'
import { Alert } from '@components/ui/Alert'
import { BRAND } from '../brand/brand.generated'
import brandJson from '../../../branding/ziipsan/brand.json'

/**
 * P0.34/P0.36 — axe sobre componentes migrados (Badge/Alert com tokens
 * semanticos) e sobre a marca gerada. axe em jsdom NAO mede contraste
 * (MARCA-E-TEMAS §4.6): o contraste e provado por contrast.test.ts; aqui
 * valem as demais regras (nome acessivel, estrutura, foco). A varredura
 * Playwright + axe das telas em cada padrao e modo fica pendente (ver
 * relatorio: Playwright nao instalado nesta VM) e bloqueia o merge ate
 * existir no CI — este teste nao a substitui.
 */
describe('axe (Vitest) nos componentes de marca e tema', () => {
  it('Badge e Alert em todos os tons nao tem violacao axe', async () => {
    const { container } = render(
      <div>
        <Badge variant="success">Ativo</Badge>
        <Badge variant="warning">Pendente</Badge>
        <Badge variant="info">Informacao</Badge>
        <Alert tone="success" title="Sucesso">
          Operacao concluida
        </Alert>
        <Alert tone="warning" title="Atencao">
          Verifique os dados
        </Alert>
        <Alert tone="info" title="Informacao">
          Leia antes de continuar
        </Alert>
      </div>,
    )
    expect(await axe(container)).toHaveNoViolations()
  })

  it('marca gerada: igual ao pacote branding/ziipsan (sem literal no teste)', () => {
    expect(BRAND.productName).toBe(brandJson.productName)
    expect(BRAND.description).toBe(brandJson.description)
    expect(BRAND.brandPrimary).toBe(brandJson.brandPrimary)
    expect(BRAND.themeColor.light).toBe(brandJson.themeColor.light)
    expect(BRAND.themeColor.dark).toBe(brandJson.themeColor.dark)
    expect(screen).toBeDefined()
  })
})
