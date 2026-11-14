package br.com.ziipsan.commercial.fiscal.relief;

import java.util.Objects;

/**
 * Residuo NFAg dos parametros de alteracao de vencimento (PA-01 V-47 =
 * PARCIAL; insumo (f) do coordenador — o PA-02 ENCOLHE aqui).
 *
 * <p>O que a P2.20 ja cobre (verificado por evidencia no adendo PA-01):
 * {@code DueDateChangeRule} — novo vencimento obrigatoriamente posterior a
 * hoje, teto de alteracoes/ano por conta
 * ({@code bill.due_change_max_per_year}) e antecedencia minima
 * ({@code bill.due_change_min_days_before_due}). NADA disso se reconstrói.
 *
 * <p>Residuo confirmado em falta (o conjunto especifico da NFAg do art. 921
 * nao existe como parametrizacao propria): conferencia de codigos de servico
 * na alteracao, aviso impresso na alteracao e comportamento do debito
 * automatico. So esse residuo entra, como parametros declarados do tenant —
 * sem valores por omissao inventados: os tres sao obrigatorios no cadastro.
 */
public record DueDateChangeNfagePolicy(boolean serviceCodeConferenceRequired,
                                       boolean printedNoticeOnChange,
                                       DirectDebitBehavior directDebitBehavior) {

    /** Comportamento do debito automatico apos alteracao de vencimento. */
    public enum DirectDebitBehavior { KEEP, CANCEL, REQUIRE_RECONFIRMATION }

    public DueDateChangeNfagePolicy {
        Objects.requireNonNull(directDebitBehavior,
                "comportamento do debito automatico e parametro obrigatorio do tenant");
    }
}
