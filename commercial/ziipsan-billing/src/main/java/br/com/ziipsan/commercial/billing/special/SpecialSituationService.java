package br.com.ziipsan.commercial.billing.special;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Inclusao e retirada de situacoes especiais por ordem de servico
 * (ED-22): {@code SPECIAL_SITUATION_SET} inclui, {@code SPECIAL_SITUATION_REMOVE}
 * retira. Em lote, a OS-mae ({@code batchId}) abre uma OS-filha por unidade -
 * o lote nao e um registro a parte: cada unidade ganha o seu
 * {@link SpecialSituationRecord} com o mesmo {@code batchId}.
 *
 * <p>A duracao e conferida contra {@code special_situation.max_months}
 * (parametro do tenant): situacao com fim previsto alem do teto e recusada;
 * situacao aberta (sem fim) e aceita, mas a retirada continua obrigatoria
 * por OS - o teto nao encerra nada sozinho.
 */
public final class SpecialSituationService {

    /** Pedido de inclusao (o conteudo da OS SPECIAL_SITUATION_SET). */
    public record SetRequest(UUID unitId, SpecialSituationRecord.Domain domain,
                             SpecialSituationType type, String reason, String legalBasis,
                             YearMonth startMonth, YearMonth endMonth,
                             Integer fixedWaterVolume, Integer fixedSewerVolume,
                             UUID openingOsId) {
    }

    private final SpecialSituationParameters parameters;

    public SpecialSituationService(SpecialSituationParameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("parametros sao obrigatorios");
        }
        this.parameters = parameters;
    }

    /** Inclui uma situacao individual (sem lote). */
    public SpecialSituationRecord set(SetRequest request, UUID newId) {
        return setInBatch(request, newId, null);
    }

    /**
     * Inclui uma situacao como filha do lote {@code batchOsId} (a OS-mae).
     * Tipo sem o indicador {@code allowsBatch} e recusado em lote.
     */
    public SpecialSituationRecord setInBatch(SetRequest request, UUID newId, UUID batchOsId) {
        if (request == null || newId == null) {
            throw new IllegalArgumentException("pedido e id sao obrigatorios");
        }
        if (batchOsId != null) {
            if (request.type() == null) {
                throw new IllegalArgumentException("inclusao em lote exige o tipo da situacao");
            }
            // Os indicadores entram como DADOS (D3): a recusa nao depende da
            // identidade do tipo, e sim do indicador allowsBatch declarado.
            requireBatchAllowed(request.type().asIndicators(), request.type().name());
        }
        SpecialSituationRecord record = new SpecialSituationRecord(newId, request.unitId(),
                request.domain(), request.type(), request.reason(), request.legalBasis(),
                request.startMonth(), request.endMonth(), null,
                request.fixedWaterVolume(), request.fixedSewerVolume(),
                request.openingOsId(), null, batchOsId);
        checkDuration(record);
        return record;
    }

    /** Expande o lote: uma OS-filha (registro) por unidade, todas com o batchId da OS-mae. */
    public List<SpecialSituationRecord> setBatch(List<SetRequest> requests, List<UUID> newIds, UUID batchOsId) {
        if (requests == null || newIds == null || requests.size() != newIds.size()) {
            throw new IllegalArgumentException("lote exige um pedido e um id por unidade");
        }
        if (batchOsId == null) {
            throw new IllegalArgumentException("lote exige a OS-mae (batchId)");
        }
        List<SpecialSituationRecord> records = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            records.add(setInBatch(requests.get(i), newIds.get(i), batchOsId));
        }
        return List.copyOf(records);
    }

    /**
     * Portao do lote, lido dos indicadores como dados (correcao pos-revisao,
     * D3): indicadores com {@code allowsBatch=false} sao recusados, venham
     * de um tipo real ou de um conjunto sintetico declarado em teste. Na
     * tabela real atual os quatro tipos declaram {@code allowsBatch=true}
     * (sem fonte no 23b para diferenciar - ver {@link SpecialSituationType}),
     * logo hoje a recusa nao dispara para tipo real; o ramo e o mesmo codigo
     * que {@link #setInBatch} executa.
     */
    static void requireBatchAllowed(SpecialSituationType.Indicators indicators, String typeLabel) {
        if (indicators == null) {
            throw new IllegalArgumentException("indicadores do tipo sao obrigatorios");
        }
        if (!indicators.allowsBatch()) {
            throw new IllegalArgumentException(
                    "tipo " + typeLabel + " nao admite inclusao em lote");
        }
    }

    /** Retira (encerra) a situacao pela OS SPECIAL_SITUATION_REMOVE. */
    public SpecialSituationRecord remove(SpecialSituationRecord record, YearMonth withdrawnMonth,
                                         UUID closingOsId) {
        if (record == null) {
            throw new IllegalArgumentException("registro e obrigatorio");
        }
        if (record.withdrawnMonth() != null) {
            throw new IllegalArgumentException("situacao ja retirada em " + record.withdrawnMonth());
        }
        return record.withdrawn(withdrawnMonth, closingOsId);
    }

    private void checkDuration(SpecialSituationRecord record) {
        long duration = record.durationMonths();
        if (duration > 0 && duration > parameters.maxMonths()) {
            throw new IllegalArgumentException("special_situation.max_months: situacao de " + duration
                    + " meses excede o teto de " + parameters.maxMonths());
        }
    }
}
