package br.com.ziipsan.commercial.registry.quality;

import java.util.UUID;

/**
 * Achado de qualidade cadastral (P3.32): a saida de uma regra sobre um
 * cliente ou uma unidade, na data de referencia da varredura.
 *
 * <p>O achado e um SINAL para a fila de correcao — ele nunca carrega uma
 * correcao nem e aplicado ao cadastro por quem o produz. A correcao so
 * ocorre como efeito de OS de lote (ADR-0039), ver
 * {@link CorrectionQueueService}.
 *
 * <p>{@code detail} e a evidencia legivel do achado (valores comparados,
 * origem do sinal), sem dado pessoal em claro: documento aparece so pelo
 * indice cego abreviado quando a regra e de duplicidade, nunca o numero.
 *
 * @param ruleCode  regra do catalogo {@link QualityRuleCode}
 * @param severity  severidade efetiva (parametro do tenant ou padrao da regra)
 * @param unitId    unidade do achado, quando a regra e por unidade
 * @param customerId cliente do achado, quando a regra e por cliente
 * @param detail    evidencia do achado (obrigatoria)
 */
public record QualityFinding(
        QualityRuleCode ruleCode,
        QualitySeverity severity,
        UUID unitId,
        UUID customerId,
        String detail) {

    public QualityFinding {
        if (ruleCode == null) {
            throw new IllegalArgumentException("regra do achado e obrigatoria");
        }
        if (severity == null) {
            throw new IllegalArgumentException("severidade do achado e obrigatoria (" + ruleCode + ")");
        }
        if (detail == null || detail.isBlank()) {
            throw new IllegalArgumentException("achado sem evidencia (detail) nao entra na fila: " + ruleCode);
        }
        if (unitId == null && customerId == null) {
            throw new IllegalArgumentException("achado sem unidade nem cliente nao tem alvo de correcao: " + ruleCode);
        }
        if (detail.length() > 500) {
            throw new IllegalArgumentException("detail do achado excede 500 caracteres (" + ruleCode + ")");
        }
    }

    /**
     * Chave de deduplicacao da fila: a mesma regra, sobre o mesmo alvo, e o
     * mesmo achado entre varreduras (a fila nao duplica item aberto).
     */
    public String key() {
        return ruleCode.name() + "|"
                + (unitId == null ? "-" : unitId) + "|"
                + (customerId == null ? "-" : customerId);
    }
}
