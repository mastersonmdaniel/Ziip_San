package br.com.ziipsan.grs.mtr.service;

import br.com.ziipsan.grs.mtr.domain.WasteMtr;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Criterios de busca de MTR. Todos os campos sao opcionais: o campo nulo nao
 * restringe a consulta. Os tipos espelham as colunas de waste_mtr.
 */
public record MtrSearchCriteria(
        String mtrNumber,
        WasteMtr.MtrStatus status,
        UUID generatorId,
        UUID transporterId,
        UUID destinationId,
        UUID wasteClassId,
        String vehiclePlate,
        OffsetDateTime issuedFrom,
        OffsetDateTime issuedTo,
        WasteMtr.SinirSyncStatus sinirSyncStatus
) {

    public static MtrSearchCriteria empty() {
        return new MtrSearchCriteria(null, null, null, null, null, null, null, null, null, null);
    }
}