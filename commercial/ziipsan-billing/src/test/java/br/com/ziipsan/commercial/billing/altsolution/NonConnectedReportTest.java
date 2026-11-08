package br.com.ziipsan.commercial.billing.altsolution;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NonConnectedReportTest {

    private static NonConnectedReport.CrossingFact fact(boolean available, boolean branch,
                                                          boolean active, boolean exempted) {
        return new NonConnectedReport.CrossingFact(UUID.randomUUID(), UUID.randomUUID(),
                available, branch, active, exempted);
    }

    @Test @DisplayName("CAD-87: entram so rede disponivel + sem ligacao + sem dispensa; ramal sem ligacao e sinalizado")
    void filtersNonConnected() {
        var withBranch = fact(true, true, false, false);       // entra, com ramal
        var noBranch = fact(true, false, false, false);        // entra, sem ramal
        var connected = fact(true, true, true, false);         // fora: ligado
        var exempted = fact(true, false, false, true);         // fora: dispensado comprovado
        var noNetwork = fact(false, false, false, false);      // fora: sem rede disponivel
        var report = NonConnectedReport.build(NonConnectedReport.Audience.AGENCY,
                List.of(withBranch, noBranch, connected, exempted, noNetwork));
        assertThat(report.total()).isEqualTo(2);
        assertThat(report.entries()).anySatisfy(e -> {
            assertThat(e.propertyId()).isEqualTo(withBranch.propertyId());
            assertThat(e.branchWithoutConnection()).isTrue();
        });
        assertThat(report.entries()).anySatisfy(e -> {
            assertThat(e.propertyId()).isEqualTo(noBranch.propertyId());
            assertThat(e.branchWithoutConnection()).isFalse();
        });
    }

    @Test @DisplayName("lista ao titular e a agencia: mesmo cruzamento, destinatarios distintos, CSV com cabecalho")
    void audiencesAndCsv() {
        var f = fact(true, true, false, false);
        var toHolder = NonConnectedReport.build(NonConnectedReport.Audience.TITLEHOLDER, List.of(f));
        var toAgency = NonConnectedReport.build(NonConnectedReport.Audience.AGENCY, List.of(f));
        assertThat(toHolder.total()).isEqualTo(toAgency.total()).isEqualTo(1);
        assertThat(toHolder.toCsv()).startsWith("property_id,consumer_unit_id,");
        assertThat(toHolder.toCsv()).contains(f.propertyId().toString());
    }

    @Test @DisplayName("sem nao conectados: relatorio vazio, sem erro")
    void emptyReport() {
        var report = NonConnectedReport.build(NonConnectedReport.Audience.AGENCY,
                List.of(fact(true, true, true, false)));
        assertThat(report.total()).isZero();
    }
}
