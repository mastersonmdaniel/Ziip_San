package br.com.ziipsan.intelligence.gis.asset;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import br.com.ziipsan.intelligence.gis.asset.AssetPorts.ValveOperabilitySnapshot;
import br.com.ziipsan.intelligence.gis.asset.AssetPorts.ValveOperabilityState;
import br.com.ziipsan.intelligence.gis.asset.EquipmentClassCatalog.EquipmentClass;
import br.com.ziipsan.intelligence.gis.asset.PmPlanService.PmPlanLine;
import br.com.ziipsan.intelligence.gis.asset.PmPlanService.TriggerKind;
import br.com.ziipsan.intelligence.gis.asset.PmPlanService.WorkPlan;
import br.com.ziipsan.intelligence.gis.asset.ReconciliationService.AssetAccountingInput;
import br.com.ziipsan.intelligence.gis.asset.ReconciliationService.Divergence;
import br.com.ziipsan.intelligence.gis.asset.RiskRenewalService.RiskInput;

import static br.com.ziipsan.intelligence.gis.asset.AssetFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Correcoes D2–D8 da REVISAO-ED-56 (pacote reprovado). Cada teste
 * reproduz primeiro a sonda do revisor (que passava/falhava errado)
 * e prova o comportamento corrigido. D1 (SQL) e provado por
 * aplicacao real em PostgreSQL — ver RELATORIO-CORRECAO-ED-56.
 */
class CorrectionEd56Test {

    private static WorkPlan workPlan() {
        return new WorkPlan(List.of("Inspecionar"), List.of(), List.of(), "Bloqueio e etiquetagem");
    }

    // ------------------------------------------------------------ D2
    @Test
    void d2TriggerAndIntervalAreBoundInJava() {
        // sonda P1 do revisor: TIME + horas era aceito e statusAt dava NPE
        assertThrows(IllegalArgumentException.class, () -> new PmPlanLine(
                BOMBA, "PUMP", TriggerKind.TIME, null, 100.0, null, workPlan(), null));
        // sonda P3: RUNNING_HOURS + meses era aceito e dueByUsage dava NPE
        assertThrows(IllegalArgumentException.class, () -> new PmPlanLine(
                BOMBA, "PUMP", TriggerKind.RUNNING_HOURS, 6, null, null, workPlan(), null));
        assertThrows(IllegalArgumentException.class, () -> new PmPlanLine(
                BOMBA, "PUMP", TriggerKind.PUMPED_VOLUME, null, 10.0, null, workPlan(), null));
        // combinacoes certas seguem validas e sem NPE
        var time = new PmPlanLine(BOMBA, "PUMP", TriggerKind.TIME, 6, null, null, workPlan(), null);
        assertEquals(PmPlanService.PmStatus.DUE_FOR_GENERATION, PmPlanService.statusAt(time,
                new PmPlanService.AssetRuntime(BOMBA, LocalDate.of(2026, 1, 10), 0, 0),
                null, LocalDate.of(2026, 7, 10), 0, 0));
        var hours = new PmPlanLine(BOMBA, "PUMP", TriggerKind.RUNNING_HOURS, null, 100.0, null, workPlan(), null);
        assertTrue(PmPlanService.dueByUsage(hours, new PmPlanService.AssetRuntime(BOMBA, null, 100, 0)));
    }

