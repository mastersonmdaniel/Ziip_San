package br.com.ziipsan.reporting.sheet;

import br.com.ziipsan.core.incident.IncidentIndicators;
import br.com.ziipsan.core.incident.NetworkIncident;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A base de intermitencia/extravasamento da ED-92 (OPE-16) consumida pela
 * ED-77: paralisacao de 6h+ qualifica para o I-04; a de 2h, nao. Massa
 * sintetica declarada sobre a entidade REAL da ED-92.
 */
class Ed92IntermittencyBaseTest {

    private NetworkIncident incident(String code, LocalDateTime start, int economies, Double km,
                                     NetworkIncident.Type type) {
        return new NetworkIncident(UUID.randomUUID(), UUID.randomUUID(), code, type, false,
                NetworkIncident.Origin.CREW, start,
                new NetworkIncident.AffectedSnapshot(List.of(), economies, km), null);
    }

    @Test
    void intermitenciaDaEd92QualificaSoSeisHorasOuMais() {
        LocalDateTime t0 = LocalDateTime.of(2026, 1, 10, 0, 0);
        NetworkIncident longa = incident("OC-LONGA", t0, 100, null, NetworkIncident.Type.INTERRUPTION);
        longa.markRestored(t0.plusHours(8));
        NetworkIncident curta = incident("OC-CURTA", t0, 50, null, NetworkIncident.Type.INTERRUPTION);
        curta.markRestored(t0.plusHours(2));
        IncidentIndicators.IntermittencyBase base = IncidentIndicators.intermittency(
                List.of(longa, curta), 6, t0.plusHours(9));
        assertEquals(1, base.qualifying().size());
        assertEquals(100, base.economiesAffected());
        assertEquals(800.0, base.economyHours(), 0.001);
    }

    @Test
    void extravasamentoPorKmDaEd92() {
        LocalDateTime t0 = LocalDateTime.of(2026, 1, 10, 0, 0);
        NetworkIncident o1 = incident("OC-E1", t0, 0, 25.0, NetworkIncident.Type.OVERFLOW);
        NetworkIncident o2 = incident("OC-E2", t0, 0, 15.0, NetworkIncident.Type.OVERFLOW);
        IncidentIndicators.OverflowBase base = IncidentIndicators.overflow(List.of(o1, o2));
        assertEquals(2, base.overflowCount());
        assertEquals(0.05, base.perKm().orElseThrow(), 0.0001);
    }
}
