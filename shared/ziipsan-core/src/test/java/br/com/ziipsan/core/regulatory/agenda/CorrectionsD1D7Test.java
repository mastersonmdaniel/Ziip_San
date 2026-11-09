package br.com.ziipsan.core.regulatory.agenda;

import br.com.ziipsan.core.regulatory.export.ExportPackage;
import br.com.ziipsan.core.regulatory.export.SubmissionRecord;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Testes negativos permanentes das correcoes D1–D7 da REVISAO-ED-98
 * (frente de correcao ED-98, 09/11/2026): cada sonda adversaria do
 * revisor (S1–S8) vira teste — as garantias passam a valer POR
 * CONSTRUCAO (construtor canonico / regra canonica), nao por remendo
 * no chamador.
 */
class CorrectionsD1D7Test {

    private ObligationCalendarService service() {
        return new ObligationCalendarService(AgendaFixtures.fullPort(),
                new AgendaFixtures.RecordingReminders());
    }

    private AgendaItem generatedItem(ObligationKind kind) {
        return service().generate(AgendaFixtures.profile(
                        AgendaFixtures.TENANT_A, TenantLegalNature.AUTARQUIA, false), 2027)
                .itemsOf(kind).get(0);
    }

    private SubmissionRecord submission(UUID tenant, String profileKey, String periodRef,
                                        SubmissionRecord.Status status) {
        return new SubmissionRecord(UUID.randomUUID(), tenant, "ARIS", profileKey, "[A DEFINIR]",
                periodRef, ExportPackage.Kind.AGGREGATE_PUBLIC, "a".repeat(64), status,
                Instant.now(),
                status == SubmissionRecord.Status.SUBMITTED_BY_TENANT ? "SYN-PROTO-1" : null);
    }

    // ---------- D1 (S1, S2, S7): vinculo RAS-08 ----------

    @Test void d1EvidenceFromAnotherPeriodIsRefused() {
        // S1 literal: item SISAGUA 2027-ANNUAL + evidencia do periodo 2025-ANNUAL.
        AgendaItem item = generatedItem(ObligationKind.SISAGUA);
        SubmissionRecord old = submission(AgendaFixtures.TENANT_A, "ARIS_SISAGUA", "2025-ANNUAL",
                SubmissionRecord.Status.SUBMITTED_BY_TENANT);
        assertThrows(IllegalArgumentException.class, () -> item.linkSubmission(old));
    }

    @Test void d1GeneratedValidatedRecordIsNotSubmissionEvidence() {
        // S2 literal: registro apenas GENERATED_VALIDATED (pacote
        // gerado/validado; o envio fisico e do tenant) nao conclui nada.
        AgendaItem item = generatedItem(ObligationKind.SISAGUA);
        SubmissionRecord notSent = submission(AgendaFixtures.TENANT_A, "ARIS_SISAGUA", "2027-ANNUAL",
                SubmissionRecord.Status.GENERATED_VALIDATED);
        assertThrows(IllegalArgumentException.class, () -> item.linkSubmission(notSent));
    }

    @Test void d1SameEvidenceCannotSettleTwoDifferentKinds() {
        // S7 literal: o MESMO registro ligado a SISAGUA e a SINISA —
        // corresponde a UM tipo so; o outro tipo recusa.
        SubmissionRecord rec = submission(AgendaFixtures.TENANT_A, "ARIS_SISAGUA", "2027-ANNUAL",
                SubmissionRecord.Status.SUBMITTED_BY_TENANT);
        AgendaItem linked = generatedItem(ObligationKind.SISAGUA).linkSubmission(rec);
        assertEquals(AgendaItem.Status.EVIDENCE_LINKED, linked.status());
        assertEquals(AgendaItem.Status.DONE, linked.complete().status());
        assertThrows(IllegalArgumentException.class,
                () -> generatedItem(ObligationKind.SINISA_ANNUAL_CYCLE).linkSubmission(rec));
        // Simetrico: evidencia do SINISA quita o SINISA, nao o SISAGUA.
        SubmissionRecord recSinisa = submission(AgendaFixtures.TENANT_A, "SINISA", "2027-ANNUAL",
                SubmissionRecord.Status.SUBMITTED_BY_TENANT);
        assertEquals(AgendaItem.Status.EVIDENCE_LINKED,
                generatedItem(ObligationKind.SINISA_ANNUAL_CYCLE).linkSubmission(recSinisa).status());
        assertThrows(IllegalArgumentException.class,
                () -> generatedItem(ObligationKind.SISAGUA).linkSubmission(recSinisa));
    }