    // ------------------------------------------------------------ D3
    @Test
    void d3StaleOrNeverExercisedValveIsUnknownNeverOperable() {
        var ref = LocalDate.of(2026, 1, 2);
        // sonda O2: CRITICAL, ultimo exercicio em 2020, operable=true velho
        var stale = ValveHydrantProgram.assessOperability(VALVULA, true,
                LocalDate.of(2020, 1, 1), null, "CRITICAL", Map.of(), ref);
        assertEquals(ValveOperabilityState.UNKNOWN, stale.state());
        assertFalse(stale.operable());
        assertEquals(LocalDate.of(2020, 1, 1), stale.lastExercisedOn());
        assertEquals(LocalDate.of(2021, 1, 1), stale.exerciseDueOn());
        // nunca exercitada (sem resultado e sem data): UNKNOWN, nem devida ela era no booleano velho
        var never = ValveHydrantProgram.assessOperability(VALVULA, null, null, null,
                "CRITICAL", Map.of(), ref);
        assertEquals(ValveOperabilityState.UNKNOWN, never.state());
        assertNull(never.lastExercisedOn());
        // fresca e fechou: OPERABLE (STANDARD 2021-06-01 + 5 anos = 2026-06-01, ref antes)
        var fresh = ValveHydrantProgram.assessOperability(VALVULA, true,
                LocalDate.of(2021, 6, 1), null, "STANDARD", Map.of(), LocalDate.of(2026, 5, 31));
        assertEquals(ValveOperabilityState.OPERABLE, fresh.state());
        assertTrue(fresh.operable());
        // nao fechou: NOT_OPERABLE conhecido, mesmo vencida depois
        var stuck = ValveHydrantProgram.assessOperability(VALVULA, false,
                LocalDate.of(2020, 1, 1), null, "CRITICAL", Map.of(), ref);
        assertEquals(ValveOperabilityState.NOT_OPERABLE, stuck.state());
        assertFalse(stuck.operable());
        // regra do tenant sobrepoe a AWWA: STANDARD com 10 anos no parametro segue OPERABLE
        var tenantRule = ValveHydrantProgram.assessOperability(VALVULA, true,
                LocalDate.of(2020, 1, 1), null, "STANDARD", Map.of("STANDARD", 10), ref);
        assertEquals(ValveOperabilityState.OPERABLE, tenantRule.state());
    }

    @Test
    void d3ExerciseResultDeliversFreshSnapshotToEd54() {
        java.util.List<ValveOperabilitySnapshot> delivered = new java.util.ArrayList<>();
        AssetPorts.ValveOperabilityPort port = new AssetPorts.ValveOperabilityPort() {
            @Override
            public void applyValveExerciseResult(UUID tenantId, UUID valveAssetId, boolean operable,
                                                 String leftPosition, LocalDateTime at, UUID workOrderId) {
            }

            @Override
            public void deliverOperabilitySnapshot(UUID tenantId, ValveOperabilitySnapshot snapshot) {
                delivered.add(snapshot);
            }
        };
        var ok = new ValveHydrantProgram.ValveExerciseResult(VALVULA,
                LocalDateTime.of(2026, 8, 1, 9, 0), 12.5, "OPEN", "OPEN", true, UUID.randomUUID());
        ValveHydrantProgram.applyExerciseResult(port, UUID.randomUUID(), ok);
        assertEquals(1, delivered.size());
        assertEquals(ValveOperabilityState.OPERABLE, delivered.get(0).state());
        assertEquals(LocalDate.of(2026, 8, 1), delivered.get(0).lastExercisedOn());
    }

    // ------------------------------------------------------------ D4
    @Test
    void d4NaturalLanguageBoundaryOverlapIsDetected() {
        // sonda E1 do revisor: sobreposicao real em linguagem natural passava vazia
        var aaa = new EquipmentClass("AAA", "A",
                "motor eletrico de acionamento e bomba", "nada", null, null, false, false);
        var motor = EquipmentClassCatalog.requireClass(EquipmentClassCatalog.SOURCED_CLASSES, "MOTOR");
        var problems = EquipmentClassCatalog.boundaryOverlaps(List.of(aaa, motor));
        assertFalse(problems.isEmpty(), "sobreposicao em linguagem natural deve ser acusada");
        assertTrue(problems.get(0).contains("AAA") && problems.get(0).contains("MOTOR"));
        // exclusao nominal apaga a acusacao (fronteira declarada de verdade)
        var aaaExcluding = new EquipmentClass("AAA", "A",
                "motor eletrico de acionamento e bomba", "o motor (MOTOR)", null, null, false, false);
        assertEquals(List.of(), EquipmentClassCatalog.boundaryOverlaps(List.of(aaaExcluding, motor)));
        // o catalogo com fonte continua sem sobreposicao — agora por exame, nao por vacuidade
        assertEquals(List.of(), EquipmentClassCatalog.boundaryOverlaps(EquipmentClassCatalog.SOURCED_CLASSES));
        // "horimetro" nao casa com "hidrometro" (tokens, nao substring)
        var instrument = EquipmentClassCatalog.requireClass(EquipmentClassCatalog.SOURCED_CLASSES, "INSTRUMENT");
        var meter = EquipmentClassCatalog.requireClass(EquipmentClassCatalog.SOURCED_CLASSES, "METER");
        assertEquals(List.of(), EquipmentClassCatalog.boundaryOverlaps(List.of(instrument, meter)));
    }

