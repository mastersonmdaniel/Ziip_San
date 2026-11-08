package br.com.ziipsan.core.apparentloss;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static br.com.ziipsan.core.apparentloss.LossFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TESTE NOMEADO DO ACEITE DA ED-95: "nenhuma regra gera debito nem
 * corte sem decisao humana registrada".
 *
 * <p>Prova em tres camadas, como o teste de arquitetura da P4.1:
 * <ol>
 *   <li><b>Funcional:</b> cruzamento e regras disparam sobre a massa
 *       que mais dispara — e a porta de efeitos (OS) registra ZERO
 *       chamadas ate existir decisao humana registrada;</li>
 *   <li><b>Decisao:</b> so {@code decideFinding}/
 *       {@code decideSuspicionWithInspection}, com decisao humana
 *       completa (quem/quando/acao), abre OS — e a OS aberta e de
 *       vistoria/fiscalizacao/recadastramento, nunca debito/corte;</li>
 *   <li><b>Arquitetura (reflexao):</b> nenhuma classe de producao do
 *       pacote declara metodo ou campo de debito, corte ou emissao
 *       de infracao, e {@code LossPorts} nao tem porta alguma com
 *       essas operacoes — gerar debito/corte a partir de uma regra
 *       e impossivel pela API, nao apenas evitado por convencao.</li>
 * </ol>
 */
class NoAutomaticDebitOrCutTest {

    /** Porta de OS espiã: registra cada chamada (unica porta de efeito do servico). */
    static final class RecordingWorkOrders implements LossPorts.WorkOrderPort {
        final List<String> calls = new ArrayList<>();

        @Override
        public UUID openWorkOrder(UUID tenantId, LossPorts.WorkOrderKind kind, UUID unitId, UUID propertyId, String reason) {
            calls.add(kind.name());
            return UUID.randomUUID();
        }
    }

    static final class MapParams implements LossPorts.TenantParameters {
        @Override
        public Optional<String> resolve(UUID tenantId, String parameterName) {
            return switch (parameterName) {
                case LossParameters.PROPERTY_REGISTRY_SYNC -> Optional.of("MODO-DECLARADO-TESTE");
                case LossParameters.UNREGISTERED_BUILDING_REVIEW_DAYS -> Optional.of("15");
                case LossParameters.FRAUD_RULES -> Optional.of(ALL_RULES_JSON);
                case LossParameters.SELF_REPORT_FINE_WAIVER_ENABLED -> Optional.of("true");
                default -> Optional.empty();
            };
        }
    }

    private ApparentLossService service(RecordingWorkOrders orders, List<UnitFacts> units) {
        LossPorts.UnitFactsPort facts = new LossPorts.UnitFactsPort() {
            @Override
            public List<UnitFacts> factsForTenant(UUID tenantId) {
                return units;
            }

            @Override
            public Optional<UnitFacts> facts(UUID tenantId, UUID unitId) {
                return units.stream().filter(u -> u.unitId().equals(unitId)).findFirst();
            }
        };
        return new ApparentLossService(new InMemoryLossStore(), null, facts, orders, new MapParams());
    }

    @Test
    void noRuleOrCrossMatchGeneratesAnyEffectWithoutHumanDecision() {
        RecordingWorkOrders orders = new RecordingWorkOrders();
        UUID pid = UUID.randomUUID();
        UUID uid = UUID.randomUUID();
        UnitFacts worstCase = unitFull(uid, pid,
                List.of(bd("0"), bd("0"), bd("0"), bd("0")), List.of(bd("100"), bd("100"), bd("100")),
                "2.5", "2.5", 1, "20", "1", true, "30");
        ApparentLossService svc = service(orders, List.of(worstCase));
        svc.importRegistryRecords(TENANT, List.of(property(pid, "100",
                PropertyRegistryRecord.PropertyUse.COMMERCIAL)));

        List<CrossMatch.Finding> findings = svc.runCrossMatch(TENANT, CrossMatch.AccessChannel.BACKOFFICE,
                bd("10"), NOW);
        assertFalse(findings.isEmpty(), "o cruzamento deve achar divergencias na massa de teste");
        List<FraudRules.Suspicion> suspicions = svc.evaluateUnit(TENANT, uid, NOW);
        assertFalse(suspicions.isEmpty(), "as regras devem disparar na massa de teste");

        assertTrue(orders.calls.isEmpty(),
                "ACEITE VIOLADO: efeito (OS) gerado sem decisao humana registrada: " + orders.calls);
        for (FraudRules.Suspicion s : suspicions) {
            assertEquals(FraudRules.AutomaticEffect.NONE, s.automaticEffect());
            assertEquals(FraudRules.Recommendation.HUMAN_INSPECTION_REVIEW, s.recommendation());
        }
        for (CrossMatch.Finding f : findings) {
            assertEquals(CrossMatch.ReviewStatus.PENDING_REVIEW, f.status());
            assertNull(f.workOrderId());
        }
    }

