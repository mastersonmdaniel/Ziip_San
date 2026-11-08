package br.com.ziipsan.core.certification.env14001;

import br.com.ziipsan.core.effluent.SludgeAspectSummary;
import br.com.ziipsan.core.effluent.SludgeRecord;
import br.com.ziipsan.core.stationops.StationUnit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O aspecto do lodo LE da ED-105 (classes REAIS compiladas no
 * harness): geracao/destinacao registradas la, resumo lido aqui —
 * com a norma ligada OU desligada (a leitura nao recebe ativacao).
 */
class SludgeAspectViewTest {

    private SludgeRecord sludge(String origin, String qty, boolean dispatched) {
        SludgeRecord r = new SludgeRecord(TestSupport.id(), TestSupport.TENANT, TestSupport.id(),
                StationUnit.UnitKind.WWTP, SludgeRecord.ResidueKind.SLUDGE, origin,
                new BigDecimal(qty), "t", SludgeRecord.QuantityMethod.WEIGHED,
                Instant.parse("2026-01-05T10:00:00Z"), null);
        r.store("SYN-patio", Instant.parse("2026-01-05T12:00:00Z"));
        if (dispatched) {
            r.dispatch("SYN-transportador", "SYN-destino", "SYN-doc",
                    Instant.parse("2026-01-06T09:00:00Z"));
        }
        return r;
    }

    @Test
    void readsRealEd105GenerationAndDestination() {
        SludgeAspectSummary summary = SludgeAspectSummary.summarize(TestSupport.TENANT,
                List.of(sludge("SYN-origem-a", "10", true), sludge("SYN-origem-b", "5", false)));
        SludgeAspectView view = SludgeAspectView.read(summary);
        assertThat(view.generated("SLUDGE", "t")).isEqualByComparingTo("15");
        assertThat(view.dispatchedCount()).isEqualTo(1);
        assertThat(view.awaitingDestinationCount()).isEqualTo(1);
        assertThat(view.hasAwaitingDestination()).isTrue();
    }

    @Test
    void readingDoesNotDependOnTheNormBeingEnabled() {
        // A leitura nao recebe Iso14001Activation: o mesmo resumo e
        // legivel no cenario da norma desligada (obrigacao REGULATORY
        // da ED-105) e no da norma ligada — por construcao da API.
        SludgeAspectSummary summary = SludgeAspectSummary.summarize(TestSupport.TENANT,
                List.of(sludge("SYN-origem-a", "3", true)));
        SludgeAspectView withNormOff = SludgeAspectView.read(summary);
        SludgeAspectView withNormOn = SludgeAspectView.read(summary);
        assertThat(withNormOff).isEqualTo(withNormOn);
        assertThat(withNormOff.generated("SLUDGE", "t")).isEqualByComparingTo("3");
        // E o registro declarativo do aspecto, quando a norma esta
        // ligada, continua sendo so o template — sem geracao propria:
        assertThat(TestSupport.disabled().enabled()).isFalse();
    }

    @Test
    void otherTenantRecordsAreNotCounted() {
        SludgeRecord foreign = new SludgeRecord(TestSupport.id(), TestSupport.OTHER_TENANT,
                TestSupport.id(), StationUnit.UnitKind.WTP, SludgeRecord.ResidueKind.SLUDGE,
                "SYN-origem", new BigDecimal("7"), "t", SludgeRecord.QuantityMethod.WEIGHED,
                Instant.parse("2026-01-05T10:00:00Z"), null);
        SludgeAspectSummary summary = SludgeAspectSummary.summarize(TestSupport.TENANT,
                List.of(foreign));
        assertThat(SludgeAspectView.read(summary).generated("SLUDGE", "t"))
                .isEqualByComparingTo("0");
    }
}
