package br.com.ziipsan.commercial.billing.cycle;

import br.com.ziipsan.commercial.billing.bill.BillStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static br.com.ziipsan.commercial.billing.cycle.CycleCommandRule.CommandDecision;
import static br.com.ziipsan.commercial.billing.cycle.CycleCommandRule.GenerationDecision;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Casos da regra pura do ciclo (P2.5/F1), conferidos a mao contra o aceite:
 * precedencia, rota nao transmitida, referencia fechada, repeticao e a
 * composicao com BillIdempotency. O catalogo do retrato e o do produto
 * (ProductActivityCatalog): leitura -> transmissao -> faturamento ->
 * fechamento, todos obrigatorios, leitura e transmissao repetiveis.
 */
class CycleCommandRuleTest {

    private static final UUID GROUP_A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID GROUP_B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    private static List<CycleCommandRule.ActivityView> catalog() {
        return ProductActivityCatalog.definitions().stream()
                .map(d -> new CycleCommandRule.ActivityView(d.code(), d.mandatory(), d.repeatable(), d.predecessor()))
                .toList();
    }

    private static CycleCommandRule.ItemView item(UUID group, CycleActivityCode activity, ScheduleItemStatus status) {
        return new CycleCommandRule.ItemView(group, activity, status, 1);
    }

    private static CycleCommandRule.Snapshot open(CycleCommandRule.ItemView... items) {
        return new CycleCommandRule.Snapshot(false, catalog(), List.of(items));
    }

    // ------------------------------------------------------------------ comando

    @Test
    @DisplayName("primeira atividade (leitura, sem precedente) pendente: comando admitido")
    void firstActivityAllowed() {
        CycleCommandRule.Snapshot s = open(item(GROUP_A, CycleActivityCode.READING, ScheduleItemStatus.PENDING));

        assertThat(CycleCommandRule.evaluateCommand(s, GROUP_A, CycleActivityCode.READING))
                .isEqualTo(CommandDecision.ALLOWED);
    }

    @Test
    @DisplayName("precedencia: comandar a transmissao com a leitura pendente ou comandada recusa")
    void outOfOrderCommandIsRefused() {
        CycleCommandRule.Snapshot pending = open(
                item(GROUP_A, CycleActivityCode.READING, ScheduleItemStatus.PENDING),
                item(GROUP_A, CycleActivityCode.TRANSMISSION, ScheduleItemStatus.PENDING));
        assertThat(CycleCommandRule.evaluateCommand(pending, GROUP_A, CycleActivityCode.TRANSMISSION))
                .isEqualTo(CommandDecision.PREDECESSOR_NOT_DONE);

        CycleCommandRule.Snapshot commanded = open(
                item(GROUP_A, CycleActivityCode.READING, ScheduleItemStatus.COMMANDED),
                item(GROUP_A, CycleActivityCode.TRANSMISSION, ScheduleItemStatus.PENDING));
        assertThat(CycleCommandRule.evaluateCommand(commanded, GROUP_A, CycleActivityCode.TRANSMISSION))
                .isEqualTo(CommandDecision.PREDECESSOR_NOT_DONE);
    }

    @Test
    @DisplayName("precedencia: fechamento com o faturamento pendente recusa; com ele realizado, admite")
    void closingNeedsBillingDone() {
        CycleCommandRule.Snapshot billingPending = open(
                item(GROUP_A, CycleActivityCode.BILLING, ScheduleItemStatus.PENDING),
                item(GROUP_A, CycleActivityCode.CLOSING, ScheduleItemStatus.PENDING));
        assertThat(CycleCommandRule.evaluateCommand(billingPending, GROUP_A, CycleActivityCode.CLOSING))
                .isEqualTo(CommandDecision.PREDECESSOR_NOT_DONE);

        CycleCommandRule.Snapshot billingDone = open(
                item(GROUP_A, CycleActivityCode.BILLING, ScheduleItemStatus.DONE),
                item(GROUP_A, CycleActivityCode.CLOSING, ScheduleItemStatus.PENDING));
        assertThat(CycleCommandRule.evaluateCommand(billingDone, GROUP_A, CycleActivityCode.CLOSING))
                .isEqualTo(CommandDecision.ALLOWED);
    }

