package br.com.ziipsan.operations.metering.supervisory;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.operations.metering.domain.StationOutage;
import br.com.ziipsan.operations.metering.repository.StationOutageRepository;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro de paradas de estação (ED-17): o registro manual de paradas da
 * ETA que o aceite pede nesta entrega, e o fechamento da parada. O diário
 * de operação de estações que consome estes registros é da ED-87 (fora
 * desta entrega).
 *
 * <p>Uma estação não tem duas paradas abertas: registrar parada com outra
 * aberta é conflito explícito, e fechar parada já fechada também.
 *
 * <p>Classe simples, sem anotação, como os demais serviços do projeto.
 */
public class StationOutageService {

    private final StationOutageRepository outages;

    public StationOutageService(StationOutageRepository outages) {
        this.outages = Objects.requireNonNull(outages, "O repositório de paradas é obrigatório");
    }

    /** Registra manualmente o início de uma parada de estação. */
    public StationOutage registerManual(UUID tenantId, String stationCode, OffsetDateTime startedAt,
                                        String reason, UUID actor) {
        Objects.requireNonNull(stationCode, "A estação é obrigatória");
        Objects.requireNonNull(startedAt, "O início da parada é obrigatório");
        if (reason == null || reason.isBlank()) {
            throw new ApiException.Validation.of(
                    new ApiException.FieldError("reason", "required", "O motivo da parada é obrigatório."));
        }
        if (outages.findByTenantIdAndStationCodeAndEndedAtIsNull(tenantId, stationCode).isPresent()) {
            throw new ApiException.Conflict("station-outage-already-open",
                    "A estação " + stationCode + " já tem uma parada aberta.");
        }
        StationOutage outage = new StationOutage();
        outage.setTenantId(tenantId);
        outage.setStationCode(stationCode);
        outage.setStartedAt(startedAt);
        outage.setReason(reason);
        outage.setSource(StationOutage.Source.MANUAL);
        outage.setRegisteredBy(actor);
        return outages.save(outage);
    }

    /** Fecha a parada aberta com o instante do fim (posterior ao início). */
    public StationOutage close(UUID tenantId, UUID outageId, OffsetDateTime endedAt) {
        StationOutage outage = outages.findByTenantIdAndId(tenantId, outageId)
                .orElseThrow(() -> new ApiException.NotFound("Parada de estação não encontrada."));
        if (outage.getEndedAt() != null) {
            throw new ApiException.Conflict("station-outage-already-closed", "A parada já foi fechada.");
        }
        if (endedAt == null || !endedAt.isAfter(outage.getStartedAt())) {
            throw new ApiException.Validation.of(new ApiException.FieldError(
                    "endedAt", "invalid", "O fim da parada precisa ser posterior ao início."));
        }
        outage.setEndedAt(endedAt);
        return outages.save(outage);
    }
}
