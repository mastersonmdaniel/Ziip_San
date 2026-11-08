package br.com.ziipsan.reporting.sheet;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import java.util.UUID;

/**
 * Elo da trilha auditavel (GES-30): liga a apuracao ao DADO PRIMARIO que a
 * produziu - leitura, ocorrencia de rede (ED-92) ou protocolo. Sem elo, a
 * apuracao nao e auditavel e a construcao recusa.
 */
public record PrimaryDataLink(Kind kind, UUID primaryId, String primaryCode) {

    public enum Kind { READING, NETWORK_INCIDENT, PROTOCOL }

    public PrimaryDataLink {
        if (kind == null) {
            throw new IndicatorValidationException("LINEAGE_KIND_REQUIRED",
                    "elo de trilha sem tipo de dado primario (leitura, ocorrencia ou protocolo)");
        }
        if (primaryId == null && (primaryCode == null || primaryCode.isBlank())) {
            throw new IndicatorValidationException("LINEAGE_REFERENCE_REQUIRED",
                    "elo de trilha sem referencia ao dado primario " + kind);
        }
    }
}