    @Test
    void workOrderOnlyAfterRegisteredHumanDecisionAndOnlyInspectionKinds() {
        RecordingWorkOrders orders = new RecordingWorkOrders();
        UUID pid = UUID.randomUUID();
        UUID uid = UUID.randomUUID();
        UnitFacts u = unit(uid, pid, UnitFacts.ConnectionStatus.ACTIVE,
                PropertyRegistryRecord.PropertyUse.COMMERCIAL, "100", 1,
                List.of(bd("10")), List.of(bd("100")));
        ApparentLossService svc = service(orders, List.of(u));
        svc.importRegistryRecords(TENANT, List.of(property(pid, "100",
                PropertyRegistryRecord.PropertyUse.RESIDENTIAL)));
        CrossMatch.Finding f = svc.runCrossMatch(TENANT, CrossMatch.AccessChannel.BACKOFFICE, bd("10"), NOW).get(0);

        assertThrows(NullPointerException.class, () -> svc.decideFinding(TENANT, f.id(), null));
        assertThrows(NullPointerException.class, () ->
                new CrossMatch.HumanDecision(null, NOW, CrossMatch.SuggestedAction.INSPECTION));
        assertTrue(orders.calls.isEmpty());

        CrossMatch.Finding decided = svc.decideFinding(TENANT, f.id(),
                new CrossMatch.HumanDecision(REVIEWER, NOW, CrossMatch.SuggestedAction.INSPECTION));
        assertEquals(List.of("INSPECTION"), orders.calls,
                "a unica OS possivel aqui e a de fiscalizacao (ED-24) — nunca debito nem corte");
        assertEquals(CrossMatch.ReviewStatus.DECIDED, decided.status());
        assertNotNull(decided.workOrderId());
        assertThrows(IllegalStateException.class, () -> svc.decideFinding(TENANT, f.id(),
                new CrossMatch.HumanDecision(REVIEWER, NOW, CrossMatch.SuggestedAction.INSPECTION)),
                "achado ja decidido nao gera segunda OS");
    }

    @Test
    void suspicionDecisionRequiresRegisteredSuspicionAndHumanDecision() {
        RecordingWorkOrders orders = new RecordingWorkOrders();
        UUID uid = UUID.randomUUID();
        UnitFacts u = unitFull(uid, null, List.of(bd("20"), bd("20"), bd("5")), List.of(bd("50")),
                null, null, null, null, null, false, null);
        ApparentLossService svc = service(orders, List.of(u));
        assertThrows(IllegalArgumentException.class, () -> svc.decideSuspicionWithInspection(
                TENANT, uid, FraudRules.Rule.ABRUPT_DROP,
                new CrossMatch.HumanDecision(REVIEWER, NOW, CrossMatch.SuggestedAction.INSPECTION)),
                "sem suspeita registrada, decisao nao abre OS");
        svc.evaluateUnit(TENANT, uid, NOW);
        svc.decideSuspicionWithInspection(TENANT, uid, FraudRules.Rule.ABRUPT_DROP,
                new CrossMatch.HumanDecision(REVIEWER, NOW, CrossMatch.SuggestedAction.INSPECTION));
        assertEquals(List.of("INSPECTION"), orders.calls);
    }

    @Test
    void architectureNoDebitCutOrInfractionOperationExistsInThePackage() {
        List<Class<?>> production = List.of(
                ApparentLossService.class, FraudRules.class, CrossMatch.class, LossPorts.class,
                LossParameters.class, PropertyRegistryRecord.class, UnitFacts.class,
                InspectionAccuracy.class, MlGate.class, SelfReport.class, InMemoryLossStore.class);
        // Nota: "fine waiver" (isencao de multa do programa de autodenuncia) NAO e
        // emissao de multa — e a isencao declarada pelo tenant; por isso fine/multa
        // fora da lista de operacoes e cobertos funcionalmente nos testes do programa.
        List<String> forbidden = List.of("debit", "debito", "charge", "cobranca", "cutoff",
                "corte", "infraction", "infracao");
        for (Class<?> c : production) {
            for (Method m : c.getDeclaredMethods()) {
                String name = m.getName().toLowerCase(Locale.ROOT);
                for (String word : forbidden) {
                    assertFalse(name.contains(word),
                            "operacao proibida na ED-95 (ADR-0042): " + c.getSimpleName() + "." + m.getName());
                }
            }
            for (Field f : c.getDeclaredFields()) {
                String name = f.getName().toLowerCase(Locale.ROOT);
                for (String word : List.of("debit", "debito", "cutoff")) {
                    assertFalse(name.contains(word),
                            "campo proibido na ED-95 (ADR-0042): " + c.getSimpleName() + "." + f.getName());
                }
            }
        }
        // A porta de efeitos so conhece os tres tipos de OS do aceite.
        assertEquals(3, LossPorts.WorkOrderKind.values().length);
    }
}
