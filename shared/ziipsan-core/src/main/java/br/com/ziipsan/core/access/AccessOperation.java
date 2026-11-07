package br.com.ziipsan.core.access;

/**
 * Operação pedida dentro de uma sessão de acesso (P3.17; ADR-0020).
 *
 * <p>{@code REVEAL_DOCUMENT} (revelar documento, ex.: ver CPF/CNPJ em claro) e
 * {@code EXPORT} (exportar dados do tenant) são operações distintas de
 * {@code READ} de propósito: o aceite da P3.17 as bloqueia no acesso assistido
 * mesmo com consentimento, e no acesso de emergência elas caem na mesma
 * vedação — exportar é escrita de uma cópia para fora do tenant e revelar
 * documento é exatamente o que o consentimento de suporte não cobre. Quem
 * chama classifica a operação; classificar exportação ou revelação como
 * {@code READ} para contornar o bloqueio é fraude de chamador, não um caminho
 * que esta enumeração abra.
 *
 * <p><b>Enum novo nasce negado</b>: a {@link AccessPolicy} só classifica
 * {@code READ} e {@code WRITE} como operações ordinárias; acrescentar um
 * valor a este enum SEM classificá-lo na política o deixa negado por padrão
 * ({@code operation-not-classified}), nos dois modos — nunca permitido por
 * omissão.
 */
public enum AccessOperation {
    READ,
    WRITE,
    REVEAL_DOCUMENT,
    EXPORT
}
