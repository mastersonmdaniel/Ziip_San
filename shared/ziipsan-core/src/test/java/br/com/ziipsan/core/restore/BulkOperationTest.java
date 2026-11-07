package br.com.ziipsan.core.restore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes puros da operacao em massa reversivel (P3.33): duas etapas,
 * run_id, remocao por execucao e OS inversa (ADR-0039).
 */
class BulkOperationTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID RUN = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID OTHER_RUN = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID ACTOR = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final Instant T0 = Instant.parse("2027-02-01T10:00:00Z");
    private static final Instant T1 = Instant.parse("2027-02-01T11:00:00Z");
    private static final Instant T2 = Instant.parse("2027-02-01T12:00:00Z");

    private static BulkOperation.BulkRun simulated() {
        return BulkOperation.BulkRun.simulate(RUN, TENANT, "BULK_PARAM_UPDATE", 1200, T0);
    }

    private static BulkOperation.BulkRun applied() {
        return simulated().apply(1200, ACTOR, T1);
    }

    @Test
    @DisplayName("duas etapas: simulacao -> aplicacao -> reversao, com os instantes e atores gravados")
    void twoPhaseLifecycle() {
        BulkOperation.BulkRun applied = applied();
        assertThat(applied.status()).isEqualTo(BulkOperation.RunStatus.APPLIED);
        BulkOperation.BulkRun reverted = applied.revert(RUN, ACTOR, T2);
        assertThat(reverted.status()).isEqualTo(BulkOperation.RunStatus.REVERTED);
        assertThat(reverted.revertedAt()).isEqualTo(T2);
        // O original e imutavel: aplicar/reverter devolvem copias.
        assertThat(applied.status()).isEqualTo(BulkOperation.RunStatus.APPLIED);
    }

    @Test
    @DisplayName("aplicar sem simular, reverter simulacao, aplicar ou reverter duas vezes: recusado com codigo estavel")
    void illegalTransitionsRefused() {
        assertThatThrownBy(() -> simulated().revert(RUN, ACTOR, T1))
                .isInstanceOf(BulkOperation.BulkRefusedException.class)
                .hasMessageContaining("aplicada")
                .extracting(e -> ((BulkOperation.BulkRefusedException) e).code())
                .isEqualTo("not-applied");
        assertThatThrownBy(() -> applied().apply(1, ACTOR, T2))
                .isInstanceOf(BulkOperation.BulkRefusedException.class)
                .extracting(e -> ((BulkOperation.BulkRefusedException) e).code())
                .isEqualTo("not-simulated");
        assertThatThrownBy(() -> applied().revert(RUN, ACTOR, T2).revert(RUN, ACTOR, T2))
                .isInstanceOf(BulkOperation.BulkRefusedException.class)
                .extracting(e -> ((BulkOperation.BulkRefusedException) e).code())
                .isEqualTo("not-applied");
    }

    @Test
    @DisplayName("remocao por execucao: run_id diverso do aplicado e recusado antes de qualquer efeito")
    void revertByWrongRunIdRefused() {
        assertThatThrownBy(() -> applied().revert(OTHER_RUN, ACTOR, T2))
                .isInstanceOf(BulkOperation.BulkRefusedException.class)
                .extracting(e -> ((BulkOperation.BulkRefusedException) e).code())
                .isEqualTo("run-id-mismatch");
    }

    @Test
    @DisplayName("simulacao descartada nao pode ser aplicada depois")
    void discardedSimulationCannotApply() {
        BulkOperation.BulkRun discarded = simulated().discard();
        assertThat(discarded.status()).isEqualTo(BulkOperation.RunStatus.DISCARDED);
        assertThatThrownBy(() -> discarded.apply(1, ACTOR, T1))
                .isInstanceOf(BulkOperation.BulkRefusedException.class);
    }

    @Test
    @DisplayName("efeito de negocio por OS exige o descritor da OS inversa ligado a OS de origem e a execucao (ADR-0039)")
    void businessEffectRequiresInverseOrder() {
        UUID orderId = UUID.randomUUID();
        BulkOperation.InverseOrderRequest req = BulkOperation.requireInverseOrderIfBusinessEffect(
                applied(), orderId, "lote aplicado com parametro errado", ACTOR, T2);
        assertThat(req.originalOrderId()).isEqualTo(orderId);
        assertThat(req.bulkRunId()).isEqualTo(RUN);
        assertThat(req.toJson()).contains("INVERSE_SERVICE_ORDER");
        // Sem efeito por OS, a remocao por execucao basta: nenhum descritor.
        assertThat(BulkOperation.requireInverseOrderIfBusinessEffect(
                applied(), null, "sem efeito de OS", ACTOR, T2)).isNull();
        // Justificativa vazia nao abre correcao de negocio.
        assertThatThrownBy(() -> BulkOperation.requireInverseOrderIfBusinessEffect(
                applied(), orderId, "  ", ACTOR, T2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("construtor canonico nao monta estado que a maquina recusa (SIMULATED aplicada, APPLIED revertida, cronologia invertida)")
    void canonicalConstructorGuardsStates() {
        assertThatThrownBy(() -> new BulkOperation.BulkRun(RUN, TENANT, "OP", 10, T0,
                10L, T1, ACTOR, null, null, BulkOperation.RunStatus.SIMULATED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BulkOperation.BulkRun(RUN, TENANT, "OP", 10, T0,
                10L, T1, ACTOR, T2, ACTOR, BulkOperation.RunStatus.APPLIED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BulkOperation.BulkRun(RUN, TENANT, "OP", 10, T1,
                10L, T0, ACTOR, null, null, BulkOperation.RunStatus.APPLIED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("trilha: a entrada de auditoria carrega run_id, codigo da decisao e sai em JSON pela classe Json")
    void auditEntryJson() {
        RestoreAudit.RestoreAuditEntry entry = new RestoreAudit.RestoreAuditEntry(
                TENANT, RestoreAudit.RestoreAction.BULK_REVERTED, ACTOR, "PLATFORM_OWNER",
                true, "allowed", null, RUN, null, null, T2);
        assertThat(entry.detailsJson())
                .contains("\"runId\":\"55555555-5555-5555-5555-555555555555\"")
                .contains("\"action\":\"BULK_REVERTED\"");
    }
}
