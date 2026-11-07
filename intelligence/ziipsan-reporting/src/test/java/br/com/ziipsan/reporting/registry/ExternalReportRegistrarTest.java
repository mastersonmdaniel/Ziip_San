package br.com.ziipsan.reporting.registry;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Mecanismo de registro da P3.31: so o mecanismo e da P3.22. */
class ExternalReportRegistrarTest {

    private ReportDefinition cert(String code, String view) {
        return new ReportDefinition(code, "Certificacoes: " + code, ReportOrigin.CERTIFICATION,
                view, "certification." + code.toLowerCase() + ":export",
                List.of(new ReportColumn("situacao", ReportColumnType.TEXT, false),
                        new ReportColumn("itens", ReportColumnType.LONG, false)),
                List.of(), "situacao", 5_000);
    }

    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();

    @Test
    void registraRelatorioDeCertificacao() {
        ReportRegistry registry = new ReportRegistry();
        ExternalReportRegistrar registrar = new ExternalReportRegistrar(registry);
        registrar.register(TENANT_A, cert("CERT-001", "rpt_cert_pendencias"));
        assertThat(registry.find("CERT-001", TENANT_A)).isPresent();
        assertThat(registry.allForTenant(TENANT_A)).hasSize(28);
    }

    @Test
    void registroPersisteERecarregaSoParaOTenantDono() {
        // Correcao G5: o registro vai para o store (report_definitions com
        // tenant_id na fiacao real); um registry NOVO sobre o mesmo store
        // o recarrega - e o tenant vizinho NAO o enxerga.
        InMemoryExternalDefinitionStore store = new InMemoryExternalDefinitionStore();
        ReportRegistry first = new ReportRegistry(store);
        new ExternalReportRegistrar(first).register(TENANT_A, cert("CERT-001", "rpt_cert_pendencias"));
        ReportRegistry reloaded = new ReportRegistry(store);
        assertThat(reloaded.find("CERT-001", TENANT_A)).isPresent();
        assertThat(reloaded.allForTenant(TENANT_A)).hasSize(28);
        assertThat(reloaded.find("CERT-001", TENANT_B)).isEmpty();
        assertThat(reloaded.allForTenant(TENANT_B)).hasSize(27);
    }

    @Test
    void recusaPermissaoQueNaoECoerenteComOCodigo() {
        // Alem do sufixo :export, a permissao tem de ser
        // <prefixo>.<codigo>:export (padrao do canExport).
        assertThatThrownBy(() -> new ReportDefinition("CERT-009", "x", ReportOrigin.CERTIFICATION,
                "rpt_ok", "reporting.rpt-001:export",
                List.of(new ReportColumn("a", ReportColumnType.TEXT, false)), List.of(), "a", 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReportDefinition("CERT-009", "x", ReportOrigin.CERTIFICATION,
                "rpt_ok", "certification.outro:export",
                List.of(new ReportColumn("a", ReportColumnType.TEXT, false)), List.of(), "a", 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recusaOrigemBuiltinCodigoDuplicadoEViewForaDoPadrao() {
        ReportRegistry registry = new ReportRegistry();
        ExternalReportRegistrar registrar = new ExternalReportRegistrar(registry);
        ReportDefinition builtin = new ReportDefinition("EXT-001", "x", ReportOrigin.BUILTIN,
                "rpt_ext", "reporting.ext-001:export",
                List.of(new ReportColumn("a", ReportColumnType.TEXT, false)), List.of(), "a", 100);
        assertThatThrownBy(() -> registrar.register(TENANT_A, builtin))
                .isInstanceOf(IllegalArgumentException.class);
        registrar.register(TENANT_A, cert("CERT-001", "rpt_cert_pendencias"));
        assertThatThrownBy(() -> registrar.register(TENANT_A, cert("CERT-001", "rpt_cert_outro")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cert("CERT-002", "view_sem_prefixo"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReportDefinition("CERT-003", "x", ReportOrigin.CERTIFICATION,
                "rpt_ok", "certification.sem-export",
                List.of(new ReportColumn("a", ReportColumnType.TEXT, false)), List.of(), "a", 100))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
