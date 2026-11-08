package br.com.ziipsan.core.effluent;

import br.com.ziipsan.core.stationops.StationUnit;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Massa SINTETICA dos testes da ED-105: parametros genericos
 * PARAM_A/PARAM_B com limites arbitrarios de fixture — NAO sao
 * valores de nenhuma norma (nem da CONAMA 430/2011, nem da revisao
 * nao publicada). Os limites reais entram por carga revisada do
 * catalogo de normas; destinos/origens sao codigos de fixture.
 */
final class TestSupport {

    static final UUID TENANT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final UUID ETA_ID = UUID.fromString("22222222-2222-2222-2222-2222222222e1");
    static final UUID ETE_ID = UUID.fromString("22222222-2222-2222-2222-2222222222e2");
    static final LocalDate JAN = LocalDate.of(2026, 1, 1);
    static final Instant T0 = Instant.parse("2026-03-10T08:00:00Z");

    private TestSupport() {}

    static StationUnit eta() {
        return new StationUnit(ETA_ID, TENANT, "ETA-01", StationUnit.UnitKind.WTP,
                "SISTEMA-A", StationUnit.Filtration.NOT_APPLICABLE, List.of(), true);
    }

    static StationUnit ete() {
        return new StationUnit(ETE_ID, TENANT, "ETE-01", StationUnit.UnitKind.WWTP,
                "SISTEMA-A", StationUnit.Filtration.NOT_APPLICABLE, List.of(), true);
    }

    static EffluentStandard federalMax(String key, String max, boolean reviewed) {
        return new EffluentStandard(key, "Parametro " + key, "u", EffluentStandard.Comparator.MAX,
                null, new BigDecimal(max), null, "NORMA-SINTETICA [FIXTURE]",
                EffluentStandard.Jurisdiction.FEDERAL, null,
                EffluentStandard.PublicationStatus.PUBLISHED, reviewed,
                reviewed ? "RT-FIXTURE" : null, JAN, null);
    }

    static EffluentStandard stateMax(String key, String max, boolean reviewed, LocalDate from, LocalDate to) {
        return new EffluentStandard(key, "Parametro " + key, "u", EffluentStandard.Comparator.MAX,
                null, new BigDecimal(max), null, "NORMA-ESTADUAL-SINTETICA [FIXTURE]",
                EffluentStandard.Jurisdiction.STATE, "XX",
                EffluentStandard.PublicationStatus.PUBLISHED, reviewed,
                reviewed ? "RT-FIXTURE" : null, from, to);
    }

    static EffluentCatalog catalog(EffluentStandard... standards) {
        return new EffluentCatalog("FIXTURE-1", List.of(standards));
    }

    /** Registro de lodo de ETE gerado (fixture), ainda nao armazenado. */
    static SludgeRecord generatedSludge(String originCode) {
        return new SludgeRecord(UUID.randomUUID(), TENANT, ETE_ID, StationUnit.UnitKind.WWTP,
                SludgeRecord.ResidueKind.SLUDGE, originCode, new BigDecimal("12.5"), "m3",
                SludgeRecord.QuantityMethod.VOLUME_ESTIMATED, T0, null);
    }

    static SludgeRecord storedSludge(String originCode) {
        SludgeRecord r = generatedSludge(originCode);
        r.store("PATIO-A", T0.plusSeconds(3600));
        return r;
    }
}
