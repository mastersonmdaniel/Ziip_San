package br.com.ziipsan.operations.metering.rules;

import br.com.ziipsan.operations.metering.domain.ConsumptionRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Versionamento das linhas de consumo (P2.5, DAD-14): retificar cria versao nova
 * em vez de UPDATE, e o historico de 120 meses se reconstroi das versoes -- este
 * teste nao toca em audit_log nenhum (a regra nem recebe um).
 */
class ConsumptionVersioningTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID UNIT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BILL_A = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID BILL_B = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private static ConsumptionRecord record(YearMonth reference, int billed) {
        ConsumptionRecord r = new ConsumptionRecord();
        r.setId(UUID.randomUUID());
        r.setTenantId(TENANT);
        r.setUnitId(UNIT);
        r.setServiceKind(ConsumptionRecord.ServiceKind.WATER);
        r.setReferenceMonth(reference);
        r.setBilledConsumption(billed);
        r.setCalculatedConsumption(billed);
        r.setBillable(true);
        r.setBillingPaused(false);
        r.setConsumptionType(ConsumptionType.REAL);
        r.setDiscardedRemainder(0);
        r.setBillId(BILL_A);
        r.setBillReferenceMonth(reference.atDay(1));
        return r;
    }

    @Test
    @DisplayName("retificacao nao faz UPDATE: cria a versao seguinte e a antiga so ganha a ligacao")
    void rectificationCreatesNewVersionInsteadOfUpdating() {
        ConsumptionRecord original = record(YearMonth.of(2026, 3), 27);

        ConsumptionRecord rectified = ConsumptionRectification.rectify(
                original, BILL_B, LocalDate.of(2026, 4, 1), "[\"pv-1\"]");
        rectified.setBilledConsumption(25);
        rectified.setCalculatedConsumption(25);

        // A nova versao: outro id, version_no seguinte, vigente, ligada a conta nova.
        assertThat(rectified.getId()).isNotEqualTo(original.getId());
        assertThat(rectified.getVersionNo()).isEqualTo(2);
        assertThat(rectified.isCurrent()).isTrue();
        assertThat(rectified.getBillId()).isEqualTo(BILL_B);
        assertThat(rectified.getBillReferenceMonth()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(rectified.getParameterVersionIds()).isEqualTo("[\"pv-1\"]");
        assertThat(rectified.getBilledConsumption()).isEqualTo(25);

        // A antiga: TODOS os valores como foram faturados; so superseded_by_id mudou.
        assertThat(original.getBilledConsumption()).isEqualTo(27);
        assertThat(original.getCalculatedConsumption()).isEqualTo(27);
        assertThat(original.getVersionNo()).isEqualTo(1);
        assertThat(original.getBillId()).isEqualTo(BILL_A);
        assertThat(original.isCurrent()).isFalse();
        assertThat(original.getSupersededById()).isEqualTo(rectified.getId());
    }

    @Test
    @DisplayName("retificar versao ja substituida recusa (a cadeia nao bifurca)")
    void rectifyingSupersededVersionIsRefused() {
        ConsumptionRecord original = record(YearMonth.of(2026, 3), 27);
        ConsumptionRectification.rectify(original, BILL_B, LocalDate.of(2026, 4, 1), null);

        assertThatThrownBy(() -> ConsumptionRectification.rectify(original, BILL_B, LocalDate.of(2026, 4, 1), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("so a versao vigente pode ser retificada");
    }

    @Test
    @DisplayName("cadeia de retificacoes: versoes 1, 2, 3 ligadas em ordem, so a ultima vigente")
    void rectificationChainLinksInOrder() {
        ConsumptionRecord v1 = record(YearMonth.of(2026, 3), 27);
        ConsumptionRecord v2 = ConsumptionRectification.rectify(v1, BILL_B, LocalDate.of(2026, 4, 1), null);
        ConsumptionRecord v3 = ConsumptionRectification.rectify(v2, BILL_B, LocalDate.of(2026, 4, 1), null);

        assertThat(v1.getSupersededById()).isEqualTo(v2.getId());
        assertThat(v2.getSupersededById()).isEqualTo(v3.getId());
        assertThat(v3.getSupersededById()).isNull();
        assertThat(List.of(v1, v2, v3)).extracting(ConsumptionRecord::getVersionNo).containsExactly(1, 2, 3);

        List<ConsumptionRecord> chain = ConsumptionHistory.versionChain(List.of(v3, v1, v2), YearMonth.of(2026, 3));
        assertThat(chain).containsExactly(v1, v2, v3);
    }

    @Test
    @DisplayName("historico de 120 meses reconstituido das versoes, sem audit_log: vigentes, na janela, em ordem")
    void historyOf120MonthsIsRebuiltFromVersions() {
        YearMonth last = YearMonth.of(2026, 10);
        List<ConsumptionRecord> all = new ArrayList<>();
        // 121 meses de historia: o mais antigo (fora da janela de 120) nao pode entrar.
        for (int i = 0; i <= 120; i++) {
            all.add(record(last.minusMonths(i), 10 + i));
        }
        // Um mes retificado dentro da janela: a versao substituida nao conta como
        // historico vigente; a vigente entra com o valor retificado.
        ConsumptionRecord june = all.stream()
                .filter(r -> r.referenceMonth().equals(YearMonth.of(2026, 6))).findFirst().orElseThrow();
        ConsumptionRecord juneRectified = ConsumptionRectification.rectify(
                june, BILL_B, LocalDate.of(2026, 7, 1), null);
        juneRectified.setBilledConsumption(99);
        all.add(juneRectified);

        List<ConsumptionRecord> history = ConsumptionHistory.reconstructCurrent(all, last);

        assertThat(history).hasSize(120);
        assertThat(history.getFirst().referenceMonth()).isEqualTo(last.minusMonths(119));
        assertThat(history.getLast().referenceMonth()).isEqualTo(last);
        assertThat(history).isSortedAccordingTo((a, b) -> a.referenceMonth().compareTo(b.referenceMonth()));
        assertThat(history).allMatch(ConsumptionRecord::isCurrent);
        ConsumptionRecord juneInHistory = history.stream()
                .filter(r -> r.referenceMonth().equals(YearMonth.of(2026, 6))).findFirst().orElseThrow();
        assertThat(juneInHistory.getBilledConsumption()).isEqualTo(99);
        assertThat(juneInHistory.getVersionNo()).isEqualTo(2);
    }

    @Test
    @DisplayName("janela menor que a historia corta pelo fim: months e o tamanho exato da janela")
    void shorterWindowEndsAtLastReference() {
        YearMonth last = YearMonth.of(2026, 10);
        List<ConsumptionRecord> all = List.of(
                record(last.minusMonths(2), 1), record(last.minusMonths(1), 2), record(last, 3));

        assertThat(ConsumptionHistory.reconstructCurrent(all, last, 2))
                .extracting(ConsumptionRecord::referenceMonth)
                .containsExactly(last.minusMonths(1), last);
    }
}
