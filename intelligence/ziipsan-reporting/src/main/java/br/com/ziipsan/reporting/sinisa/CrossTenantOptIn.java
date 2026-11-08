package br.com.ziipsan.reporting.sinisa;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import java.time.Instant;
import java.util.UUID;

/**
 * Adesao registrada de um tenant a comparacao entre tenants do benchmark
 * (GES-29): o parametro {@code bench.cross_tenant_opt_in} (classe b, padrao
 * DESLIGADO — ver {@link BenchmarkParameters}) so vale com o registro
 * desta adesao, que exige a referencia da CLAUSULA CONTRATUAL que autoriza
 * o uso agregado do dado e o instante do registro. Adesao sem clausula e
 * recusada na construcao: opt-in nao se presume de um booleano solto.
 */
public record CrossTenantOptIn(UUID tenantId, boolean optedIn, String contractClauseReference,
                               Instant registeredAt) {

    public CrossTenantOptIn {
        if (tenantId == null) {
            throw new IndicatorValidationException("TENANT_REQUIRED", "tenant e obrigatorio");
        }
        if (optedIn) {
            if (contractClauseReference == null || contractClauseReference.isBlank()) {
                throw new IndicatorValidationException("OPT_IN_WITHOUT_CONTRACT_CLAUSE",
                        "adesao a comparacao entre tenants sem a referencia da clausula "
                                + "contratual: recusada (GES-29)");
            }
            if (registeredAt == null) {
                throw new IndicatorValidationException("OPT_IN_WITHOUT_REGISTRATION",
                        "adesao a comparacao entre tenants sem o instante do registro: recusada");
            }
        }
    }

    /** O estado padrao de quem nunca aderiu: desligado, sem clausula. */
    public static CrossTenantOptIn notOptedIn(UUID tenantId) {
        return new CrossTenantOptIn(tenantId, false, null, null);
    }
}
