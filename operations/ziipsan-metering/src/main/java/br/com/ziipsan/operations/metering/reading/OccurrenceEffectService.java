package br.com.ziipsan.operations.metering.reading;

import br.com.ziipsan.operations.metering.rules.MeteringErrorCode;
import br.com.ziipsan.operations.metering.rules.MeteringInputException;
import br.com.ziipsan.operations.metering.rules.ReadingOccurrence;

import java.util.Optional;
import java.util.UUID;

/**
 * Ocorrências de leitura e seus efeitos lidos do catálogo da P2.5
 * (ED-15): esta entrega <b>não</b> define ocorrência nenhuma nem duplica
 * efeito — lê a {@link ReadingOccurrence} do catálogo do tenant (o da
 * P2.5, JOR-10: tipo de OS do catálogo da P1.31, consumo a faturar por
 * média/mínimo/sem faturar, mudança de situação e perda de benefício)
 * pela porta e devolve o efeito completo do caso.
 *
 * <p>Código fora do catálogo do tenant é recusa explícita, como na
 * sincronização de campo da ED-18: ocorrência desconhecida nunca vira
 * "sem efeito" em silêncio.
 */
public final class OccurrenceEffectService {

    private OccurrenceEffectService() {
    }

    /** Porta de leitura do catálogo de ocorrências da P2.5, por tenant. */
    public interface Catalog {
        Optional<ReadingOccurrence> findByCode(UUID tenantId, String code);
    }

    /**
     * O efeito completo da ocorrência para o caso concreto.
     *
     * @param hasReading houve leitura atual informada neste ciclo
     * @throws MeteringInputException código ausente ou fora do catálogo do tenant
     */
    public static ReadingOccurrence.Effect effectFor(Catalog catalog, UUID tenantId,
                                                     String occurrenceCode, boolean hasReading) {
        if (catalog == null) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    "catálogo de ocorrências da P2.5 é obrigatório");
        }
        if (occurrenceCode == null || occurrenceCode.isBlank()) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    "código da ocorrência de leitura é obrigatório");
        }
        ReadingOccurrence occurrence = catalog.findByCode(tenantId, occurrenceCode)
                .orElseThrow(() -> new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                        "ocorrência fora do catálogo do tenant (P2.5): " + occurrenceCode));
        return occurrence.effect(hasReading);
    }
}
