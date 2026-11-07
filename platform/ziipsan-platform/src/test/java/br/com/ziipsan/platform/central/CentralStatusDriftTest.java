package br.com.ziipsan.platform.central;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guarda de DERIVA dos conjuntos de estados copiados como texto nesta
 * fronteira (correção pós-revisão m-2).
 *
 * <p>Os conjuntos vivem como {@code String} porque o isolamento de
 * módulos (ADR-0018) proíbe a platform de depender de billing/fiscal em
 * compilação: o pom da platform só traz core+security. Sem o import dos
 * enums, uma renomeação futura em {@code ScheduleItemStatus} (P2.5) ou
 * {@code NfageStatus} (P2.6) NÃO quebraria a compilação aqui — por isso
 * este teste FIXA os literais esperados, escritos a partir das fontes:
 *
 * <ul>
 *   <li>ciclo: o CHECK de {@code cycle_schedule_items.status} na V4_3 —
 *       {@code CHECK (status IN ('PENDING', 'COMMANDED', 'DONE',
 *       'CANCELLED'))} — idêntico aos nomes de
 *       {@code ScheduleItemStatus};</li>
 *   <li>fiscal: os 9 nomes de {@code NfageStatus} (dicionário 23b
 *       §3.13) e o conjunto {@code LIVE} de
 *       {@code NfageStateMachine.isLive} =
 *       {RESERVED, SIGNED, TRANSMITTED, AUTHORIZED,
 *       PENDING_RECONCILIATION}, do qual a fila desta entrega é
 *       LIVE − AUTHORIZED.</li>
 * </ul>
 *
 * <p>Se um literal mudar na fonte, este teste falha aqui em vez de a
 * Central divergir em silêncio. A comparação DIRETA contra os enums
 * (no estilo do {@code CycleMigrationConsistencyTest} da P2.5, que vive
 * dentro do módulo dono do enum) fica para a integração, num módulo que
 * enxergue os dois lados sem violar o ADR-0018 — registrado no
 * RELATORIO-P2.11.
 */
class CentralStatusDriftTest {

    /** Literais do CHECK da V4_3 / nomes de ScheduleItemStatus (P2.5). */
    private static final Set<String> EXPECTED_CYCLE_STATUSES =
            Set.of("PENDING", "COMMANDED", "DONE", "CANCELLED");

    /** Os 9 nomes de NfageStatus (P2.6, dicionário 23b §3.13). */
    private static final Set<String> EXPECTED_DOCUMENT_STATUSES = Set.of(
            "RESERVED", "SIGNED", "TRANSMITTED", "AUTHORIZED", "REJECTED",
            "PENDING_RECONCILIATION", "CANCELLED", "REPLACED", "VOIDED");

    /** Conjunto LIVE de NfageStateMachine.isLive (P2.6). */
    private static final Set<String> EXPECTED_LIVE_STATUSES = Set.of(
            "RESERVED", "SIGNED", "TRANSMITTED", "AUTHORIZED", "PENDING_RECONCILIATION");

    @Test
    @DisplayName("P2.11 (m-2): situacoes do ciclo sao exatamente as do CHECK da V4_3 / ScheduleItemStatus")
    void cycleStatusesMatchV43Check() {
        assertThat(BillingCycleSnapshot.ITEM_STATUSES).isEqualTo(EXPECTED_CYCLE_STATUSES);
    }

    @Test
    @DisplayName("P2.11 (m-2): estados fiscais sao exatamente os 9 de NfageStatus")
    void documentStatusesMatchNfageStatus() {
        assertThat(FiscalQueueSnapshot.DOCUMENT_STATUSES).isEqualTo(EXPECTED_DOCUMENT_STATUSES);
        assertThat(FiscalQueueSnapshot.DOCUMENT_STATUSES).containsAll(EXPECTED_LIVE_STATUSES);
    }

    @Test
    @DisplayName("P2.11 (m-2): fila e exatamente o LIVE de NfageStateMachine menos AUTHORIZED")
    void queueStatusesAreLiveMinusAuthorized() {
        Set<String> expectedQueue = Set.of(
                "RESERVED", "SIGNED", "TRANSMITTED", "PENDING_RECONCILIATION");
        assertThat(EXPECTED_LIVE_STATUSES).contains("AUTHORIZED");
        assertThat(FiscalQueueSnapshot.QUEUE_STATUSES).isEqualTo(expectedQueue);
        assertThat(FiscalQueueSnapshot.QUEUE_STATUSES)
                .isSubsetOf(FiscalQueueSnapshot.DOCUMENT_STATUSES)
                .doesNotContain("AUTHORIZED", "REJECTED");
    }
}