    @Test
    @DisplayName("rota nao transmitida: faturamento com a transmissao pendente, comandada ou ausente recusa como ROUTE_NOT_TRANSMITTED")
    void billingRefusedWhenRouteNotTransmitted() {
        CycleCommandRule.Snapshot pending = open(
                item(GROUP_A, CycleActivityCode.READING, ScheduleItemStatus.DONE),
                item(GROUP_A, CycleActivityCode.TRANSMISSION, ScheduleItemStatus.PENDING),
                item(GROUP_A, CycleActivityCode.BILLING, ScheduleItemStatus.PENDING));
        assertThat(CycleCommandRule.evaluateCommand(pending, GROUP_A, CycleActivityCode.BILLING))
                .isEqualTo(CommandDecision.ROUTE_NOT_TRANSMITTED);

        CycleCommandRule.Snapshot commanded = open(
                item(GROUP_A, CycleActivityCode.READING, ScheduleItemStatus.DONE),
                item(GROUP_A, CycleActivityCode.TRANSMISSION, ScheduleItemStatus.COMMANDED),
                item(GROUP_A, CycleActivityCode.BILLING, ScheduleItemStatus.PENDING));
        assertThat(CycleCommandRule.evaluateCommand(commanded, GROUP_A, CycleActivityCode.BILLING))
                .isEqualTo(CommandDecision.ROUTE_NOT_TRANSMITTED);

        CycleCommandRule.Snapshot missing = open(
                item(GROUP_A, CycleActivityCode.READING, ScheduleItemStatus.DONE),
                item(GROUP_A, CycleActivityCode.BILLING, ScheduleItemStatus.PENDING));
        assertThat(CycleCommandRule.evaluateCommand(missing, GROUP_A, CycleActivityCode.BILLING))
                .isEqualTo(CommandDecision.ROUTE_NOT_TRANSMITTED);
    }

    @Test
    @DisplayName("com a rota transmitida (transmissao realizada), o faturamento e admitido")
    void billingAllowedAfterTransmission() {
        CycleCommandRule.Snapshot s = open(
                item(GROUP_A, CycleActivityCode.READING, ScheduleItemStatus.DONE),
                item(GROUP_A, CycleActivityCode.TRANSMISSION, ScheduleItemStatus.DONE),
                item(GROUP_A, CycleActivityCode.BILLING, ScheduleItemStatus.PENDING));

        assertThat(CycleCommandRule.evaluateCommand(s, GROUP_A, CycleActivityCode.BILLING))
                .isEqualTo(CommandDecision.ALLOWED);
    }

    @Test
    @DisplayName("referencia fechada: nenhum comando e admitido, nem o da primeira atividade")
    void closedReferenceRefusesEveryCommand() {
        CycleCommandRule.Snapshot closed = new CycleCommandRule.Snapshot(true, catalog(), List.of(
                item(GROUP_A, CycleActivityCode.READING, ScheduleItemStatus.PENDING)));

        assertThat(CycleCommandRule.evaluateCommand(closed, GROUP_A, CycleActivityCode.READING))
                .isEqualTo(CommandDecision.REFERENCE_CLOSED);
    }