    @Test void d1EvidenceOfAnotherReportProfileIsRefused() {
        // Periodo e envio certos, perfil de OUTRO relatorio (RREO):
        // sem correspondencia de tipo, nao quita o SISAGUA.
        AgendaItem item = generatedItem(ObligationKind.SISAGUA);
        SubmissionRecord otherReport = submission(AgendaFixtures.TENANT_A, "RREO", "2027-ANNUAL",
                SubmissionRecord.Status.SUBMITTED_BY_TENANT);
        assertThrows(IllegalArgumentException.class, () -> item.linkSubmission(otherReport));
    }

    @Test void d1ConstructorRefusesEvidenceRefFromAnotherPeriod() {
        // Por construcao: nem instanciando o record direto a evidencia
        // de outro periodo entra (o periodo vai embutido na referencia).
        assertThrows(IllegalArgumentException.class, () -> new AgendaItem(UUID.randomUUID(),
                AgendaFixtures.TENANT_A, ObligationKind.SISAGUA, "t", "k", "2027-ANNUAL", null,
                "[A DEFINIR]", AgendaFixtures.RESP,
                "ED97-EVIDENCE:ARIS:2025-ANNUAL:aaaaaaaaaaaaaaaa",
                AgendaItem.Status.EVIDENCE_LINKED, false, null, null, null, null));
    }

