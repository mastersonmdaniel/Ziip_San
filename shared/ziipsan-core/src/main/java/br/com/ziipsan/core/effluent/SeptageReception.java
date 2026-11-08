package br.com.ziipsan.core.effluent;

import br.com.ziipsan.core.stationops.StationUnit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Recepcao de lodo de fossa (septico) na ETE: registro de ENTRADA,
 * distinto da geracao propria — a ETE recebe o lodo de terceiro
 * (limpa-fossa) com volume e origem declarados (aceite da ED-105).
 * O lodo de fossa recebido NAO vira geracao do tenant neste
 * registro; a destinacao posterior do residuo da ETE segue pelo
 * {@link SludgeRecord} normalmente.
 *
 * <p>{@code originCode} e o mesmo vocabulario de origens de
 * {@code waste.mtr_exempt_origins} quando a fiacao o mapear; a
 * descricao da origem e obrigatoria porque "origem" sem declaracao
 * nao e registro. O vinculo opcional {@code shiftLogId} e o turno
 * do diario da ED-87 em que a recepcao aconteceu.
 *
 * <p>Correcao D4 (revisao da ED-105): a unidade receptora e
 * verificada como ETE — o aceite fala em recepcao "na ETE" e o
 * registro recebe o {@code receivingUnitKind} (o
 * {@link StationUnit.UnitKind} da ED-87), recusando qualquer
 * unidade que nao seja {@code WWTP}, como o {@link SludgeRecord}
 * ja fazia para a unidade geradora. Antes da correcao o registro
 * recebia so o UUID da unidade, sem prova do tipo.
 */
public record SeptageReception(
        UUID id,
        UUID tenantId,
        UUID receivingUnitId,
        StationUnit.UnitKind receivingUnitKind,
        BigDecimal volume,
        String volumeUnit,
        String originCode,
        String originDescription,
        String carrierRef,
        Instant receivedAt,
        UUID shiftLogId) {

    public SeptageReception {
        if (id == null) throw new IllegalArgumentException("id da recepcao e obrigatorio");
        if (tenantId == null) throw new IllegalArgumentException("tenantId e obrigatorio");
        if (receivingUnitId == null)
            throw new IllegalArgumentException("a ETE receptora e obrigatoria");
        if (receivingUnitKind == null)
            throw new IllegalArgumentException("o tipo da unidade receptora e obrigatorio");
        if (receivingUnitKind != StationUnit.UnitKind.WWTP)
            throw new IllegalArgumentException("lodo de fossa so e recebido na ETE (unidade WWTP),"
                    + " nao em " + receivingUnitKind);
        if (volume == null || volume.signum() <= 0)
            throw new IllegalArgumentException("volume do lodo de fossa recebido deve ser maior que zero");
        if (volumeUnit == null || volumeUnit.isBlank())
            throw new IllegalArgumentException("unidade do volume e obrigatoria (dado do registro)");
        if (originCode == null || originCode.isBlank())
            throw new IllegalArgumentException("codigo da origem do lodo de fossa e obrigatorio");
        if (originDescription == null || originDescription.isBlank())
            throw new IllegalArgumentException("descricao da origem do lodo de fossa e obrigatoria:"
                    + " recepcao sem origem declarada nao e registro");
        if (carrierRef == null || carrierRef.isBlank())
            throw new IllegalArgumentException("transportador que entregou o lodo de fossa e obrigatorio");
        if (receivedAt == null) throw new IllegalArgumentException("instante da recepcao e obrigatorio");
        originCode = originCode.trim();
        volumeUnit = volumeUnit.trim();
    }
}