    @Test
    @DisplayName("repeticao: faturamento realizado nao comanda de novo; leitura realizada comanda como nova ocorrencia")
    void repetitionFollowsTheCatalog() {
        CycleCommandRule.Snapshot billingDone = open(
                item(GROUP_A, CycleActivityCode.BILLING, ScheduleItemStatus.DONE));
        assertThat(CycleCommandRule.evaluateCommand(billingDone, GROUP_A, CycleActivityCode.BILLING))
                .isEqualTo(CommandDecision.ALREADY_DONE_NOT_REPEATABLE);

        CycleCommandRule.Snapshot readingDone = open(
                item(GROUP_A, CycleActivityCode.READING, ScheduleItemStatus.DONE));
        assertThat(CycleCommandRule.evaluateCommand(readingDone, GROUP_A, CycleActivityCode.READING))
                .isEqualTo(CommandDecision.NEW_OCCURRENCE_ALLOWED);

        // A ocorrencia que vale e a mais recente: releitura comandada trava novo comando.
        CycleCommandRule.Snapshot rereadCommanded = open(
                item(GROUP_A, CycleActivityCode.READING, ScheduleItemStatus.DONE),
                new CycleCommandRule.ItemView(GROUP_A, CycleActivityCode.READING, ScheduleItemStatus.COMMANDED, 2));
        assertThat(CycleCommandRule.evaluateCommand(rereadCommanded, GROUP_A, CycleActivityCode.READING))
                .isEqualTo(CommandDecision.ALREADY_COMMANDED);
    }

    @Test
    @DisplayName("ausencias sao recusa explicita: sem instancia e sem entrada no catalogo")
    void absencesAreExplicitRefusals() {
        CycleCommandRule.Snapshot empty = open();
        assertThat(CycleCommandRule.evaluateCommand(empty, GROUP_A, CycleActivityCode.READING))
                .isEqualTo(CommandDecision.NO_SUCH_INSTANCE);

        CycleCommandRule.Snapshot noCatalog = new CycleCommandRule.Snapshot(false, List.of(), List.of(
                item(GROUP_A, CycleActivityCode.READING, ScheduleItemStatus.PENDING)));
        assertThat(CycleCommandRule.evaluateCommand(noCatalog, GROUP_A, CycleActivityCode.READING))
                .isEqualTo(CommandDecision.CATALOG_ENTRY_MISSING);
    }

    // --------------------------------------------------------------- fechamento

    @Test
    @DisplayName("fechamento: todos os grupos com todas as obrigatorias realizadas admite")
    void closeAllowedWhenEveryGroupIsDone() {
        CycleCommandRule.Snapshot s = open(
                item(GROUP_A, CycleActivityCode.READING, ScheduleItemStatus.DONE),
                item(GROUP_A, CycleActivityCode.TRANSMISSION, ScheduleItemStatus.DONE),
                item(GROUP_A, CycleActivityCode.BILLING, ScheduleItemStatus.DONE),
                item(GROUP_A, CycleActivityCode.CLOSING, ScheduleItemStatus.DONE),
                item(GROUP_B, CycleActivityCode.READING, ScheduleItemStatus.DONE),
                item(GROUP_B, CycleActivityCode.TRANSMISSION, ScheduleItemStatus.DONE),
                item(GROUP_B, CycleActivityCode.BILLING, ScheduleItemStatus.DONE),
                item(GROUP_B, CycleActivityCode.CLOSING, ScheduleItemStatus.DONE));

        CycleCommandRule.CloseEvaluation evaluation = CycleCommandRule.evaluateClose(s);
        assertThat(evaluation.decision()).isEqualTo(CycleCommandRule.CloseDecision.ALLOWED);
        assertThat(evaluation.pending()).isEmpty();
    }

