package br.com.ziipsan.commercial.registry.domain;

/**
 * Cobertura da rede numa quadra, como a tela envia (frontend: {@code NetworkCoverage} em
 * {@code services/registry/types.ts} e o select de {@code territoryDefinitions.ts}).
 *
 * <p>Os três valores NÃO colapsam para booleano: {@code PARTIAL} é cobertura parcial, distinta de
 * {@code FULL}. Os booleanos legados {@code hasWaterNetwork}/{@code hasSewerNetwork} continuam
 * existindo (regras de situação de ligação os consultam) e são derivados do enum na escrita:
 * {@code NONE} → {@code false}, {@code PARTIAL}/{@code FULL} → {@code true}. O caminho inverso
 * (payload só com booleano, sem o enum) mapeia {@code true} → {@code FULL} e {@code false} →
 * {@code NONE}: é aproximação documentada, não semântica do produto — um booleano não distingue
 * parcial de total, e só o enum expressa {@code PARTIAL}. Linhas anteriores à V3_6 recebem o mesmo
 * backfill na migração.
 */
public enum NetworkCoverage {
    NONE,
    PARTIAL,
    FULL
}
