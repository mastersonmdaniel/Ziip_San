package br.com.ziipsan.commercial.billing.content;

import java.time.LocalDate;

/**
 * Uma mensagem do catalogo de mensagens da fatura (P2.32): chave estavel,
 * chave da NORMA que a fundamenta (o agrupamento "por norma" do aceite),
 * classe do parametro que a governa (classes a a d do ADR-0015, em
 * PARAMETROS-DO-TENANT: a = travado, b = padrao com aceite, c = so
 * ampliavel/piso, d = livre), condicao de disparo, vigencia e ordem dentro
 * do seu grupo. Mensagem sem parametro de tenant que a governe (dever legal
 * incondicional) e classe A. [Correcao da revisao cruzada: a primeira versao
 * truncava o enum em {A, B} e classificava errado o aviso de perda da tarifa
 * social (parametro classe c) e o de vencimento de beneficio (classe d).]
 *
 * <p>{@code legal = true}: mensagem legal — impressa SEMPRE antes das
 * demais, travado no motor ({@link LegalMessageEngine}), sem parametro que
 * desligue nem reordene. {@code slotOnly = true}: e um ESPACO reservado —
 * a tarefa dona (ED-88, ED-25 ou P3.5) e quem o ativa e define o conteudo;
 * aqui o slot existe, inativo, e nao emite nada.
 *
 * <p>O TEXTO da mensagem nao vive aqui: ele e do modelo aprovado do tenant
 * (motor da P2.30) — esta classe guarda a chave e o fundamento, nunca um
 * texto juridico inventado.
 */
public record LegalMessageDef(
        String messageKey,
        String normKey,
        ParameterClass parameterClass,
        Trigger trigger,
        int order,
        boolean legal,
        boolean slotOnly,
        String activatingTask,
        LocalDate validFrom,
        LocalDate validTo) {

    /** Classe do parametro em PARAMETROS-DO-TENANT (ADR-0015): A travado, B padrao com aceite, C so ampliavel, D livre. */
    public enum ParameterClass { A, B, C, D }

    /** Condicao de disparo declarada; o motor a avalia contra o contexto da conta. */
    public enum Trigger {
        SOCIAL_TARIFF_LOSS_SCHEDULED,
        ANNUAL_DISCHARGE_DUE,
        WATER_QUALITY_AVAILABLE,
        BENEFIT_EXPIRY_WINDOW,
        SLOT_ACTIVATION
    }

    public LegalMessageDef {
        if (messageKey == null || messageKey.isBlank()) {
            throw new IllegalArgumentException("messageKey e obrigatoria");
        }
        if (normKey == null || normKey.isBlank()) {
            throw new IllegalArgumentException("normKey e obrigatoria para " + messageKey
                    + ": mensagem sem norma declarada nao entra no catalogo");
        }
        if (trigger == null || parameterClass == null) {
            throw new IllegalArgumentException("trigger e parameterClass sao obrigatorios para " + messageKey);
        }
        if (slotOnly && (activatingTask == null || activatingTask.isBlank())) {
            throw new IllegalArgumentException("Slot " + messageKey + " precisa declarar a tarefa que o ativa");
        }
        if (validFrom != null && validTo != null && validTo.isBefore(validFrom)) {
            throw new IllegalArgumentException("validTo anterior a validFrom em " + messageKey);
        }
    }

    public boolean covers(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("date e obrigatoria");
        }
        if (validFrom != null && date.isBefore(validFrom)) {
            return false;
        }
        return validTo == null || !date.isAfter(validTo);
    }
}
