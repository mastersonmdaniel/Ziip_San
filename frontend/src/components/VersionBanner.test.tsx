import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { VersionBanner } from './VersionBanner'
import { VERSION_OUTDATED_EVENT } from '@services/versionInfo'

describe('VersionBanner (P0.39)', () => {
  it('não mostra nada até o evento de versão desatualizada', () => {
    render(<VersionBanner />)
    expect(screen.queryByRole('status')).toBeNull()
  })

  it('mostra o aviso e recarrega ao clicar', async () => {
    const onReload = vi.fn()
    const user = userEvent.setup()
    render(<VersionBanner onReload={onReload} />)
    act(() => {
      window.dispatchEvent(
        new CustomEvent(VERSION_OUTDATED_EVENT, {
          detail: { serverVersion: '1.3.0', minClientVersion: '1.3.0' },
        }),
      )
    })
    expect(screen.getByRole('status')).toHaveTextContent('Há uma versão nova do Ziip SAN')
    expect(screen.getByRole('status')).toHaveTextContent('1.3.0')
    await user.click(screen.getByRole('button', { name: 'Recarregar agora' }))
    expect(onReload).toHaveBeenCalledTimes(1)
  })
})
