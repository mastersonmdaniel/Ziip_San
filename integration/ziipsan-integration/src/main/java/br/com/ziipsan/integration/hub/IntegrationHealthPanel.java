package br.com.ziipsan.integration.hub;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * P3.37: painel de saúde por integração e por tenant — a superfície da
 * aba Integrações da Central (P3.15: aba {@code integracoes}, rota
 * {@code /central/integracoes}, registrada como REFERRAL da P3.37 em
 * {@code CentralCompleteTabs}) e do console do tenant. Serviço puro
 * sobre instantâneos, como a P3.15: quem coleta monta o
 * {@link IntegrationSnapshot} a partir do circuito, da fila, do cofre e
 * do teste de contrato; aqui só se DERIVA a situação, sempre pela mesma
 * regra:
 *
 * <ul>
 *   <li>{@code DOWN}: circuito ABERTO ou credencial EXPIRADA/NOT_FOUND;</li>
 *   <li>{@code DEGRADED}: circuito MEIO-ABERTO, fila com pendências,
 *       credencial EXPIRING/NO_EXPIRY_DECLARED, ou contrato em DRIFT;</li>
 *   <li>{@code UNKNOWN}: nenhum sucesso nem falha registrados ainda —
 *       ausência de sinal nunca é exibida como "saudável";</li>
 *   <li>{@code UP}: houve sucesso, nenhum dos sinais acima. Teste de
 *       contrato NOT_RUN não degrada sozinho: é ausência de prova, e
 *       fica visível no campo próprio da visão.</li>
 * </ul>
 */
public final class IntegrationHealthPanel {

    public enum HealthStatus { UP, DEGRADED, DOWN, UNKNOWN }

    /** Chave e rota da aba na Central (espelham o registro da P3.15; a unificação é da integração). */
    public static final String CENTRAL_TAB_KEY = "integracoes";
    public static final String CENTRAL_TAB_ROUTE = "/central/integracoes";

    /** Instantâneo de uma (integração, tenant) montado pelo coletor da fiação. */
    public record IntegrationSnapshot(UUID tenantId, String integrationName, IntegrationMode mode,
                                      CircuitBreaker.State circuitState, int consecutiveFailures,
                                      Instant lastSuccessAt, Instant lastFailureAt,
                                      long pendingFailures,
                                      CredentialMonitor.ExpiryStatus credentialStatus,
                                      ContractTestService.ContractStatus lastContractStatus) {
        public IntegrationSnapshot {
            Objects.requireNonNull(tenantId, "tenantId é obrigatório no instantâneo de saúde");
            IntegrationParameters.requireValidName(integrationName);
            Objects.requireNonNull(mode, "modo é obrigatório no instantâneo de saúde");
            Objects.requireNonNull(circuitState, "estado do circuito é obrigatório no instantâneo de saúde");
            if (consecutiveFailures < 0) {
                throw new IllegalArgumentException("falhas consecutivas não podem ser negativas: " + consecutiveFailures);
            }
            if (pendingFailures < 0) {
                throw new IllegalArgumentException("pendências da fila não podem ser negativas: " + pendingFailures);
            }
            Objects.requireNonNull(credentialStatus, "situação da credencial é obrigatória no instantâneo de saúde");
            Objects.requireNonNull(lastContractStatus, "situação do contrato é obrigatória no instantâneo de saúde");
        }
    }

    /** Uma linha do painel: o instantâneo + a situação derivada + os motivos legíveis. */
    public record IntegrationHealth(IntegrationSnapshot snapshot, HealthStatus status, List<String> reasons) {
        public IntegrationHealth {
            Objects.requireNonNull(snapshot, "instantâneo é obrigatório na linha de saúde");
            Objects.requireNonNull(status, "situação é obrigatória na linha de saúde");
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }
    }

    /** Visão da aba Integrações da Central: uma linha por (integração, tenant), na ordem de entrada. */
    public record CentralIntegrationsView(String tabKey, String tabRoute, List<IntegrationHealth> lines) {
        public CentralIntegrationsView {
            lines = lines == null ? List.of() : List.copyOf(lines);
        }

        /** Pior situação por integração (todos os tenants): DOWN > DEGRADED > UNKNOWN > UP. */
        public Map<String, HealthStatus> worstByIntegration() {
            Map<String, HealthStatus> out = new LinkedHashMap<>();
            for (IntegrationHealth line : lines) {
                out.merge(line.snapshot().integrationName(), line.status(),
                        (a, b) -> severity(b) > severity(a) ? b : a);
            }
            return Map.copyOf(out);
        }
    }