    // ------------------------------------------------------------ D5
    @Test
    void d5ReversibleWithoutOriginEmitsDivergence() {
        AssetPorts.AccountingPort acc = new AssetPorts.AccountingPort() {
            @Override
            public Optional<String> resolveTenantAccount(String reg, int exercise) {
                return Optional.of("1.1.1.01");
            }

            @Override
            public void receiveReconciliationExport(UUID tenantId, String ref, int exercise) {
            }
        };
        var svc = new ReconciliationService(acc);
        // sonda R1 do revisor: reversivel completo mas sem origem saia clean=true
        var noOrigin = new AssetAccountingInput(BOMBA, "PAT-X", "12", "1234567", "MCRSB-1",
                new BigDecimal("10"), true, null);
        var line = svc.reconcile(UUID.randomUUID(), 2026, List.of(noOrigin)).lines().get(0);
        assertFalse(line.clean());
        assertEquals(List.of(Divergence.REVERSIBLE_WITHOUT_ORIGIN), line.divergences());
        // reversivel COM origem segue limpo; nao reversivel sem origem tambem
        var withOrigin = new AssetAccountingInput(BOMBA, "PAT-X", "12", "1234567", "MCRSB-1",
                new BigDecimal("10"), true, "Investimento do titular (sintetico)");
        assertTrue(svc.reconcile(UUID.randomUUID(), 2026, List.of(withOrigin)).lines().get(0).clean());
        var notReversible = new AssetAccountingInput(BOMBA, "PAT-X", "12", "1234567", "MCRSB-1",
                new BigDecimal("10"), false, null);
        assertTrue(svc.reconcile(UUID.randomUUID(), 2026, List.of(notReversible)).lines().get(0).clean());
    }

    // ------------------------------------------------------------ D6
    @Test
    void d6PacpParserIsGatedByConfirmedStandard() {
        String file = "T-01;1.0;CRACK;3";
        // sonda C1 do revisor: parse sem nenhuma confirmacao era possivel — nao e mais
        var ex = assertThrows(IllegalStateException.class,
                () -> CctvInspectionService.parsePacpTransfer("T-01", file, null));
        assertTrue(ex.getMessage().contains("[A DEFINIR]"));
        // padrao confirmado WRC recusa o parser PACP
        assertThrows(IllegalStateException.class,
                () -> CctvInspectionService.parsePacpTransfer("T-01", file, "WRC"));
        // padrao invalido recusa; PACP confirmado parseia
        assertThrows(IllegalStateException.class,
                () -> CctvInspectionService.parsePacpTransfer("T-01", file, "OUTRO"));
        assertEquals(1, CctvInspectionService.parsePacpTransfer("T-01", file, "PACP").size());
    }

    // ------------------------------------------------------------ D7
    @Test
    void d7AllMcpsbCodesAreRefusedInFeatureAttributes() {
        // sonda L1 do revisor: estas chaves passavam sem recusa
        for (String key : List.of("loc_ibge", "ac", "nts_sds", "at1", "up_code",
                "at2", "at3", "at4", "at5", "cib", "uar", "up")) {
            assertThrows(IllegalArgumentException.class,
                    () -> AssetLinkRules.rejectPatrimonialCodeInFeatureAttributes(Map.of(key, "x")), key);
        }
        // atributos comuns da feicao seguem passando
        AssetLinkRules.rejectPatrimonialCodeInFeatureAttributes(
                Map.of("material", "PVC", "dn", "110", "actuator", "manual"));
    }

    // ------------------------------------------------------------ D8
    @Test
    void d8RiskViewExposesConsequenceAndValveProgramViewIsBuilt() {
        var a = RiskRenewalService.assess(new RiskInput(BOMBA, 3, 4, 4, "CRITICAL"));
        var view = AssetDtos.RiskView.of(a);
        assertEquals(5, view.probability());
        assertEquals(4, view.consequence());
        assertEquals(20, view.score());
        assertEquals("CRITICAL", view.band());
        // ValveProgramView montada pelo motor do programa (superficie via AssetOperations/controller)
        var stuck = new ValveHydrantProgram.ValveExerciseResult(id(77),
                LocalDateTime.of(2026, 8, 1, 10, 0), 3.0, "PARTIAL", "PARTIAL", false, UUID.randomUUID());
        var program = ValveHydrantProgram.programView(VALVULA, LocalDate.of(2021, 6, 1), null,
                "CRITICAL", Map.of(), List.of(stuck), 8);
        assertEquals(LocalDate.of(2022, 6, 1), program.nextExerciseDue());
        assertEquals(12.5, program.inoperablePercent());
        assertEquals(List.of(id(77)), program.replacementQueue());
    }
}
