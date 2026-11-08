package br.com.ziipsan.integration.hub;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P3.37: teste de contrato diário em homologação, por versão do
 * parceiro. Conector falso devolve a impressão programada; nenhuma
 * chamada sai em PRODUCTION (o contexto é montado SANDBOX pelo serviço
 * e o teste o confere).
 */
class ContractTestServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");
    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private static final class FakeConnector implements ExternalConnector {
        private final String fingerprint;
        private IntegrationMode seenMode;

        FakeConnector(String fingerprint) {
            this.fingerprint = fingerprint;
        }

        @Override public String name() { return "psp-pix"; }
        @Override public Kind kind() { return Kind.PSP; }
        @Override public String partnerVersion() { return "2.1"; }

        @Override
        public ConnectionTestResult testConnection(ConnectorContext context) {
            return new ConnectionTestResult(true, 1, "ok", NOW);
        }

        @Override
        public ConnectorResponse send(ConnectorContext context, ConnectorRequest request) {
            return new ConnectorResponse(Outcome.SUCCESS, "ext-1", "{}", null);
        }

        @Override
        public String homologationLayoutFingerprint(ConnectorContext homologationContext) {
            this.seenMode = homologationContext.mode();
            return fingerprint;
        }
    }

    private static PartnerConnectorCatalog.CatalogEntry entry(String expected) {
        return new PartnerConnectorCatalog.CatalogEntry("psp-pix", ExternalConnector.Kind.PSP,
                "PSP-EXEMPLO", "2.1", Set.of("PIX"),
                Optional.of(new PartnerConnectorCatalog.PspBehaviorView("PSP-EXEMPLO", "reenvia", 60,
                        "PORTAL", false, Optional.empty(), false, false)),
                expected);
    }

    private static ContractTestService service(PartnerConnectorCatalog catalog, ExternalConnector connector) {
        Map<String, ExternalConnector> byName = connector == null ? Map.of() : Map.of("psp-pix", connector);
        return new ContractTestService(catalog, name -> Optional.ofNullable(byName.get(name)));
    }

    @Test
    @DisplayName("impressão igual = MATCH, e o contexto de homologação é SANDBOX")
    void matchInSandbox() {
        PartnerConnectorCatalog catalog = new PartnerConnectorCatalog();
        catalog.register(entry("hash-A"));
        FakeConnector fake = new FakeConnector("hash-A");
        ContractTestService.ContractResult result =
                service(catalog, fake).runOne(entry("hash-A"), TENANT, null, "corr-ct", NOW);
        // runOne usa a entrada do catálogo; aqui a mesma entrada é passada de novo.
        assertThat(result.status()).isEqualTo(ContractTestService.ContractStatus.MATCH);
        assertThat(fake.seenMode).isEqualTo(IntegrationMode.SANDBOX);
    }

    @Test
    @DisplayName("impressão diferente = DRIFT (mudança de leiaute detectada), com esperada e observada")
    void driftDetected() {
        PartnerConnectorCatalog catalog = new PartnerConnectorCatalog();
        var e = entry("hash-A");
        catalog.register(e);
        ContractTestService.ContractResult result =
                service(catalog, new FakeConnector("hash-B")).runOne(e, TENANT, null, "corr-ct", NOW);
        assertThat(result.status()).isEqualTo(ContractTestService.ContractStatus.DRIFT);
        assertThat(result.expectedFingerprint()).isEqualTo("hash-A");
        assertThat(result.observedFingerprint()).isEqualTo("hash-B");
        assertThat(result.detail()).contains("MUDANÇA DE LEIAUTE");
    }

    @Test
    @DisplayName("sem esperada cadastrada ou sem conector: NOT_RUN explícito, nunca MATCH")
    void notRunIsExplicit() {
        PartnerConnectorCatalog catalog = new PartnerConnectorCatalog();
        var noExpected = entry(null);
        catalog.register(noExpected);
        assertThat(service(catalog, new FakeConnector("hash-A"))
                .runOne(noExpected, TENANT, null, "corr-ct", NOW).status())
                .isEqualTo(ContractTestService.ContractStatus.NOT_RUN);
        var withExpected = entry("hash-A");
        assertThat(service(catalog, null).runOne(withExpected, TENANT, null, "corr-ct", NOW).status())
                .isEqualTo(ContractTestService.ContractStatus.NOT_RUN);
    }

    @Test
    @DisplayName("cadência diária: sem rodada está vencido; 23h59 não; 24h00 sim (fronteira)")
    void dailyCadenceBoundary() {
        assertThat(ContractTestService.isDue(null, NOW)).isTrue();
        assertThat(ContractTestService.isDue(NOW.minusSeconds(23 * 3600 + 59 * 60), NOW)).isFalse();
        assertThat(ContractTestService.isDue(NOW.minusSeconds(24 * 3600), NOW)).isTrue();
    }

    @Test
    @DisplayName("runAll devolve um resultado por entrada do catálogo, na ordem")
    void runAllCoversCatalog() {
        PartnerConnectorCatalog catalog = new PartnerConnectorCatalog();
        catalog.register(entry("hash-A"));
        List<ContractTestService.ContractResult> results =
                service(catalog, new FakeConnector("hash-A")).runAll(TENANT, null, "corr-ct", NOW);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).integrationName()).isEqualTo("psp-pix");
        assertThat(results.get(0).partnerVersion()).isEqualTo("2.1");
    }
}
