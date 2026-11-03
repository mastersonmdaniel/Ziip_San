import { describe, expect, it } from 'vitest'
import { compareSemver, isClientOutdated, resolveBuildInfo } from './versionInfo'

describe('compareSemver', () => {
  it('ordena maior/menor/igual e trata pré-release como mais antiga', () => {
    expect(compareSemver('1.2.3', '1.2.3')).toBe(0)
    expect(compareSemver('1.2.2', '1.2.3')).toBe(-1)
    expect(compareSemver('1.3.0', '1.2.9')).toBe(1)
    expect(compareSemver('1.2.3-SNAPSHOT', '1.2.3')).toBe(-1)
    expect(compareSemver('2.0', '2.0.0')).toBe(0)
  })
})

describe('isClientOutdated', () => {
  it('cliente abaixo do mínimo está desatualizado; igual ou acima, não', () => {
    expect(isClientOutdated('1.2.0', '1.3.0')).toBe(true)
    expect(isClientOutdated('1.3.0', '1.3.0')).toBe(false)
    expect(isClientOutdated('1.4.0', '1.3.0')).toBe(false)
  })

  it('build dev nunca avisa (sem versão real para comparar)', () => {
    expect(isClientOutdated('dev', '9.9.9')).toBe(false)
    expect(isClientOutdated('1.0.0', 'dev')).toBe(false)
  })
})

describe('resolveBuildInfo', () => {
  it('precedência: env > tag git > dev; commit/data do commit, não do relógio', () => {
    expect(
      resolveBuildInfo(
        { ZIIPSAN_VERSION: '2.0.0' },
        { tagVersion: '1.0.0', commit: 'abc1234', date: '2026-11-01T10:00:00-03:00' },
      ),
    ).toEqual({ version: '2.0.0', commit: 'abc1234', buildDate: '2026-11-01T10:00:00-03:00' })
    expect(resolveBuildInfo({}, { tagVersion: '1.0.0', commit: 'abc1234', date: '2026-11-01T10:00:00-03:00' })).toEqual({
      version: '1.0.0',
      commit: 'abc1234',
      buildDate: '2026-11-01T10:00:00-03:00',
    })
    expect(resolveBuildInfo({}, {})).toEqual({ version: '0.0.0-SNAPSHOT', commit: 'unknown', buildDate: 'unknown' })
  })
})