    /** Visão do console do tenant: só as integrações daquele tenant. */
    public record TenantIntegrationsView(UUID tenantId, List<IntegrationHealth> lines) {
        public TenantIntegrationsView {
            Objects.requireNonNull(tenantId, "tenantId é obrigatório na visão do tenant");
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }

    private IntegrationHealthPanel() {
    }

    private static int severity(HealthStatus status) {
        return switch (status) {
            case UP -> 0;
            case UNKNOWN -> 1;
            case DEGRADED -> 2;
            case DOWN -> 3;
        };
    }

    /** Deriva a situação de UM instantâneo, com os motivos na ordem da regra. */
    public static IntegrationHealth evaluate(IntegrationSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "instantâneo é obrigatório na avaliação de saúde");
        List<String> reasons = new ArrayList<>();
        boolean down = false;
        boolean degraded = false;
        if (snapshot.circuitState() == CircuitBreaker.State.OPEN) {
            down = true;
            reasons.add("circuito aberto após " + snapshot.consecutiveFailures() + " falhas consecutivas");
        }
        if (snapshot.credentialStatus() == CredentialMonitor.ExpiryStatus.EXPIRED) {
            down = true;
            reasons.add("credencial expirada");
        }
        if (snapshot.credentialStatus() == CredentialMonitor.ExpiryStatus.NOT_FOUND) {
            down = true;
            reasons.add("credencial não encontrada no cofre");
        }
        if (snapshot.circuitState() == CircuitBreaker.State.HALF_OPEN) {
            degraded = true;
            reasons.add("circuito meio-aberto (chamada de prova)");
        }
        if (snapshot.pendingFailures() > 0) {
            degraded = true;
            reasons.add(snapshot.pendingFailures() + " mensagem(ns) pendente(s) na fila de falhas");
        }
        if (snapshot.credentialStatus() == CredentialMonitor.ExpiryStatus.EXPIRING) {
            degraded = true;
            reasons.add("credencial expirando dentro da janela de alerta");
        }
        if (snapshot.credentialStatus() == CredentialMonitor.ExpiryStatus.NO_EXPIRY_DECLARED) {
            degraded = true;
            reasons.add("credencial sem expiração declarada");
        }
        if (snapshot.lastContractStatus() == ContractTestService.ContractStatus.DRIFT) {
            degraded = true;
            reasons.add("mudança de leiaute detectada no teste de contrato");
        }
        HealthStatus status;
        if (down) {
            status = HealthStatus.DOWN;
        } else if (degraded) {
            status = HealthStatus.DEGRADED;
        } else if (snapshot.lastSuccessAt() == null && snapshot.lastFailureAt() == null) {
            status = HealthStatus.UNKNOWN;
            reasons.add("nenhum sucesso nem falha registrados ainda");
        } else if (snapshot.lastSuccessAt() == null) {
            // Só há falhas no histórico (abaixo do limiar do circuito e já
            // fora da fila): nunca exibir como saudável.
            status = HealthStatus.DEGRADED;
            reasons.add("sem sucesso registrado; o último sinal é uma falha");
        } else {
            status = HealthStatus.UP;
        }
        return new IntegrationHealth(snapshot, status, reasons);
    }

    /** Visão da Central sobre todos os instantâneos entregues. */
    public static CentralIntegrationsView centralView(List<IntegrationSnapshot> snapshots) {
        Objects.requireNonNull(snapshots, "instantâneos são obrigatórios na visão da Central");
        List<IntegrationHealth> lines = new ArrayList<>();
        for (IntegrationSnapshot s : snapshots) {
            lines.add(evaluate(s));
        }
        return new CentralIntegrationsView(CENTRAL_TAB_KEY, CENTRAL_TAB_ROUTE, lines);
    }

    /** Visão do console do tenant: filtra os instantâneos do tenant e avalia. */
    public static TenantIntegrationsView tenantView(UUID tenantId, List<IntegrationSnapshot> snapshots) {
        Objects.requireNonNull(tenantId, "tenantId é obrigatório na visão do tenant");
        Objects.requireNonNull(snapshots, "instantâneos são obrigatórios na visão do tenant");
        List<IntegrationHealth> lines = new ArrayList<>();
        for (IntegrationSnapshot s : snapshots) {
            if (s.tenantId().equals(tenantId)) {
                lines.add(evaluate(s));
            }
        }
        return new TenantIntegrationsView(tenantId, lines);
    }
}