    @Test
    @DisplayName("fechamento: um grupo com o faturamento pendente recusa e nomeia a pendencia")
    void closeRefusedWhileAGroupIsPending() {
        CycleCommandRule.Snapshot s = open(
                item(GROUP_A, CycleActivityCode.READING, ScheduleItemStatus.DONE),
                item(GROUP_A, CycleActivityCode.TRANSMISSION, ScheduleItemStatus.DONE),
                item(GROUP_A, CycleActivityCode.BILLING, ScheduleItemStatus.DONE),
                item(GROUP_A, CycleActivityCode.CLOSING, ScheduleItemStatus.DONE),
                item(GROUP_B, CycleActivityCode.READING, ScheduleItemStatus.DONE),
                item(GROUP_B, CycleActivityCode.TRANSMISSION, ScheduleItemStatus.DONE),
                item(GROUP_B, CycleActivityCode.BILLING, ScheduleItemStatus.PENDING),
                item(GROUP_B, CycleActivityCode.CLOSING, ScheduleItemStatus.PENDING));

        CycleCommandRule.CloseEvaluation evaluation = CycleCommandRule.evaluateClose(s);
        assertThat(evaluation.decision()).isEqualTo(CycleCommandRule.CloseDecision.MANDATORY_PENDING);
        assertThat(evaluation.pending()).containsExactly(
                "group=" + GROUP_B + " activity=BILLING",
                "group=" + GROUP_B + " activity=CLOSING");
    }

    @Test
    @DisplayName("fechamento: referencia ja fechada e cronograma vazio recusam")
    void closeRefusals() {
        CycleCommandRule.Snapshot closed = new CycleCommandRule.Snapshot(true, catalog(), List.of(
                item(GROUP_A, CycleActivityCode.READING, ScheduleItemStatus.DONE)));
        assertThat(CycleCommandRule.evaluateClose(closed).decision())
                .isEqualTo(CycleCommandRule.CloseDecision.ALREADY_CLOSED);

        CycleCommandRule.CloseEvaluation empty = CycleCommandRule.evaluateClose(open());
        assertThat(empty.decision()).isEqualTo(CycleCommandRule.CloseDecision.MANDATORY_PENDING);
        assertThat(empty.pending()).containsExactly("NO_SCHEDULE_ITEMS");
    }

    // ------------------------------------------------------------------ geracao

    @Test
    @DisplayName("geracao: referencia fechada recusa mesmo sem conta nenhuma")
    void generationRefusedInClosedReference() {
        assertThat(CycleCommandRule.evaluateGeneration(true, List.of()))
                .isEqualTo(GenerationDecision.REFERENCE_CLOSED);
        assertThat(CycleCommandRule.evaluateGeneration(true, List.of(BillStatus.NORMAL)))
                .isEqualTo(GenerationDecision.REFERENCE_CLOSED);
    }

    @Test
    @DisplayName("ciclo repetido nao gera segunda conta: conta viva existente recusa (BillIdempotency)")
    void repeatedCycleDoesNotGenerateSecondBill() {
        assertThat(CycleCommandRule.evaluateGeneration(false, List.of(BillStatus.NORMAL)))
                .isEqualTo(GenerationDecision.ALREADY_BILLED);
        assertThat(CycleCommandRule.evaluateGeneration(false, List.of(BillStatus.PAID)))
                .isEqualTo(GenerationDecision.ALREADY_BILLED);
        // Pre-faturada convivendo com uma normal: duas vivas, nao e o caso de substituicao.
        assertThat(CycleCommandRule.evaluateGeneration(false, List.of(BillStatus.PRE_BILLED, BillStatus.NORMAL)))
                .isEqualTo(GenerationDecision.ALREADY_BILLED);
    }

    @Test
    @DisplayName("geracao admitida: sem conta, so com contas nao vivas, ou substituindo a pre-faturada")
    void generationAllowedCases() {
        assertThat(CycleCommandRule.evaluateGeneration(false, List.of()))
                .isEqualTo(GenerationDecision.CREATE);
        assertThat(CycleCommandRule.evaluateGeneration(false,
                List.of(BillStatus.CANCELLED, BillStatus.PROCESSING_ERROR)))
                .isEqualTo(GenerationDecision.CREATE);
        assertThat(CycleCommandRule.evaluateGeneration(false, List.of(BillStatus.PRE_BILLED)))
                .isEqualTo(GenerationDecision.REPLACE_PRE_BILLED);
    }
}
