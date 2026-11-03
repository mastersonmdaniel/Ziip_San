import { describe, expect, it } from 'vitest'
import { hasPermission } from './usePermissions'
import type { AuthUser } from '@services/api'

const base: AuthUser = { id: 'u', username: 't', email: 't@x.invalid', fullName: 'T', roles: ['VIEWER'], tenantId: 'ten' }

describe('hasPermission (P1.12)', () => {
  it('sem usuário ou sem campo permissions, não esconde nada (o servidor nega)', () => {
    expect(hasPermission(null, 'registry:export')).toBe(true)
    expect(hasPermission(base, 'registry:export')).toBe(true)
  })

  it('com permissions presente, só mostra para quem tem a permissão exata', () => {
    expect(hasPermission({ ...base, permissions: ['registry:read'] }, 'registry:export')).toBe(false)
    expect(hasPermission({ ...base, permissions: ['registry:read', 'registry:export'] }, 'registry:export')).toBe(true)
    expect(hasPermission({ ...base, permissions: [] }, 'registry:read')).toBe(false)
  })
})