    @Test void d1ConstructorRefusesMalformedOrBlankEvidenceRef() {
        assertThrows(IllegalArgumentException.class, () -> new AgendaItem(UUID.randomUUID(),
                AgendaFixtures.TENANT_A, ObligationKind.SISAGUA, "t", "k", "2027-ANNUAL", null,
                "[A DEFINIR]", AgendaFixtures.RESP, "PROTOCOLO-123",
                AgendaItem.Status.EVIDENCE_LINKED, false, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new AgendaItem(UUID.randomUUID(),
                AgendaFixtures.TENANT_A, ObligationKind.SISAGUA, "t", "k", "2027-ANNUAL", null,
                "[A DEFINIR]", AgendaFixtures.RESP, "   ",
                AgendaItem.Status.EVIDENCE_LINKED, false, null, null, null, null));
    }

    @Test void d1CoherentSubmittedEvidenceStillLinksAndCompletes() {
        // A correcao nao bloqueia o caminho legitimo: mesmo tenant,
        // mesmo periodo, envio real declarado e perfil correspondente.
        SubmissionRecord rec = submission(AgendaFixtures.TENANT_A, "ARIS_SISAGUA", "2027-ANNUAL",
                SubmissionRecord.Status.SUBMITTED_BY_TENANT);
        AgendaItem linked = generatedItem(ObligationKind.SISAGUA).linkSubmission(rec);
        assertEquals(rec.evidenceRef(), linked.evidenceRef());
        assertEquals(AgendaItem.Status.DONE, linked.complete().status());
    }

    // ---------- D2 (S3b): RREO/RGF = natureza de ente + flag ----------

    @Test void d2PrivateConcessionaireWithFiscalFlagGetsNoRreoRgf() {
        // S3b literal: concessionaria privada com a flag ligada.
        var gen = service().generate(AgendaFixtures.profile(
                AgendaFixtures.TENANT_C, TenantLegalNature.CONCESSIONARIA_PRIVADA, true), 2027);
        assertTrue(gen.itemsOf(ObligationKind.RREO).isEmpty(), "ente privado nao emite RREO");
        assertTrue(gen.itemsOf(ObligationKind.RGF).isEmpty(), "ente privado nao emite RGF");
        assertTrue(gen.itemsOf(ObligationKind.ESFINGE_BIMONTHLY).isEmpty());
    }

    @Test void d2AutarchyAndPublicCompanyWithFiscalFlagGetNoRreoRgf() {
        for (TenantLegalNature nature : List.of(TenantLegalNature.AUTARQUIA,
                TenantLegalNature.EMPRESA_PUBLICA, TenantLegalNature.FUNDACAO_PUBLICA,
                TenantLegalNature.SOCIEDADE_ECONOMIA_MISTA)) {
            var gen = service().generate(AgendaFixtures.profile(
                    AgendaFixtures.TENANT_A, nature, true), 2027);
            assertTrue(gen.itemsOf(ObligationKind.RREO).isEmpty(), nature.name());
            assertTrue(gen.itemsOf(ObligationKind.RGF).isEmpty(), nature.name());
            assertEquals(6, gen.itemsOf(ObligationKind.ESFINGE_BIMONTHLY).size(),
                    "natureza de direito publico segue jurisdicionada ao TC: " + nature.name());
        }
    }

    @Test void d2EntityNeedsBothNatureAndFiscalFlag() {
        var withFlag = service().generate(AgendaFixtures.profile(
                AgendaFixtures.TENANT_B, TenantLegalNature.ADMINISTRACAO_DIRETA_ENTE, true), 2027);
        assertEquals(6, withFlag.itemsOf(ObligationKind.RREO).size());
        assertEquals(3, withFlag.itemsOf(ObligationKind.RGF).size());
        var noFlag = service().generate(AgendaFixtures.profile(
                AgendaFixtures.TENANT_B, TenantLegalNature.ADMINISTRACAO_DIRETA_ENTE, false), 2027);
        assertTrue(noFlag.itemsOf(ObligationKind.RREO).isEmpty(),
                "natureza de ente sem a flag tambem nao recebe (quando aplicavel = natureza E flag)");
        assertTrue(noFlag.itemsOf(ObligationKind.RGF).isEmpty());
    }

    // ---------- D3 (S3a): a prova GES-05 separa natureza de flag ----------

    @Test void d3SameFlagAutarchyAndEntityCalendarsDifferOnlyByNature() {
        // S3a literal: com a flag IGUALADA (false), autarquia e ente
        // tem conjuntos identicos — a diferenca so aparece onde a
        // NATUREZA decide (RREO/RGF do ente, com a flag ligada).
        var svc = service();
        Set<ObligationKind> autOff = svc.generate(AgendaFixtures.profile(
                        AgendaFixtures.TENANT_A, TenantLegalNature.AUTARQUIA, false), 2027)
                .items().stream().map(AgendaItem::kind).collect(Collectors.toSet());
        Set<ObligationKind> enteOff = svc.generate(AgendaFixtures.profile(
                        AgendaFixtures.TENANT_B, TenantLegalNature.ADMINISTRACAO_DIRETA_ENTE, false), 2027)
                .items().stream().map(AgendaItem::kind).collect(Collectors.toSet());
        assertEquals(autOff, enteOff, "S3a: com a mesma flag, os conjuntos-base sao identicos");
        Set<ObligationKind> autOn = svc.generate(AgendaFixtures.profile(
                        AgendaFixtures.TENANT_A, TenantLegalNature.AUTARQUIA, true), 2027)
                .items().stream().map(AgendaItem::kind).collect(Collectors.toSet());
        Set<ObligationKind> enteOn = svc.generate(AgendaFixtures.profile(
                        AgendaFixtures.TENANT_B, TenantLegalNature.ADMINISTRACAO_DIRETA_ENTE, true), 2027)
                .items().stream().map(AgendaItem::kind).collect(Collectors.toSet());
        Set<ObligationKind> onlyEntity = new java.util.HashSet<>(enteOn);
        onlyEntity.removeAll(autOn);
        assertEquals(Set.of(ObligationKind.RREO, ObligationKind.RGF), onlyEntity,
                "com a mesma flag ligada, a diferenca autarquia x ente e SO a natureza (RREO/RGF do ente)");
        assertEquals(autOff, autOn, "a flag nao muda o calendario da autarquia");
    }

    // ---------- D4 (S4): casamento com dobra de acentos ----------

    @Test void d4AccentedTitleMatchesUnaccentedToken() {
        // S4 literal: titulo acentuado realista + chave sem o token.
        var view = new AgendaPorts.NormRuleView("doc-2027-0142",
                "Declaração anual de quitação", "VIGENTE", LocalDate.of(2024, 1, 1));
        assertTrue(view.matchesToken("QUITACAO"), "a dobra de acentos casa o titulo realista");
        assertTrue(view.matchesToken("quitação"), "token acentuado tambem casa");
        assertTrue(view.matchesToken("DECLARACAO"));
        assertFalse(view.matchesToken("SISAGUA"));
    }

    @Test void d4AccentedCatalogResolvesEveryRuleEndToEnd() {
        // Catalogo "realista": chaves opacas (padrao doc-NNNN, como as
        // chaves reais da carga P1.34) e titulos acentuados — nenhuma
        // regra pode cair em unresolvedKinds por causa de acento.
        List<AgendaPorts.NormRuleView> norms = List.of(
            new AgendaPorts.NormRuleView("doc-0001", "Sistema Nacional de Informações sobre Saneamento (Sinisa)", "VIGENTE", LocalDate.of(2024, 1, 1)),
            new AgendaPorts.NormRuleView("doc-0002", "Relatório anual de tarifa social", "VIGENTE", LocalDate.of(2024, 1, 1)),
            new AgendaPorts.NormRuleView("doc-0003", "Declaração anual de quitação", "VIGENTE", LocalDate.of(2024, 1, 1)),
            new AgendaPorts.NormRuleView("doc-0004", "Balanço de perdas de água (NR 15)", "VIGENTE", LocalDate.of(2024, 1, 1)),
            new AgendaPorts.NormRuleView("doc-0005", "Remessa bimestral ao e-Sfinge (IN TC-28/2021)", "VIGENTE", LocalDate.of(2024, 1, 1)),
            new AgendaPorts.NormRuleView("doc-0006", "Vigilância da qualidade da água — Sisagua", "VIGENTE", LocalDate.of(2024, 1, 1)),
            new AgendaPorts.NormRuleView("doc-0007", "Publicação do reajuste tarifário anual", "VIGENTE", LocalDate.of(2024, 1, 1)),
            new AgendaPorts.NormRuleView("doc-0008", "Relatório Resumido da Execução Orçamentária (RREO)", "VIGENTE", LocalDate.of(2024, 1, 1)),
            new AgendaPorts.NormRuleView("doc-0009", "Relatório de Gestão Fiscal (RGF)", "VIGENTE", LocalDate.of(2024, 1, 1)),
            new AgendaPorts.NormRuleView("doc-0010", "Declaração de Movimentação de Resíduos (DMR)", "VIGENTE", LocalDate.of(2024, 1, 1)));
        var svc = new ObligationCalendarService(() -> norms, new AgendaFixtures.RecordingReminders());
        var gen = svc.generate(AgendaFixtures.profile(
                AgendaFixtures.TENANT_B, TenantLegalNature.ADMINISTRACAO_DIRETA_ENTE, true), 2027);
        assertTrue(gen.unresolvedKinds().isEmpty(),
                "nenhuma regra pode ficar nao resolvida por acento: " + gen.unresolvedKinds());
        assertEquals(6, gen.itemsOf(ObligationKind.ESFINGE_BIMONTHLY).size());
        assertEquals(6, gen.itemsOf(ObligationKind.RREO).size());
        assertEquals(3, gen.itemsOf(ObligationKind.RGF).size());
        assertEquals(1, gen.itemsOf(ObligationKind.ANNUAL_DISCHARGE_DECLARATION).size());
    }

    // ---------- D5 (S6): ancora da quitacao expoe so o mes ----------

    @Test void d5DischargeRuleExposesMonthOnlyNoConcreteDay() {
        ObligationRule rule = ObligationRule.standardRuleFor(ObligationKind.ANNUAL_DISCHARGE_DECLARATION);
        assertEquals(Month.MAY, rule.annualMonth(), "a ancora com fonte e o MES de maio (aceite)");
        // Nenhum componente MonthDay pode voltar ao record da regra:
        // era o --05-01 sem fonte da sonda S6.
        for (var component : ObligationRule.class.getRecordComponents()) {
            assertNotEquals(java.time.MonthDay.class, component.getType(),
                    "componente com dia concreto sem fonte: " + component.getName());
        }
    }

    @Test void d5GeneratedDischargeItemKeepsDayUndefined() {
        AgendaItem item = generatedItem(ObligationKind.ANNUAL_DISCHARGE_DECLARATION);
        assertNull(item.dueDate(), "dia sem fonte nao vira data fingida");
        assertTrue(item.dueLabel().contains("maio/2027"));
        assertTrue(item.dueLabel().contains("[A DEFINIR]"));
    }

    // ---------- D6: periodicidades sem fonte marcadas ----------

    @Test void d6PeriodicitiesWithoutSourceAreMarkedUndefined() {
        assertFalse(ObligationRule.standardRuleFor(ObligationKind.WATER_LOSS_BALANCE_NR15).periodicityDefined(),
                "NR 15: periodicidade sem fonte marcada [A DEFINIR]");
        assertFalse(ObligationRule.standardRuleFor(ObligationKind.SISAGUA).periodicityDefined(),
                "SISAGUA: periodicidade sem fonte marcada [A DEFINIR]");
        assertFalse(ObligationRule.standardRuleFor(ObligationKind.WASTE_MTR_CDF_DMR_MANUAL).periodicityDefined(),
                "item manual RAS-17: periodicidade sem fonte marcada [A DEFINIR]");
        for (ObligationKind kind : List.of(ObligationKind.SINISA_ANNUAL_CYCLE,
                ObligationKind.SOCIAL_TARIFF_ANNUAL_REPORT, ObligationKind.ANNUAL_DISCHARGE_DECLARATION,
                ObligationKind.ESFINGE_BIMONTHLY, ObligationKind.TARIFF_READJUSTMENT_PUBLICATION,
                ObligationKind.RREO, ObligationKind.RGF)) {
            assertTrue(ObligationRule.standardRuleFor(kind).periodicityDefined(),
                    "periodicidade com fonte no aceite/LC 101: " + kind);
        }
    }

    @Test void d6GeneratedItemsCarryTheUndefinedPeriodicityMark() {
        for (ObligationKind kind : List.of(ObligationKind.SISAGUA, ObligationKind.WATER_LOSS_BALANCE_NR15,
                ObligationKind.WASTE_MTR_CDF_DMR_MANUAL)) {
            AgendaItem item = generatedItem(kind);
            assertTrue(item.dueLabel().contains("[A DEFINIR]"), kind.name());
            assertTrue(item.dueLabel().contains("periodicidade"),
                    "a provisao da periodicidade aparece no rotulo do item: " + kind);
        }
        AgendaItem sinisa = generatedItem(ObligationKind.SINISA_ANNUAL_CYCLE);
        assertEquals("[A DEFINIR] vencimento anual sem fonte no aceite nem no catalogo",
                sinisa.dueLabel(), "periodicidade COM fonte nao ganha a marca de provisoria");
    }

    // ---------- S8 (comportamento correto, vira regressao permanente) ----------

    @Test void s8ReadjustmentWithoutBaseDateIsUndefinedNotInvented() {
        var noBase = new TenantObligationProfile(AgendaFixtures.TENANT_A, TenantLegalNature.AUTARQUIA,
                null, false, AgendaFixtures.RESP, Map.of());
        AgendaItem item = service().generate(noBase, 2027)
                .itemsOf(ObligationKind.TARIFF_READJUSTMENT_PUBLICATION).get(0);
        assertNull(item.dueDate());
        assertTrue(item.dueLabel().contains("[A DEFINIR]"));
    }
}
