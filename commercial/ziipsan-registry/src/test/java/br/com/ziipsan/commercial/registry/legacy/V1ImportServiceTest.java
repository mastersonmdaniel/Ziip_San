package br.com.ziipsan.commercial.registry.legacy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P2.10 sobre o motor da P1.2, tudo em memória e com massa 100% sintética
 * DECLARADA aqui: os saldos "do legado" destes testes são os valores das
 * constantes abaixo, escritos à mão neste arquivo — a conferência compara
 * a soma dos débitos criados contra ESTES valores, e nenhum deles é dado
 * de legado real.
 *
 * <p>Cenário (mesma massa-base do teste da P1.2): LEG-1 (matrícula
 * 910001, saldo declarado de 1.234,56) com acordo ACORDO-7 de 2 prestações
 * a vencer de 200,00; LEG-2 (910002, saldo 100,00) sem prestações; LEG-3
 * com documento inválido, recusada já na aplicação da P1.2.
 */
class V1ImportServiceTest {

    private static final String PILOTO_CSV = """
            matricula_legada;matricula;localidade;setor;quadra;rota;categoria;lote;sublote;sequencia_leitura;saldo;dt_ligacao;titular;cpf_cnpj
            LEG-1;910001;1;10;5;20;RES;10;0;1;1.234,56;05/03/2020;Fulano Sintético 1;111.444.777-35
            LEG-2;910002;1;10;5;20;COM;11;0;2;100,00;06/03/2020;Empresa Sintética Alfa;12.abc.345/01de-35
            LEG-3;910003;1;20;6;30;RES;12;0;3;0,00;07/03/2020;Fulano Sintético 3;111.444.777-36
            """;

    private static final String PILOTO_INSTALLMENTS_CSV = """
            matricula_legada;acordo;parcela;vencimento;valor
            LEG-1;ACORDO-7;1;10/04/2026;200,00
            LEG-1;ACORDO-7;2;10/05/2026;200,00
            """;

    private static final LocalDate CUTOVER = LocalDate.of(2026, 10, 1);
    private static final UUID TENANT = UUID.randomUUID();

