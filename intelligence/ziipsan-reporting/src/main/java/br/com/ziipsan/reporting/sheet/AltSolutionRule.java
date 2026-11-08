package br.com.ziipsan.reporting.sheet;

/**
 * Regra de computo da solucao alternativa na cobertura, POR AGENCIA
 * (RAS-33; perfis: ARIS RN 47/2025, AGIR RN 024/2025, ARESC Res. 431/2026).
 * O acceptance nomeia as resolucoes mas NAO enuncia o sentido da regra de
 * cada uma nesta VM - logo UNDECLARED e o estado inicial de todo perfil e o
 * calculo RECUSA sem a regra declarada na integracao a partir do texto da
 * resolucao. Nenhum sentido e presumido como fato de agencia.
 */
public enum AltSolutionRule {
    UNDECLARED,
    COUNTS_AS_COVERED,
    DOES_NOT_COUNT
}
