package br.com.ziipsan.core.certification.lims17025;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes do pacote de validacao do sistema por versao (ED-51-PD08;
 * PD-08). Massa SINTETICA declarada como fixture: versao
 * {@code vTESTE}, itens {@code ITEM_A}/{@code ITEM_B}/{@code ITEM_C} e
 * evidencias {@code EV-FIXTURE-*} — nao sao versao, checklist nem
 * evidencia de nenhum sistema ou laboratorio real; o conteudo real do
 * plano e declarado pelo tenant/laboratorio.
 */
class SystemValidationTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000151");
    private static final UUID PROPOSER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID ACCEPTER = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final Instant T0 = Instant.parse("2026-06-15T10:00:00Z");
    private static final String PLANNER = "PLANEJADOR-1";
    private static final String EXECUTOR = "EXECUTOR-1";
    private static final String EXECUTOR_2 = "EXECUTOR-2";
    private static final String APPROVER = "APROVADOR-1";

    private static Lims17025Activation enabled() {
        return Lims17025Activation.enabled("Escopo sintetico de fixture", PROPOSER, ACCEPTER);
    }

    private static List<SystemValidationPlanItem> plan() {
        return List.of(
                new SystemValidationPlanItem("ITEM_A", "Item sintetico A do plano"),
                new SystemValidationPlanItem("ITEM_B", "Item sintetico B do plano"),
                new SystemValidationPlanItem("ITEM_C", "Item sintetico C do plano"));
    }

    private static SystemValidation openDossier() {
        return SystemValidation.open(enabled(), UUID.randomUUID(), TENANT, UUID.randomUUID(),
                "vTESTE", plan(), PLANNER, T0);
    }

    private static SystemValidationExecution exec(String itemKey,
                                                  SystemValidationExecution.Outcome outcome,
                                                  String executedBy) {
        return new SystemValidationExecution(itemKey, outcome,
                "EV-FIXTURE-" + itemKey, executedBy, T0.plusSeconds(600), null);
    }

    private static SystemValidation concludedDossier() {
        SystemValidation v = openDossier();
        v.recordExecution(exec("ITEM_A", SystemValidationExecution.Outcome.PASSED, EXECUTOR));
        v.recordExecution(exec("ITEM_B", SystemValidationExecution.Outcome.PASSED, EXECUTOR_2));
        v.recordExecution(exec("ITEM_C", SystemValidationExecution.Outcome.PASSED, EXECUTOR));
        v.conclude(APPROVER, T0.plusSeconds(3600));
        return v;
    }

    // (a) Regua ligada/desligada: nascimento recusado com a norma desligada.
    @Test void disabledRefusesBirth() {
        Lims17025Activation off = Lims17025Activation.disabled();
        assertThrows(IllegalStateException.class, () -> SystemValidation.open(off,
                UUID.randomUUID(), TENANT, UUID.randomUUID(), "vTESTE", plan(), PLANNER, T0));
    }

    @Test void enabledLetsBirthHappen() {
        SystemValidation v = openDossier();
        assertEquals(SystemValidation.Status.OPEN, v.status());
        assertEquals("vTESTE", v.systemVersion());
        assertEquals(3, v.plan().size());
        assertEquals(1, v.revision());
        assertFalse(v.isComplete());
        assertTrue(v.outcome().isEmpty());
    }

    // (b) Tudo declarado: sem versao, sem plano ou com item duplicado, nao nasce.
    @Test void declaredDataIsMandatory() {
        assertThrows(IllegalArgumentException.class, () -> SystemValidation.open(enabled(),
                UUID.randomUUID(), TENANT, UUID.randomUUID(), " ", plan(), PLANNER, T0));
        assertThrows(IllegalArgumentException.class, () -> SystemValidation.open(enabled(),
                UUID.randomUUID(), TENANT, UUID.randomUUID(), "vTESTE", List.of(), PLANNER, T0));
        assertThrows(IllegalArgumentException.class, () -> SystemValidation.open(enabled(),
                UUID.randomUUID(), TENANT, UUID.randomUUID(), "vTESTE",
                List.of(new SystemValidationPlanItem("ITEM_A", "a"),
                        new SystemValidationPlanItem("ITEM_A", "duplicado")), PLANNER, T0));
        // execucao sem referencia de evidencia nao se regista
        assertThrows(IllegalArgumentException.class, () -> new SystemValidationExecution(
                "ITEM_A", SystemValidationExecution.Outcome.PASSED, " ", EXECUTOR, T0, null));
    }

    // (c) Segregacao: quem executou ou declarou nao aprova/conclui.
    @Test void segregationExecutorAndPlannerCannotConclude() {
        SystemValidation v = openDossier();
        v.recordExecution(exec("ITEM_A", SystemValidationExecution.Outcome.PASSED, EXECUTOR));
        v.recordExecution(exec("ITEM_B", SystemValidationExecution.Outcome.PASSED, EXECUTOR_2));
        v.recordExecution(exec("ITEM_C", SystemValidationExecution.Outcome.PASSED, EXECUTOR));
        // o executor de itens nao conclui
        assertThrows(IllegalArgumentException.class, () -> v.conclude(EXECUTOR, T0.plusSeconds(3600)));
        assertThrows(IllegalArgumentException.class, () -> v.conclude(EXECUTOR_2, T0.plusSeconds(3600)));
        // quem declarou o plano tambem nao conclui
        assertThrows(IllegalArgumentException.class, () -> v.conclude(PLANNER, T0.plusSeconds(3600)));
        // a segunda pessoa, distinta de todos, conclui
        v.conclude(APPROVER, T0.plusSeconds(3600));
        assertTrue(v.concluded());
        assertEquals(APPROVER, v.concludedByRef());
    }

    // (d) Completude aritmetica: item declarado sem execucao recusa a conclusao.
    @Test void conclusionRefusedWithItemMissingExecution() {
        SystemValidation v = openDossier();
        v.recordExecution(exec("ITEM_A", SystemValidationExecution.Outcome.PASSED, EXECUTOR));
        v.recordExecution(exec("ITEM_B", SystemValidationExecution.Outcome.PASSED, EXECUTOR));
        assertEquals(List.of("ITEM_C"), v.missingItemKeys());
        assertThrows(IllegalStateException.class, () -> v.conclude(APPROVER, T0.plusSeconds(3600)));
        assertEquals(SystemValidation.Status.OPEN, v.status());
        // item fora do plano declarado tambem e recusado
        assertThrows(IllegalArgumentException.class,
                () -> v.recordExecution(exec("ITEM_X", SystemValidationExecution.Outcome.PASSED, EXECUTOR)));
    }

    // (d) Conclusao bem-sucedida com todos os itens executados.
    @Test void conclusionSucceedsWhenAllItemsExecuted() {
        SystemValidation v = concludedDossier();
        assertEquals(SystemValidation.Status.CONCLUDED, v.status());
        assertTrue(v.isComplete());
        assertEquals(List.of(), v.missingItemKeys());
        assertEquals(SystemValidationExecution.Outcome.PASSED, v.outcome().orElseThrow());
    }

    // (d) Resultado do item e o como registado: FAILED fica FAILED, sem
    // faixa nem reinterpretacao — e a completude, nao o resultado, e o
    // portao da conclusao.
    @Test void itemResultIsPreservedAsRecorded() {
        SystemValidation v = openDossier();
        v.recordExecution(exec("ITEM_A", SystemValidationExecution.Outcome.PASSED, EXECUTOR));
        v.recordExecution(exec("ITEM_B", SystemValidationExecution.Outcome.FAILED, EXECUTOR));
        v.recordExecution(exec("ITEM_C", SystemValidationExecution.Outcome.PASSED, EXECUTOR));
        assertEquals(SystemValidationExecution.Outcome.FAILED,
                v.currentExecution("ITEM_B").orElseThrow().outcome());
        assertEquals(SystemValidationExecution.Outcome.FAILED, v.outcome().orElseThrow());
        v.conclude(APPROVER, T0.plusSeconds(3600));
        assertTrue(v.concluded());
        assertEquals(SystemValidationExecution.Outcome.FAILED, v.outcome().orElseThrow());
    }

    // (e) Historico por acrescimo: novo registo do mesmo item preserva o anterior.
    @Test void executionHistoryIsAppendOnly() {
        SystemValidation v = openDossier();
        v.recordExecution(exec("ITEM_A", SystemValidationExecution.Outcome.FAILED, EXECUTOR));
        v.recordExecution(new SystemValidationExecution("ITEM_A",
                SystemValidationExecution.Outcome.PASSED, "EV-FIXTURE-ITEM_A-2",
                EXECUTOR, T0.plusSeconds(1200), "reexecucao registrada"));
        assertEquals(2, v.executions().size());
        assertEquals(SystemValidationExecution.Outcome.FAILED, v.executions().get(0).outcome());
        assertEquals(SystemValidationExecution.Outcome.PASSED,
                v.currentExecution("ITEM_A").orElseThrow().outcome());
    }

    // (e) Emenda de dossie concluido: nasce a revisao seguinte e a
    // anterior fica SUPERSEDED, preservada com historico e conclusao.
    @Test void amendmentPreservesTheConcludedOriginal() {
        SystemValidation original = concludedDossier();
        UUID originalId = original.id();
        // emendar sem motivo e recusado
        assertThrows(IllegalArgumentException.class, () -> original.amend(enabled(),
                UUID.randomUUID(), "vTESTE", plan(), " ", APPROVER, T0.plusSeconds(7200)));
        SystemValidation next = original.amend(enabled(), UUID.randomUUID(), "vTESTE",
                plan(), "Correcao do plano declarado", APPROVER, T0.plusSeconds(7200));
        assertEquals(SystemValidation.Status.SUPERSEDED, original.status());
        assertEquals(3, original.executions().size());
        assertEquals(APPROVER, original.concludedByRef());
        assertEquals(SystemValidation.Status.OPEN, next.status());
        assertEquals(2, next.revision());
        assertEquals(originalId, next.supersedesId());
        assertEquals("Correcao do plano declarado", next.amendmentReason());
        // a revisao substituida nao se emenda de novo, nem recebe execucoes
        assertThrows(IllegalStateException.class, () -> original.amend(enabled(),
                UUID.randomUUID(), "vTESTE", plan(), "outra", APPROVER, T0.plusSeconds(7300)));
        assertThrows(IllegalStateException.class,
                () -> original.recordExecution(exec("ITEM_A",
                        SystemValidationExecution.Outcome.PASSED, EXECUTOR)));
        // emendar dossie ainda OPEN tambem e recusado
        SystemValidation open = openDossier();
        assertThrows(IllegalStateException.class, () -> open.amend(enabled(),
                UUID.randomUUID(), "vTESTE", plan(), "motivo", APPROVER, T0.plusSeconds(100)));
    }
}
