package br.com.ziipsan.integration.hub;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P3.37: painel de saúde — a regra de derivação caso a caso, e as duas
 * visões (Central por integração, console por tenant).
 */
class IntegrationHealthPanelTest {

    private static final Instant T0 = Instant.parse("2026-11-07T12:00:00Z");
    private static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    private static IntegrationHealthPanel.IntegrationSnapshot snapshot(
            UUID tenant, CircuitBreaker.State circuit, Instant lastSuccess, Instant lastFailure,
            long pending, CredentialMonitor.ExpiryStatus credential,
            ContractTestService.ContractStatus contract) {
        return new IntegrationHealthPanel.IntegrationSnapshot(tenant, "psp-pix", IntegrationMode.SANDBOX,
                circuit, circuit == CircuitBreaker.State.OPEN ? 5 : 0, lastSuccess, lastFailure,
                pending, credential, contract);
    }

    private static IntegrationHealthPanel.IntegrationSnapshot healthy(UUID tenant) {
        return snapshot(tenant, CircuitBreaker.State.CLOSED, T0, null, 0,
                CredentialMonitor.ExpiryStatus.VALID, ContractTestService.ContractStatus.MATCH);
    }

    @Test
    @DisplayName("saudável com sucesso registrado = UP, sem motivos")
    void up() {
        IntegrationHealthPanel.IntegrationHealth health = IntegrationHealthPanel.evaluate(healthy(TENANT_A));
        assertThat(health.status()).isEqualTo(IntegrationHealthPanel.HealthStatus.UP);
        assertThat(health.reasons()).isEmpty();
    }

    @Test
    @DisplayName("sem nenhum sinal = UNKNOWN (ausência nunca é 'saudável')")
    void unknownWithoutSignals() {
        var s = snapshot(TENANT_A, CircuitBreaker.State.CLOSED, null, null, 0,
                CredentialMonitor.ExpiryStatus.VALID, ContractTestService.ContractStatus.NOT_RUN);
        IntegrationHealthPanel.IntegrationHealth health = IntegrationHealthPanel.evaluate(s);
        assertThat(health.status()).isEqualTo(IntegrationHealthPanel.HealthStatus.UNKNOWN);
        assertThat(health.reasons()).anyMatch(r -> r.contains("nenhum sucesso nem falha"));
    }

    @Test
    @DisplayName("circuito aberto ou credencial expirada/ausente = DOWN")
    void downCases() {
        var open = snapshot(TENANT_A, CircuitBreaker.State.OPEN, T0, T0, 0,
                CredentialMonitor.ExpiryStatus.VALID, ContractTestService.ContractStatus.MATCH);
        assertThat(IntegrationHealthPanel.evaluate(open).status())
                .isEqualTo(IntegrationHealthPanel.HealthStatus.DOWN);
        var expired = snapshot(TENANT_A, CircuitBreaker.State.CLOSED, T0, null, 0,
                CredentialMonitor.ExpiryStatus.EXPIRED, ContractTestService.ContractStatus.MATCH);
        assertThat(IntegrationHealthPanel.evaluate(expired).status())
                .isEqualTo(IntegrationHealthPanel.HealthStatus.DOWN);
        var missing = snapshot(TENANT_A, CircuitBreaker.State.CLOSED, T0, null, 0,
                CredentialMonitor.ExpiryStatus.NOT_FOUND, ContractTestService.ContractStatus.MATCH);
        assertThat(IntegrationHealthPanel.evaluate(missing).status())
                .isEqualTo(IntegrationHealthPanel.HealthStatus.DOWN);
    }

    @Test
    @DisplayName("fila pendente, credencial expirando ou DRIFT de contrato = DEGRADED (cada um sozinho)")
    void degradedCases() {
        var pending = snapshot(TENANT_A, CircuitBreaker.State.CLOSED, T0, null, 2,
                CredentialMonitor.ExpiryStatus.VALID, ContractTestService.ContractStatus.MATCH);
        assertThat(IntegrationHealthPanel.evaluate(pending).status())
                .isEqualTo(IntegrationHealthPanel.HealthStatus.DEGRADED);
        var expiring = snapshot(TENANT_A, CircuitBreaker.State.CLOSED, T0, null, 0,
                CredentialMonitor.ExpiryStatus.EXPIRING, ContractTestService.ContractStatus.MATCH);
        assertThat(IntegrationHealthPanel.evaluate(expiring).status())
                .isEqualTo(IntegrationHealthPanel.HealthStatus.DEGRADED);
        var drift = snapshot(TENANT_A, CircuitBreaker.State.CLOSED, T0, null, 0,
                CredentialMonitor.ExpiryStatus.VALID, ContractTestService.ContractStatus.DRIFT);
        IntegrationHealthPanel.IntegrationHealth driftHealth = IntegrationHealthPanel.evaluate(drift);
        assertThat(driftHealth.status()).isEqualTo(IntegrationHealthPanel.HealthStatus.DEGRADED);
        assertThat(driftHealth.reasons()).anyMatch(r -> r.contains("leiaute"));
        // NOT_RUN sozinho NÃO degrada: é ausência de prova, visível no campo próprio.
        var notRun = snapshot(TENANT_A, CircuitBreaker.State.CLOSED, T0, null, 0,
                CredentialMonitor.ExpiryStatus.VALID, ContractTestService.ContractStatus.NOT_RUN);
        assertThat(IntegrationHealthPanel.evaluate(notRun).status())
                .isEqualTo(IntegrationHealthPanel.HealthStatus.UP);
    }

    @Test
    @DisplayName("só falhas no histórico (sem sucesso) = DEGRADED, nunca UP")
    void failureOnlyIsDegraded() {
        var s = snapshot(TENANT_A, CircuitBreaker.State.CLOSED, null, T0, 0,
                CredentialMonitor.ExpiryStatus.VALID, ContractTestService.ContractStatus.MATCH);
        IntegrationHealthPanel.IntegrationHealth health = IntegrationHealthPanel.evaluate(s);
        assertThat(health.status()).isEqualTo(IntegrationHealthPanel.HealthStatus.DEGRADED);
        assertThat(health.reasons()).anyMatch(r -> r.contains("último sinal é uma falha"));
    }

    @Test
    @DisplayName("visão da Central: aba/rota da P3.15 e pior situação por integração; visão do tenant filtra")
    void centralAndTenantViews() {
        var downB = snapshot(TENANT_B, CircuitBreaker.State.OPEN, T0, T0, 0,
                CredentialMonitor.ExpiryStatus.VALID, ContractTestService.ContractStatus.MATCH);
        IntegrationHealthPanel.CentralIntegrationsView central =
                IntegrationHealthPanel.centralView(List.of(healthy(TENANT_A), downB));
        assertThat(central.tabKey()).isEqualTo("integracoes");
        assertThat(central.tabRoute()).isEqualTo("/central/integracoes");
        assertThat(central.lines()).hasSize(2);
        assertThat(central.worstByIntegration())
                .isEqualTo(Map.of("psp-pix", IntegrationHealthPanel.HealthStatus.DOWN));

        IntegrationHealthPanel.TenantIntegrationsView tenantView =
                IntegrationHealthPanel.tenantView(TENANT_A, List.of(healthy(TENANT_A), downB));
        assertThat(tenantView.lines()).hasSize(1);
        assertThat(tenantView.lines().get(0).status()).isEqualTo(IntegrationHealthPanel.HealthStatus.UP);
    }
}
