package br.com.ziipsan.core.quality;

import java.util.UUID;

/**
 * Ponto de coleta/amostragem do tenant. Estrutura de cadastro — nenhum
 * ponto, frequencia ou parametro nasce de fabrica; o cadastro e do
 * prestador (plano de amostragem), ligado ao sistema de abastecimento
 * pela chave textual do sistema (a mesma convencao do bloco da P2.24).
 *
 * @param consumerUnitId unidade consumidora quando o ponto e um ponto
 *        de consumo (reclamacao OPE-26); nulo nos demais tipos.
 */
public record SamplingPoint(
        UUID id,
        String code,
        String supplySystem,
        PointKind kind,
        UUID consumerUnitId,
        boolean active) {

    /** Tipos estruturais de ponto — nao sao valores regulatorios. */
    public enum PointKind {
        TREATMENT_OUTLET,
        DISTRIBUTION_NETWORK,
        RESERVOIR,
        CONSUMER_POINT,
        RAW_WATER_INLET
    }

    public SamplingPoint {
        if (id == null) throw new IllegalArgumentException("id do ponto e obrigatorio");
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code do ponto e obrigatorio");
        if (supplySystem == null || supplySystem.isBlank())
            throw new IllegalArgumentException("supplySystem e obrigatorio para o ponto " + code);
        if (kind == null) throw new IllegalArgumentException("kind e obrigatorio para o ponto " + code);
        if (kind == PointKind.CONSUMER_POINT && consumerUnitId == null)
            throw new IllegalArgumentException("ponto de consumo exige consumerUnitId (ponto " + code + ")");
        if (kind != PointKind.CONSUMER_POINT && consumerUnitId != null)
            throw new IllegalArgumentException("consumerUnitId so existe em ponto de consumo (ponto " + code + ")");
    }
}
