package br.com.ziipsan.integration.esfinge;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.parameters.MissingParameterException;
import br.com.ziipsan.integration.esfinge.EsfingeRemittance.ReturnOutcome;
import br.com.ziipsan.integration.esfinge.EsfingeRemittance.ReturnReceipt;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EsfingeFileLedgerReportTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 11, 5, 10, 0, 0, 0, ZoneOffset.UTC);

    private EsfingeRemittanceFile.GeneratedFile generatedFile(boolean official) {
        var layout = EsfingeTestSupport.fixtureLayout(official);
        var data = EsfingeTestSupport.validData();
        var validation = EsfingeValidator.validate(layout, EsfingeTestSupport.COMPETENCE, data);
        return EsfingeRemittanceFile.generate(TENANT, EsfingeTestSupport.COMPETENCE, layout,
                EsfingeTestSupport.params(), data, validation, NOW);
    }

    @Test
    void parametroDeVersaoAusenteEhErroExplicito() {
        var params = EsfingeParameters.fromValues(Map.of());
        assertThatThrownBy(params::layoutVersion)
                .isInstanceOf(MissingParameterException.class)
                .hasMessageContaining(EsfingeParameters.LAYOUT_VERSION);
    }

    @Test
    void arquivoCarregaVersaoHashEResumosDasDependencias() {
        var file = generatedFile(false);
        assertThat(file.layoutVersion()).isEqualTo(EsfingeTestSupport.LAYOUT_VERSION);
        assertThat(file.layoutOfficial()).isFalse();
        assertThat(file.sha256()).isEqualTo(EsfingeRemittanceFile.sha256(file.json()));
        assertThat(file.sha256()).hasSize(64);
        assertThat(file.json()).contains("\"layoutVersion\":\"" + EsfingeTestSupport.LAYOUT_VERSION + "\"");
        assertThat(file.json()).contains(EsfingeLayout.OFFICIAL_LAYOUT_PENDING_MARKER);
        assertThat(file.json()).contains("\"source\":\"ED-31\"");
        assertThat(file.json()).contains("\"source\":\"ED-32\"");
        assertThat(file.json()).contains("\"recordCount\":3");
    }

    @Test
    void versaoDoLeiauteDivergenteDoParametroRecusaGeracao() {
        var layout = EsfingeTestSupport.fixtureLayout(false);
        var data = EsfingeTestSupport.validData();
        var validation = EsfingeValidator.validate(layout, EsfingeTestSupport.COMPETENCE, data);
        var otherParams = EsfingeParameters.fromValues(Map.of(
                EsfingeParameters.LAYOUT_VERSION, "\"OUTRA-9.9\""));
        assertThatThrownBy(() -> EsfingeRemittanceFile.generate(TENANT, EsfingeTestSupport.COMPETENCE,
                layout, otherParams, data, validation, NOW))
                .isInstanceOf(ApiException.Conflict.class)
                .hasMessageContaining("difere da versao aceita");
    }

    @Test
    void remessaComErroDeConsistenciaNaoGeraArquivo() {
        var layout = EsfingeTestSupport.fixtureLayout(false);
        var data = EsfingeTestSupport.validData();
        var invalid = EsfingeValidator.validate(layout, java.time.YearMonth.of(2025, 12), data);
        assertThat(invalid.valid()).isFalse();
        assertThatThrownBy(() -> EsfingeRemittanceFile.generate(TENANT, EsfingeTestSupport.COMPETENCE,
                layout, EsfingeTestSupport.params(), data, invalid, NOW))
                .isInstanceOf(ApiException.Conflict.class)
                .hasMessageContaining("nao gera arquivo");
    }

    @Test
    void cicloDeVidaValidadaBaixadaRetornoRegistrado() {
        var ledger = new EsfingeRemittance.Ledger();
        var remittance = ledger.register(TENANT, EsfingeTestSupport.COMPETENCE, generatedFile(false), NOW);
        assertThat(remittance.status()).isEqualTo(EsfingeRemittance.Status.VALIDATED);
        assertThat(remittance.sha256()).hasSize(64);
        assertThat(remittance.layoutVersion()).isEqualTo(EsfingeTestSupport.LAYOUT_VERSION);

        var downloaded = ledger.markDownloaded(remittance.id(), NOW.plusHours(1), "operador-do-cliente");
        assertThat(downloaded.status()).isEqualTo(EsfingeRemittance.Status.DOWNLOADED);
        assertThat(downloaded.downloadedBy()).isEqualTo("operador-do-cliente");

        var withReturn = ledger.registerReturn(remittance.id(),
                new ReturnReceipt("PROTOCOLO-TESTE-1", ReturnOutcome.ACCEPTED, LocalDate.of(2026, 11, 6), null));
        assertThat(withReturn.status()).isEqualTo(EsfingeRemittance.Status.RETURN_REGISTERED);
        assertThat(withReturn.returnReceipt().outcome()).isEqualTo(ReturnOutcome.ACCEPTED);
    }

    @Test
    void retornoAntesDoDownloadEDownloadDuplicadoSaoRecusados() {
        var ledger = new EsfingeRemittance.Ledger();
        var remittance = ledger.register(TENANT, EsfingeTestSupport.COMPETENCE, generatedFile(false), NOW);
        assertThatThrownBy(() -> ledger.registerReturn(remittance.id(),
                new ReturnReceipt("P-1", ReturnOutcome.ACCEPTED, LocalDate.of(2026, 11, 6), null)))
                .isInstanceOf(ApiException.Conflict.class).hasMessageContaining("depois do download");
        ledger.markDownloaded(remittance.id(), NOW.plusHours(1), "operador-do-cliente");
        assertThatThrownBy(() -> ledger.markDownloaded(remittance.id(), NOW.plusHours(2), "operador-do-cliente"))
                .isInstanceOf(ApiException.Conflict.class).hasMessageContaining("VALIDATED");
        ledger.registerReturn(remittance.id(),
                new ReturnReceipt("P-1", ReturnOutcome.ACCEPTED, LocalDate.of(2026, 11, 6), null));
        assertThatThrownBy(() -> ledger.registerReturn(remittance.id(),
                new ReturnReceipt("P-2", ReturnOutcome.ACCEPTED, LocalDate.of(2026, 11, 7), null)))
                .isInstanceOf(ApiException.Conflict.class);
    }

    @Test
    void duplicidadePorCompetenciaEVersaoERecusadaMasRejeicaoAdmiteRetificacao() {
        var ledger = new EsfingeRemittance.Ledger();
        var first = ledger.register(TENANT, EsfingeTestSupport.COMPETENCE, generatedFile(false), NOW);
        assertThatThrownBy(() -> ledger.register(TENANT, EsfingeTestSupport.COMPETENCE, generatedFile(false), NOW.plusDays(1)))
                .isInstanceOf(ApiException.Conflict.class).hasMessageContaining("Ja existe remessa");
        ledger.markDownloaded(first.id(), NOW.plusHours(1), "operador-do-cliente");
        ledger.registerReturn(first.id(),
                new ReturnReceipt("P-REJ", ReturnOutcome.REJECTED, LocalDate.of(2026, 11, 6), "consistencia"));
        var rectified = ledger.register(TENANT, EsfingeTestSupport.COMPETENCE, generatedFile(false), NOW.plusDays(2));
        assertThat(rectified.id()).isNotEqualTo(first.id());
        assertThat(ledger.ofTenant(TENANT)).hasSize(2);
    }

    @Test
    void relatorioDeEnvioListaHashVersaoRetornoEContaSoAceite() {
        var ledger = new EsfingeRemittance.Ledger();
        var remittance = ledger.register(TENANT, EsfingeTestSupport.COMPETENCE, generatedFile(false), NOW);
        ledger.markDownloaded(remittance.id(), NOW.plusHours(1), "operador-do-cliente");

        var beforeReturn = EsfingeShipmentReport.generate(TENANT, ledger.entries(), NOW.plusDays(1));
        assertThat(beforeReturn.acceptedCount()).isZero();
        assertThat(beforeReturn.text()).contains("Enviadas com aceite (retorno ACCEPTED): 0");

        ledger.registerReturn(remittance.id(),
                new ReturnReceipt("PROTOCOLO-TESTE-1", ReturnOutcome.ACCEPTED, LocalDate.of(2026, 11, 6), null));
        var report = EsfingeShipmentReport.generate(TENANT, ledger.entries(), NOW.plusDays(2));
        assertThat(report.totalRemittances()).isEqualTo(1);
        assertThat(report.acceptedCount()).isEqualTo(1);
        assertThat(report.sha256()).isEqualTo(EsfingeRemittanceFile.sha256(report.text()));
        assertThat(report.text()).contains(remittance.sha256());
        assertThat(report.text()).contains(EsfingeTestSupport.LAYOUT_VERSION);
        assertThat(report.text()).contains("PROTOCOLO-TESTE-1");
        assertThat(report.text()).contains("ACCEPTED");
        // Leiaute nao oficial: o relatorio NAO esconde a pendencia.
        assertThat(report.text()).contains(EsfingeLayout.OFFICIAL_LAYOUT_PENDING_MARKER);
    }

    @Test
    void relatorioComLeiauteOficialNaoTrazMarcadorDePendencia() {
        var ledger = new EsfingeRemittance.Ledger();
        ledger.register(TENANT, EsfingeTestSupport.COMPETENCE, generatedFile(true), NOW);
        var report = EsfingeShipmentReport.generate(TENANT, ledger.entries(), NOW.plusDays(1));
        assertThat(report.text()).doesNotContain(EsfingeLayout.OFFICIAL_LAYOUT_PENDING_MARKER);
        assertThat(report.text()).contains("(oficial)");
    }
}
