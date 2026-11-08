package br.com.ziipsan.commercial.billingrules;

/**
 * Versao do CONTRATO das regras de campo (ED-19). Nao e versao de release do
 * produto (essa vem da tag, P0.39): e o identificador do contrato aritmetico
 * que o pacote da rota carrega e que servidor e aparelho comparam antes de
 * qualquer calculo — versao de contrato divergente nem calcula: e divergencia
 * de base (ver {@link DivergenceEvaluator}).
 */
public final class RulesVersion {

    /** Identificador do contrato aritmetico das regras de campo. */
    public static final String CONTRACT = "billing-rules/1";

    private RulesVersion() {
    }
}
