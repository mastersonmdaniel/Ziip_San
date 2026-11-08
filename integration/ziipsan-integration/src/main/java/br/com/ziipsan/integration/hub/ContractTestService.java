package br.com.ziipsan.integration.hub;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * P3.37: teste de contrato DIÁRIO contra o ambiente de HOMOLOGAÇÃO do
 * terceiro, por versão do parceiro, para detectar mudança de leiaute.
 * Para cada entrada do catálogo, o serviço pede ao conector a impressão
 * do leiaute de homologação (contexto SANDBOX — nunca PRODUCTION) e
 * compara com a esperada cadastrada:
 * <ul>
 *   <li>igual -> {@code MATCH};</li>
 *   <li>diferente -> {@code DRIFT} (mudança de leiaute detectada — o
 *       painel degrada e o alerta sai por esta situação);</li>
 *   <li>sem esperada cadastrada, sem conector ligado, ou conector que
 *       falha ao responder -> {@code NOT_RUN} com o motivo: ausência de
 *       prova nunca vira MATCH em silêncio.</li>
 * </ul>
 *
 * <p>"Diário" é cadência: {@link #isDue} diz quando a próxima rodada
 * vence (24 h desde a última); o agendamento (job) é da fiação.
 */
public final class ContractTestService {

    public enum ContractStatus { MATCH, DRIFT, NOT_RUN }

    public static final Duration DAILY_PERIOD = Duration.ofHours(24);

    /** Resultado de um teste de contrato para (integração, versão do parceiro). */
    public record ContractResult(String integrationName, String partnerVersion, ContractStatus status,
                                 String expectedFingerprint, String observedFingerprint,
                                 String detail, Instant at) {
        public ContractResult {
            IntegrationParameters.requireValidName(integrationName);
            Objects.requireNonNull(status, "situação do teste de contrato é obrigatória");
            Objects.requireNonNull(at, "instante do teste de contrato é obrigatório");
        }
    }

    /** De onde o serviço tira o conector ligado a um nome (a fiação registra os adaptadores). */
    @FunctionalInterface
    public interface ConnectorRegistry {
        Optional<ExternalConnector> connector(String integrationName);
    }

    private final PartnerConnectorCatalog catalog;
    private final ConnectorRegistry registry;

    public ContractTestService(PartnerConnectorCatalog catalog, ConnectorRegistry registry) {
        this.catalog = Objects.requireNonNull(catalog, "catálogo é obrigatório no teste de contrato");
        this.registry = Objects.requireNonNull(registry, "registro de conectores é obrigatório no teste de contrato");
    }

    /** A rodada está vencida? Sem rodada anterior, está sempre vencida. */
    public static boolean isDue(Instant lastRunAt, Instant now) {
        Objects.requireNonNull(now, "instante é obrigatório na cadência do teste de contrato");
        return lastRunAt == null || !now.isBefore(lastRunAt.plus(DAILY_PERIOD));
    }

    /**
     * Roda o teste para UMA entrada do catálogo. O contexto de homologação
     * é montado aqui com modo SANDBOX forçado e a referência de credencial
     * de homologação que a fiação entregar em {@code homologationCredentialRef}
     * (pode ser nula para parceiro sem credencial de homologação).
     */
    public ContractResult runOne(PartnerConnectorCatalog.CatalogEntry entry, UUID tenantId,
                                 String homologationCredentialRef, String correlationId, Instant now) {
        Objects.requireNonNull(entry, "entrada do catálogo é obrigatória no teste de contrato");
        Objects.requireNonNull(now, "instante é obrigatório no teste de contrato");
        String expected = entry.expectedLayoutFingerprint();
        if (expected == null || expected.isBlank()) {
            return new ContractResult(entry.name(), entry.partnerVersion(), ContractStatus.NOT_RUN,
                    null, null, "sem impressão esperada cadastrada para esta versão", now);
        }
        Optional<ExternalConnector> connector = registry.connector(entry.name());
        if (connector.isEmpty()) {
            return new ContractResult(entry.name(), entry.partnerVersion(), ContractStatus.NOT_RUN,
                    expected, null, "nenhum conector ligado a este nome", now);
        }
        ExternalConnector.ConnectorContext homologation = new ExternalConnector.ConnectorContext(
                tenantId, IntegrationMode.SANDBOX, homologationCredentialRef, correlationId);
        String observed;
        try {
            observed = connector.get().homologationLayoutFingerprint(homologation);
        } catch (RuntimeException e) {
            return new ContractResult(entry.name(), entry.partnerVersion(), ContractStatus.NOT_RUN,
                    expected, null, "conector falhou ao responder em homologação: " + e.getMessage(), now);
        }
        if (observed == null || observed.isBlank()) {
            return new ContractResult(entry.name(), entry.partnerVersion(), ContractStatus.NOT_RUN,
                    expected, null, "conector não devolveu impressão de leiaute em homologação", now);
        }
        boolean match = expected.equals(observed);
        return new ContractResult(entry.name(), entry.partnerVersion(),
                match ? ContractStatus.MATCH : ContractStatus.DRIFT,
                expected, observed,
                match ? "leiaute de homologação confere com o cadastrado"
                        : "MUDANÇA DE LEIAUTE detectada em homologação para a versão " + entry.partnerVersion(),
                now);
    }

    /** Roda a rodada completa: uma entrada -> um resultado, na ordem do catálogo. */
    public List<ContractResult> runAll(UUID tenantId, String homologationCredentialRef,
                                       String correlationId, Instant now) {
        List<ContractResult> out = new ArrayList<>();
        for (PartnerConnectorCatalog.CatalogEntry entry : catalog.entries()) {
            out.add(runOne(entry, tenantId, homologationCredentialRef, correlationId, now));
        }
        return List.copyOf(out);
    }
}
