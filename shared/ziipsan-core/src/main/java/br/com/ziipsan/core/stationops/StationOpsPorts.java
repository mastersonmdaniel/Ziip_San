package br.com.ziipsan.core.stationops;

import java.util.UUID;

/**
 * Portas da ED-87 para os vizinhos — a ED-87 CONSUME por estas
 * interfaces, sem duplicar o que cada entrega ja faz:
 * <ul>
 *   <li>{@link ManualSeriesPort} — a serie temporal da ED-17
 *       ({@code supervisory_samples}): toda leitura do diario e
 *       gravada nela com origem {@code MANUAL}. A ED-17 ja recebe
 *       amostras do supervisório; a leitura manual e a mesma serie
 *       com a origem trocada — nenhum segundo repositorio de serie
 *       nasce aqui. A chave da amostra (sampleKey) e a idempotencia:
 *       o reenvio da mesma leitura nao duplica a linha.</li>
 *   <li>{@link WorkOrderPort} — o nucleo de OS da P1.31 (COE-10): a
 *       ocorrencia do diario vira OS com alvo tipado
 *       {@code OPERATIONAL_SITE} sobre a unidade; o motor, os efeitos
 *       e a maquina de estados sao da P1.31.</li>
 *   <li>{@link RoundAlertPort} — o alerta de ronda nao realizada: o
 *       canal de aviso e o do prestador (o mesmo da mensageria); esta
 *       entrega decide QUANDO o alerta existe, nao por onde ele sai.</li>
 * </ul>
 */
public final class StationOpsPorts {

    private StationOpsPorts() {}

    /** Origem das amostras na serie da ED-17. */
    public enum SeriesOrigin { MANUAL, SUPERVISORY }

    /** Uma amostra pronta para a serie da ED-17, ja com origem e chave de idempotencia. */
    public record ManualSample(String stationCode, String tagCode, java.math.BigDecimal numericValue,
                               String unitOfMeasure, java.time.Instant sampledAt,
                               SeriesOrigin origin, String sampleKey) {
        public ManualSample {
            if (stationCode == null || stationCode.isBlank())
                throw new IllegalArgumentException("stationCode da amostra e obrigatorio");
            if (tagCode == null || tagCode.isBlank())
                throw new IllegalArgumentException("tagCode da amostra e obrigatorio");
            if (numericValue == null) throw new IllegalArgumentException("valor da amostra e obrigatorio");
            if (sampledAt == null) throw new IllegalArgumentException("instante da amostra e obrigatorio");
            if (origin == null) throw new IllegalArgumentException("origem da amostra e obrigatoria");
            if (sampleKey == null || sampleKey.isBlank())
                throw new IllegalArgumentException("sampleKey (idempotencia) da amostra e obrigatoria");
        }
    }

    /** Serie da ED-17: grava a amostra manual; devolve true se gravou, false se ja existia (reenvio). */
    public interface ManualSeriesPort {
        boolean record(UUID tenantId, ManualSample sample);
    }

    /** Nucleo de OS da P1.31: abre a OS da ocorrencia e devolve o id dela. */
    public interface WorkOrderPort {
        UUID openOccurrenceOrder(UUID tenantId, UUID operationalSiteId, String description, String requestedByRef);
    }

    /** Alerta de ronda nao realizada (slot vencido sem execucao completa). */
    public interface RoundAlertPort {
        void notifyMissedRound(UUID tenantId, UUID unitId, java.time.Instant slotStart, String reason);
    }
}