    private static String mappingJson(String path) {
        try (InputStream in = V1ImportServiceTest.class.getResourceAsStream(path)) {
            assertThat(in).as("recurso %s", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class FakeApplier implements LegacyUnitApplier {
        @Override
        public Outcome apply(StagedUnit unit, UUID actorId) {
            return new Outcome(UUID.randomUUID(), List.of());
        }
    }

    private static final class FakeBatchGateway implements LegacyBatchOrderGateway {
        @Override
        public OpenedOrder openBatchOrder(LegacyBatch batch, List<StagedUnit> appliedUnits, UUID actorId) {
            return new OpenedOrder(UUID.randomUUID(), "CLOSED_EXECUTED");
        }
    }

    private static final class FakeDebitGateway implements InstallmentDebitGateway {
        final List<InstallmentDebit> installments = new ArrayList<>();
        final List<ResidualDebit> residuals = new ArrayList<>();
        /** Campo {@code installments} de cada débito de prestação criado (prova A6: sempre 1). */
        final List<Integer> installmentCounts = new ArrayList<>();

        @Override
        public UUID createInstallmentDebit(UUID tenantId, UUID actorId, InstallmentDebit debit) {
            installments.add(debit);
            installmentCounts.add(debit.installments());
            return UUID.randomUUID();
        }

        @Override
        public UUID createResidualDebit(UUID tenantId, UUID actorId, ResidualDebit debit) {
            residuals.add(debit);
            return UUID.randomUUID();
        }
    }

    private static final class FakeSituationGateway implements UnitSituationGateway {
        final List<String> applied = new ArrayList<>();

        @Override
        public void applyOccupancyStatus(UUID tenantId, UUID unitId, String occupancyStatus, UUID actorId) {
            applied.add(unitId + "=" + occupancyStatus);
        }
    }

    private record Fixture(InMemoryLegacyStagingStore staging, LegacyImportService legacy,
                           InMemoryV1MigrationStore store, FakeDebitGateway debits,
                           FakeSituationGateway situations, V1ImportService service,
                           UUID batchId) {
    }

    /** Lote da P1.2 carregado e aplicado (unidades + prestações encenadas). */
    private static Fixture appliedBatch() {
        InMemoryLegacyStagingStore staging = new InMemoryLegacyStagingStore();
        LegacyImportService legacy = new LegacyImportService(staging, new FakeApplier(), new FakeBatchGateway());
        LegacyMapping piloto = LegacyMapping.parse(mappingJson("/legacy/mappings/piloto-v1.json"));
        LegacyImportService.LoadOutcome load = legacy.load(new LegacyImportService.LoadRequest(
                piloto, new CsvLegacyReader(PILOTO_CSV, ';'), "CSV", "carga-v1-1", UUID.randomUUID()));
        legacy.loadInstallments(load.batch().id(), piloto,
                new CsvLegacyReader(PILOTO_INSTALLMENTS_CSV, ';'));
        legacy.apply(load.batch().id(), UUID.randomUUID());
        InMemoryV1MigrationStore store = new InMemoryV1MigrationStore();
        FakeDebitGateway debits = new FakeDebitGateway();
        FakeSituationGateway situations = new FakeSituationGateway();
        V1ImportService service = new V1ImportService(staging, store, debits, situations);
        return new Fixture(staging, legacy, store, debits, situations, service, load.batch().id());
    }

    private static V1ImportService.ApplyConfig config() {
        return new V1ImportService.ApplyConfig("PARCELAMENTO_MIGRADO", "SALDO_MIGRADO", CUTOVER);
    }

    /** 120 meses sintéticos de LEG-1 (11/2016 a 10/2026) + ruído proposital. */
    private static String historyCsv() {
        StringBuilder csv = new StringBuilder("matricula_legada;competencia;consumo_m3;valor_faturado;vencimento\n");
        LocalDate month = LocalDate.of(2016, 11, 1);
        for (int i = 0; i < 120; i++) {
            csv.append("LEG-1;")
                    .append(String.format("%02d/%04d", month.getMonthValue(), month.getYear()))
                    .append(";12,000;150,00;10/")
                    .append(String.format("%02d/%04d", month.getMonthValue(), month.getYear()))
                    .append('\n');
            month = month.plusMonths(1);
        }
        csv.append("LEG-2;09/2026;8,500;100,00;10/10/2026\n");
        csv.append("LEG-1;10/2016;9,000;140,00;10/11/2016\n");   // fora da janela de 120 meses
        csv.append("LEG-1;10/2026;9,000;140,00;10/11/2026\n");   // duplicado do último mês válido
        csv.append("LEG-9;10/2026;9,000;140,00;10/11/2026\n");   // unidade desconhecida no lote
        csv.append("LEG-1;11/2026;9,000;140,00;10/12/2026\n");   // depois do corte (10/2026) — A3
        return csv.toString();
    }

    @Test
    @DisplayName("histórico: 120 meses encenam; fora da janela, depois do corte, duplicado e sem unidade são recusados")
    void historyLoadStagesWindowAndRejects() {
        Fixture f = appliedBatch();
        V1ImportService.LoadHistoryOutcome outcome = f.service().loadHistory(f.batchId(),
                V1HistoryMapping.v1(), new CsvLegacyReader(historyCsv(), ';'), CUTOVER);
        // Aceitos recalculados com a linha pós-corte incluída no fixture (A3):
        // 120 meses da janela (11/2016–10/2026) de LEG-1 + 1 linha válida de LEG-2 = 121.
        // A linha 11/2026 NÃO entra: é recusa nova, não aceito novo.
        assertThat(outcome.accepted()).isEqualTo(121);
        assertThat(outcome.rejectedRows()).hasSize(4);
        assertThat(outcome.rejectedRows()).anySatisfy(r -> assertThat(r.issues())
                .anyMatch(i -> i.code().equals("HISTORY_OUT_OF_WINDOW")));
        assertThat(outcome.rejectedRows()).anySatisfy(r -> assertThat(r.issues())
                .anyMatch(i -> i.code().equals("HISTORY_AFTER_CUTOVER")));
        assertThat(outcome.rejectedRows()).anySatisfy(r -> assertThat(r.issues())
                .anyMatch(i -> i.code().equals("DUPLICATE_HISTORY_MONTH")));
        assertThat(outcome.rejectedRows()).anySatisfy(r -> assertThat(r.issues())
                .anyMatch(i -> i.code().equals("REFERENCE_UNKNOWN")));
        // Recarga não duplica staging: tudo vira duplicado.
        V1ImportService.LoadHistoryOutcome again = f.service().loadHistory(f.batchId(),
                V1HistoryMapping.v1(), new CsvLegacyReader(historyCsv(), ';'), CUTOVER);
        assertThat(again.accepted()).isZero();
    }

    @Test
    @DisplayName("simulação não cria débito, não aplica histórico nem situação")
    void simulationWritesNothing() {
        Fixture f = appliedBatch();
        f.service().loadHistory(f.batchId(), V1HistoryMapping.v1(),
                new CsvLegacyReader(historyCsv(), ';'), CUTOVER);
        V1ImportService.Simulation simulation = f.service().simulate(f.batchId());
        assertThat(simulation.appliedUnits()).isEqualTo(2);
        assertThat(simulation.historyToApply()).isEqualTo(121);
        assertThat(simulation.installmentsToConvert()).isEqualTo(2);
        assertThat(simulation.vacantUnits()).isZero();
        assertThat(simulation.occupiedUnits()).isEqualTo(2);
        assertThat(simulation.conference().allMatch()).isTrue();
        assertThat(f.debits().installments).isEmpty();
        assertThat(f.debits().residuals).isEmpty();
        assertThat(f.situations().applied).isEmpty();
        assertThat(f.store().installmentDebits()).isEmpty();
    }

    @Test
    @DisplayName("prestações viram débitos um a um, com origem parcelamento e referência ao acordo do legado")
    void installmentsBecomeDebitsOneByOne() {
        Fixture f = appliedBatch();
        V1ImportService.ApplyOutcome outcome = f.service().apply(TENANT, f.batchId(), UUID.randomUUID(), config());
        assertThat(outcome.installmentDebitsCreated()).isEqualTo(2);
        assertThat(f.debits().installments).hasSize(2);
        assertThat(f.debits().installments).allSatisfy(d -> {
            assertThat(d.legacyAgreementRef()).isEqualTo("ACORDO-7");
            assertThat(d.amountCents()).isEqualTo(20000L);
            // A6: cada prestação vira débito de UMA parcela, ponta a ponta.
            assertThat(d.installments()).isEqualTo(1);
            assertThat(d.agreementId()).isNotNull();
            assertThat(d.financingTerm()).isEqualTo(2);
        });
        assertThat(f.debits().installmentCounts).containsExactly(1, 1);
        assertThat(f.debits().installments).extracting(InstallmentDebitGateway.InstallmentDebit::installmentNo)
                .containsExactlyInAnyOrder(1, 2);
        assertThat(f.debits().installments).extracting(InstallmentDebitGateway.InstallmentDebit::collectionMonth)
                .containsExactly(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1));
        // O mesmo acordo migrado nos dois débitos: source_id estável.
        assertThat(f.debits().installments.get(0).agreementId())
                .isEqualTo(f.debits().installments.get(1).agreementId());
        assertThat(f.store().installmentDebits()).hasSize(2);
    }

    @Test
    @DisplayName("saldo por matrícula confere com as prestações incluídas: débitos somam o saldo declarado")
    void balancePerMatriculaMatchesLegacyWithInstallments() {
        Fixture f = appliedBatch();
        V1ImportService.ApplyOutcome outcome = f.service().apply(TENANT, f.batchId(), UUID.randomUUID(), config());
        assertThat(outcome.conference().allMatch()).isTrue();
        // LEG-1: declarado 123456 = prestações 40000 + resíduo 83456.
        // LEG-2: declarado 10000 = resíduo 10000. Total migrado = total declarado.
        long created = f.debits().installments.stream()
                .mapToLong(InstallmentDebitGateway.InstallmentDebit::amountCents).sum()
                + f.debits().residuals.stream()
                .mapToLong(InstallmentDebitGateway.ResidualDebit::amountCents).sum();
        assertThat(created).isEqualTo(133456L);
        assertThat(outcome.conference().legacyTotalCents()).isEqualTo(133456L);
        assertThat(outcome.conference().migratedTotalCents()).isEqualTo(133456L);
        assertThat(f.debits().residuals).extracting(InstallmentDebitGateway.ResidualDebit::amountCents)
                .containsExactlyInAnyOrder(83456L, 10000L);
        assertThat(outcome.residualDebitsCreated()).isEqualTo(2);
        assertThat(outcome.unitsSkippedByConference()).isZero();
    }

    @Test
    @DisplayName("reaplicação é idempotente: nenhum débito, histórico ou situação duplica")
    void reapplyIsIdempotent() {
        Fixture f = appliedBatch();
        f.service().loadHistory(f.batchId(), V1HistoryMapping.v1(),
                new CsvLegacyReader(historyCsv(), ';'), CUTOVER);
        V1ImportService.ApplyOutcome first = f.service().apply(TENANT, f.batchId(), UUID.randomUUID(), config());
        assertThat(first.historyApplied()).isEqualTo(121);
        V1ImportService.ApplyOutcome second = f.service().apply(TENANT, f.batchId(), UUID.randomUUID(), config());
        assertThat(second.installmentDebitsCreated()).isZero();
        assertThat(second.residualDebitsCreated()).isZero();
        assertThat(second.historyApplied()).isZero();
        assertThat(second.situationsApplied()).isZero();
        assertThat(second.alreadyFullyApplied()).isTrue();
        assertThat(f.debits().installments).hasSize(2);
        assertThat(f.debits().residuals).hasSize(2);
    }

    @Test
    @DisplayName("histórico aplicado: os 120 meses de LEG-1 e o mês de LEG-2, nada da unidade recusada")
    void historyAppliesOnlyForAppliedUnits() {
        Fixture f = appliedBatch();
        f.service().loadHistory(f.batchId(), V1HistoryMapping.v1(),
                new CsvLegacyReader(historyCsv(), ';'), CUTOVER);
        V1ImportService.ApplyOutcome outcome = f.service().apply(TENANT, f.batchId(), UUID.randomUUID(), config());
        assertThat(outcome.historyApplied()).isEqualTo(121);
        long leg1 = f.store().listHistory(f.batchId()).stream()
                .filter(r -> r.unitLegacyKey().equals("LEG-1") && r.status() == V1HistoryRecord.Status.APPLIED)
                .count();
        assertThat(leg1).isEqualTo(120);
    }

    @Test
    @DisplayName("CAD-68: unidade sem usuário vigente entra VACANT; com usuário vigente, OCCUPIED")
    void vacantRuleFromP141() {
        StagedUnit withoutHolder = stagedUnit("LEG-V", null, "NONE", 0L);
        StagedUnit withHolder = stagedUnit("LEG-O", "Fulano Sintético", "CPF", 0L);
        assertThat(V1UnitSituation.occupancyStatusFor(withoutHolder)).isEqualTo("VACANT");
        assertThat(V1UnitSituation.occupancyStatusFor(withHolder)).isEqualTo("OCCUPIED");
        assertThat(V1UnitSituation.hasCurrentHolder(withoutHolder)).isFalse();
        assertThat(V1UnitSituation.hasCurrentHolder(withHolder)).isTrue();
    }

    @Test
    @DisplayName("conferência reprova matrícula cujas prestações somam mais que o saldo declarado")
    void conferenceFailsWhenInstallmentsExceedBalance() {
        StagedUnit unit = stagedUnit("LEG-X", "Fulano Sintético", "CPF", 30000L);
        List<StagedInstallment> installments = List.of(
                new StagedInstallment(UUID.randomUUID(), unit.batchId(), unit.id(),
                        "LEG-X", "ACORDO-1", 1, LocalDate.of(2026, 4, 10), 20000L),
                new StagedInstallment(UUID.randomUUID(), unit.batchId(), unit.id(),
                        "LEG-X", "ACORDO-1", 2, LocalDate.of(2026, 5, 10), 20000L));
        V1BalanceConference.Report report = V1BalanceConference.confer(List.of(unit), installments);
        assertThat(report.allMatch()).isFalse();
        assertThat(report.failures()).singleElement().satisfies(row -> {
            assertThat(row.failureCode()).isEqualTo("INSTALLMENTS_EXCEED_BALANCE");
            assertThat(row.legacyBalanceCents()).isEqualTo(30000L);
            assertThat(row.installmentsCents()).isEqualTo(40000L);
        });
    }

    // ------------------------------------------------------------------
    // Correções pós-revisão (A2, A4, A6): fixture manual — unidades já
    // APLICADAS montadas direto no staging em memória, sem depender da
    // carga CSV da P1.2. Só usa assinaturas que o LegacyImportService da
    // P1.2 e esta entrega já exercem (createBatch/insertUnits/
    // insertInstallments e os construtores de StagedUnit/StagedInstallment).
    // ------------------------------------------------------------------

    private record ManualFixture(InMemoryLegacyStagingStore staging, InMemoryV1MigrationStore store,
                                 FakeDebitGateway debits, FakeSituationGateway situations,
                                 V1ImportService service, UUID batchId) {
    }

    private static ManualFixture emptyManualFixture() {
        InMemoryLegacyStagingStore staging = new InMemoryLegacyStagingStore();
        LegacyBatch batch = staging.createBatch("PILOTO", "manual-p2.10", "1",
                "carga-manual-" + UUID.randomUUID(), "CSV", UUID.randomUUID());
        InMemoryV1MigrationStore store = new InMemoryV1MigrationStore();
        FakeDebitGateway debits = new FakeDebitGateway();
        FakeSituationGateway situations = new FakeSituationGateway();
        V1ImportService service = new V1ImportService(staging, store, debits, situations);
        return new ManualFixture(staging, store, debits, situations, service, batch.id());
    }

    private static StagedUnit appliedUnit(UUID batchId, String legacyKey, String holderName,
                                          String documentKind, Long balanceCents) {
        return new StagedUnit(UUID.randomUUID(), batchId, 1, legacyKey,
                "910099", null, "1", "10", "5", "20", "RES",
                10L, 0L, 1L, balanceCents, LocalDate.of(2020, 3, 5),
                holderName, documentKind, null, null,
                new ArrayList<>(), StagedUnit.Status.APPLIED, UUID.randomUUID());
    }

    private static void stage(ManualFixture f, List<StagedUnit> units,
                              List<StagedInstallment> installments) {
        f.staging().insertUnits(f.batchId(), units);
        if (!installments.isEmpty()) {
            f.staging().insertInstallments(f.batchId(), installments);
        }
    }

    @Test
    @DisplayName("A2 aplicação: matrícula divergente (prestações > saldo) NÃO gera débito nenhum")
    void divergentMatriculaCreatesNoDebitOnApply() {
        ManualFixture f = emptyManualFixture();
        StagedUnit unit = appliedUnit(f.batchId(), "LEG-D", "Fulano Sintético", "CPF", 30000L);
        stage(f, List.of(unit), List.of(
                new StagedInstallment(UUID.randomUUID(), f.batchId(), unit.id(),
                        "LEG-D", "ACORDO-1", 1, LocalDate.of(2026, 4, 10), 20000L),
                new StagedInstallment(UUID.randomUUID(), f.batchId(), unit.id(),
                        "LEG-D", "ACORDO-1", 2, LocalDate.of(2026, 5, 10), 20000L)));
        V1ImportService.ApplyOutcome outcome = f.service().apply(TENANT, f.batchId(), UUID.randomUUID(), config());
        assertThat(outcome.unitsSkippedByConference()).isEqualTo(1);
        assertThat(outcome.installmentDebitsCreated()).isZero();
        assertThat(outcome.residualDebitsCreated()).isZero();
        assertThat(f.debits().installments).isEmpty();
        assertThat(f.debits().residuals).isEmpty();
        assertThat(f.store().installmentDebits()).isEmpty();
        assertThat(outcome.conference().failures()).singleElement().satisfies(row -> {
            assertThat(row.failureCode()).isEqualTo("INSTALLMENTS_EXCEED_BALANCE");
            assertThat(row.legacyBalanceCents()).isEqualTo(30000L);
            assertThat(row.installmentsCents()).isEqualTo(40000L);
        });
    }

    @Test
    @DisplayName("A2 regra: saldo ausente é BALANCE_MISSING e saldo negativo é BALANCE_NEGATIVE")
    void conferenceMissingAndNegativeBalance() {
        V1BalanceConference.Report report = V1BalanceConference.confer(List.of(
                stagedUnitWithBalance("LEG-M", null),
                stagedUnitWithBalance("LEG-N", -5000L)), List.of());
        assertThat(report.allMatch()).isFalse();
        assertThat(report.failures()).hasSize(2);
        assertThat(report.failures()).anySatisfy(row -> {
            assertThat(row.legacyKey()).isEqualTo("LEG-M");
            assertThat(row.failureCode()).isEqualTo("BALANCE_MISSING");
            assertThat(row.legacyBalanceCents()).isNull();
        });
        assertThat(report.failures()).anySatisfy(row -> {
            assertThat(row.legacyKey()).isEqualTo("LEG-N");
            assertThat(row.failureCode()).isEqualTo("BALANCE_NEGATIVE");
            assertThat(row.legacyBalanceCents()).isEqualTo(-5000L);
        });
    }

    @Test
    @DisplayName("A2 aplicação: saldo ausente ou negativo NÃO gera débito nenhum")
    void missingOrNegativeBalanceCreatesNoDebitOnApply() {
        ManualFixture f = emptyManualFixture();
        stage(f, List.of(
                appliedUnit(f.batchId(), "LEG-M", "Fulano Sintético", "CPF", null),
                appliedUnit(f.batchId(), "LEG-N", "Fulano Sintético", "CPF", -5000L)), List.of());
        V1ImportService.ApplyOutcome outcome = f.service().apply(TENANT, f.batchId(), UUID.randomUUID(), config());
        assertThat(outcome.unitsSkippedByConference()).isEqualTo(2);
        assertThat(outcome.installmentDebitsCreated()).isZero();
        assertThat(outcome.residualDebitsCreated()).isZero();
        assertThat(f.debits().installments).isEmpty();
        assertThat(f.debits().residuals).isEmpty();
        assertThat(outcome.conference().failures())
                .extracting(V1BalanceConference.MatriculaConference::failureCode)
                .containsExactlyInAnyOrder("BALANCE_MISSING", "BALANCE_NEGATIVE");
    }

    @Test
    @DisplayName("A4: chave legada de 100 caracteres gera referência de resíduo ≤100 e o débito é criado")
    void longLegacyKeyResidualReferenceFitsColumn() {
        String longKey = "K".repeat(100);
        UUID batchIdProbe = UUID.randomUUID();
        // A forma natural batchId + ":" + legacyKey estouraria a VARCHAR(100) da V4_4.
        assertThat((batchIdProbe + ":" + longKey).length()).isEqualTo(137);
        ManualFixture f = emptyManualFixture();
        stage(f, List.of(appliedUnit(f.batchId(), longKey, "Fulano Sintético", "CPF", 10000L)), List.of());
        V1ImportService.ApplyOutcome outcome = f.service().apply(TENANT, f.batchId(), UUID.randomUUID(), config());
        assertThat(outcome.residualDebitsCreated()).isEqualTo(1);
        assertThat(f.debits().residuals).singleElement().satisfies(d -> {
            assertThat(d.amountCents()).isEqualTo(10000L);
            assertThat(d.legacyReference().length()).isLessThanOrEqualTo(100);
            assertThat(d.legacyReference()).startsWith(f.batchId() + ":");
        });
    }

    @Test
    @DisplayName("A4: referência curta fica intacta; longa compacta com hash da referência completa, sem passar de 100")
    void legacyReferencesCompaction() {
        UUID batchId = UUID.randomUUID();
        assertThat(LegacyReferences.residualReference(batchId, "LEG-1"))
                .isEqualTo(batchId + ":LEG-1");
        String compacted = LegacyReferences.residualReference(batchId, "K".repeat(100));
        assertThat(compacted.length()).isEqualTo(100);
        assertThat(compacted).startsWith(batchId + ":");
        // Mesmo prefixo de 54 chars, chave diferente: o hash da referência completa distingue.
        assertThat(LegacyReferences.residualReference(batchId, "K".repeat(99) + "X"))
                .isNotEqualTo(compacted);
        // Prestação: referência do acordo intacta quando cabe (origem já é VARCHAR(100)).
        assertThat(LegacyReferences.installmentReference("ACORDO-7")).isEqualTo("ACORDO-7");
        String longAgreement = "A".repeat(120);
        String compactedAgreement = LegacyReferences.installmentReference(longAgreement);
        assertThat(compactedAgreement.length()).isLessThanOrEqualTo(100);
        assertThat(compactedAgreement).startsWith(longAgreement.substring(0, 91));
    }

    @Test
    @DisplayName("A6 CAD-68 ponta-a-ponta: unidade aplicada sem titular grava VACANT; com titular, OCCUPIED")
    void vacantAppliedEndToEnd() {
        ManualFixture f = emptyManualFixture();
        StagedUnit vacant = appliedUnit(f.batchId(), "LEG-V", null, "NONE", 0L);
        StagedUnit occupied = appliedUnit(f.batchId(), "LEG-O", "Fulano Sintético", "CPF", 0L);
        stage(f, List.of(vacant, occupied), List.of());
        V1ImportService.ApplyOutcome outcome = f.service().apply(TENANT, f.batchId(), UUID.randomUUID(), config());
        assertThat(outcome.situationsApplied()).isEqualTo(2);
        assertThat(f.situations().applied).containsExactlyInAnyOrder(
                vacant.appliedUnitId() + "=VACANT",
                occupied.appliedUnitId() + "=OCCUPIED");
        assertThat(f.store().situations()).containsValue("VACANT");
        assertThat(f.store().situations()).containsValue("OCCUPIED");
    }

    private static StagedUnit stagedUnitWithBalance(String legacyKey, Long balanceCents) {
        return new StagedUnit(UUID.randomUUID(), UUID.randomUUID(), 1, legacyKey,
                "910099", null, "1", "10", "5", "20", "RES",
                10L, 0L, 1L, balanceCents, LocalDate.of(2020, 3, 5),
                "Fulano Sintético", "CPF", null, null,
                new ArrayList<>(), StagedUnit.Status.PENDING, null);
    }

    private static StagedUnit stagedUnit(String legacyKey, String holderName, String documentKind, long balanceCents) {
        return new StagedUnit(UUID.randomUUID(), UUID.randomUUID(), 1, legacyKey,
                "910099", null, "1", "10", "5", "20", "RES",
                10L, 0L, 1L, balanceCents, LocalDate.of(2020, 3, 5),
                holderName, documentKind, null, null,
                new ArrayList<>(), StagedUnit.Status.PENDING, null);
    }
}
