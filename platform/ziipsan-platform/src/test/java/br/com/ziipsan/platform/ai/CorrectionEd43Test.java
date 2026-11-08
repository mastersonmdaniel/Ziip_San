package br.com.ziipsan.platform.ai;
import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal; import java.time.*; import java.util.*; import java.util.concurrent.atomic.*;
import br.com.ziipsan.platform.notification.multichannel.UsageMetric;

/** CORRECAO ED-43 — um teste por defeito da revisao cruzada (M1–M4, m1–m6).
 * Os testes comportamentais usam so APIs pre-existentes onde possivel; os de API nova
 * (PROMPT_NOT_EVALUATED, despacho EXPLAIN_BILL, Draft.markReviewedByExecutor, AiRetentionSweeper)
 * nem compilam sem a correcao. A captura pre-correcao esta em CaptureTest (harness de correcao). */
class CorrectionEd43Test {
    static final class CountingModel implements ModelPort {
        final AtomicInteger calls = new AtomicInteger(); final AtomicReference<ModelCall> last = new AtomicReference<>();
        String out = "Resposta generica de ajuda sobre a sua conta."; long tokens = 42; BigDecimal cost = new BigDecimal("0.50");
        public ModelResult complete(ModelCall c){ calls.incrementAndGet(); last.set(c); return new ModelResult(out, tokens, cost); }
    }
    static ChatbotService svc(Ed43TestSupport.Params p, ModelPort m, Ed43TestSupport.Usage u, Ed43TestSupport.Logs l){
        return svc(p, m, u, l, ()->0L, ()->BigDecimal.ZERO);
    }
    static ChatbotService svc(Ed43TestSupport.Params p, ModelPort m, Ed43TestSupport.Usage u, Ed43TestSupport.Logs l,
                              java.util.function.LongSupplier used, java.util.function.Supplier<BigDecimal> spent){
        return new ChatbotService(p, t->Optional.of(Ed43TestSupport.okReg()), m, Ed43TestSupport.registryOk(p), l, u,
            Clock.fixed(Instant.parse("2026-11-08T12:00:00Z"), ZoneOffset.UTC), used, spent, Ed43TestSupport.billExplain(p), Ed43TestSupport.billContext());
    }
    static ChatbotService.ChatRequest req(String text, String fn, Set<String> perms){ return Ed43TestSupport.req(text, fn, perms); }

    // ---------- M1: mascara/detector fail-closed nao contornaveis por grafia ----------
    @Test void m1_spacedCpfIsMasked(){ String out = PiiMasker.mask("meu cpf e 123 456 789 09, ok?", List.of()); assertFalse(out.contains("123 456 789 09"), out); assertTrue(out.contains("[CPF]"), out); assertFalse(PiiMasker.containsStructuredPii(out)); }
    @Test void m1_phoneWithoutHyphenIsMasked(){ String out = PiiMasker.mask("me liguem no 48 99999 1234 sobre a conta", List.of()); assertFalse(out.contains("99999"), out); assertFalse(PiiMasker.containsStructuredPii(out)); }
    @Test void m1_spacedCepIsMasked(){ String out = PiiMasker.mask("cep 88010 010 e cep 88010-010", List.of()); assertFalse(out.contains("88010"), out); }
    @Test void m1_detectorCoversPhoneAndCepInAnySpelling(){
        assertTrue(PiiMasker.containsStructuredPii("ligue (48) 99999-1234"));
        assertTrue(PiiMasker.containsStructuredPii("ligue 48 99999 1234"));
        assertTrue(PiiMasker.containsStructuredPii("cep 88010-010"));
        assertTrue(PiiMasker.containsStructuredPii("meu cpf e 1 2 3 4 5 6 7 8 9 0 9")); // grafia exotica: 2a camada do detector
        assertFalse(PiiMasker.containsStructuredPii("leitura de 30 dias, consumo de 12 m3"));
    }
    @Test void m1_serviceNeverSendsSpacedIdentifiersToModel(){
        var p=new Ed43TestSupport.Params(); var m=new CountingModel();
        var r=svc(p,m,new Ed43TestSupport.Usage(),new Ed43TestSupport.Logs())
            .chat(Ed43TestSupport.T, req("meu cpf e 123 456 789 09 e me liguem no 48 99999 1234","HELP_SEARCH",Set.of("portal.help.read")));
        assertEquals(ChatbotService.Outcome.ANSWERED, r.outcome()); assertEquals(1, m.calls.get());
        String sent = m.last.get().messages().get(0).maskedText();
        assertFalse(sent.contains("123 456 789 09"), sent); assertFalse(sent.contains("99999"), sent);
    }
    @Test void m1_copilotSpacedCpfIsMasked(){ String s = AttendantCopilot.summarizeInput(List.of("cpf 123 456 789 09 relatado"), List.of()); assertFalse(s.contains("123 456 789 09"), s); }

    // ---------- M2: simetria de saida — reply nunca sai crua ----------
    @Test void m2_outputWithPhoneIsDiscarded(){
        var p=new Ed43TestSupport.Params(); var m=new CountingModel(); m.out="ligaremos para (48) 99999-1234 sobre a conta.";
        var r=svc(p,m,new Ed43TestSupport.Usage(),new Ed43TestSupport.Logs()).chat(Ed43TestSupport.T, req("qual minha conta?","HELP_SEARCH",Set.of("portal.help.read")));
        assertEquals(ChatbotService.Outcome.OUTPUT_DISCARDED_LEAK, r.outcome()); assertNull(r.reply());
    }
    @Test void m2_outputWithKnownNameIsDiscarded(){
        var p=new Ed43TestSupport.Params(); var m=new CountingModel(); m.out="Maria Silva, sua conta foi analisada.";
        var r=svc(p,m,new Ed43TestSupport.Usage(),new Ed43TestSupport.Logs()).chat(Ed43TestSupport.T, req("qual minha conta?","HELP_SEARCH",Set.of("portal.help.read")));
        assertEquals(ChatbotService.Outcome.OUTPUT_DISCARDED_LEAK, r.outcome()); assertNull(r.reply());
    }
    @Test void m2_outputWithCepIsDiscarded(){
        var p=new Ed43TestSupport.Params(); var m=new CountingModel(); m.out="a unidade do cep 88010-010 foi localizada.";
        var r=svc(p,m,new Ed43TestSupport.Usage(),new Ed43TestSupport.Logs()).chat(Ed43TestSupport.T, req("qual minha conta?","HELP_SEARCH",Set.of("portal.help.read")));
        assertEquals(ChatbotService.Outcome.OUTPUT_DISCARDED_LEAK, r.outcome()); assertNull(r.reply());
    }
    @Test void m2_answeredReplyIsTheMaskedVersion(){
        var p=new Ed43TestSupport.Params(); var m=new CountingModel(); m.out="Sobre a conta: o periodo de leitura foi de 30 dias.";
        var l=new Ed43TestSupport.Logs();
        var r=svc(p,m,new Ed43TestSupport.Usage(),l).chat(Ed43TestSupport.T, req("qual minha conta?","HELP_SEARCH",Set.of("portal.help.read")));
        assertEquals(ChatbotService.Outcome.ANSWERED, r.outcome());
        assertEquals(l.rows.get(0).maskedReplyText(), r.reply());
    }

    // ---------- M3: avaliacao de prompt imposta ----------
    @Test void m3_promptNotRegisteredRefusesNamedWithoutModelCall(){
        var p=new Ed43TestSupport.Params(); var m=new CountingModel();
        var reg=new ModelPromptRegistry(); reg.registerModel(new ModelPromptRegistry.ModelVersion(p.model,"current",true));
        var s=new ChatbotService(p, t->Optional.of(Ed43TestSupport.okReg()), m, reg, new Ed43TestSupport.Logs(), new Ed43TestSupport.Usage(),
            Clock.fixed(Instant.parse("2026-11-08T12:00:00Z"), ZoneOffset.UTC), ()->0L, ()->BigDecimal.ZERO, Ed43TestSupport.billExplain(p), Ed43TestSupport.billContext());
        var r=s.chat(Ed43TestSupport.T, req("qual minha conta?","EXPLAIN_BILL",Set.of("portal.bill.read")));
        assertEquals(ChatbotService.Outcome.PROMPT_NOT_EVALUATED, r.outcome()); assertEquals("prompt-not-evaluated", r.handoverReason()); assertEquals(0, m.calls.get());
    }
    @Test void m3_promptRegisteredButNotEvaluatedRefuses(){
        var p=new Ed43TestSupport.Params(); var m=new CountingModel();
        var reg=new ModelPromptRegistry(); reg.registerModel(new ModelPromptRegistry.ModelVersion(p.model,"current",true));
        reg.registerPrompt(new ModelPromptRegistry.PromptVersion("chatbot","current","tpl",false));
        var s=new ChatbotService(p, t->Optional.of(Ed43TestSupport.okReg()), m, reg, new Ed43TestSupport.Logs(), new Ed43TestSupport.Usage(),
            Clock.fixed(Instant.parse("2026-11-08T12:00:00Z"), ZoneOffset.UTC), ()->0L, ()->BigDecimal.ZERO);
        assertEquals(ChatbotService.Outcome.PROMPT_NOT_EVALUATED, s.chat(Ed43TestSupport.T, req("oi","HELP_SEARCH",Set.of("portal.help.read"))).outcome());
        assertEquals(0, m.calls.get());
    }

    // ---------- M4: EXPLAIN_BILL despacha pelo deterministico ED-38 ----------
    @Test void m4_explainBillModelDraftsOverDeterministicEd38Result(){
        var p=new Ed43TestSupport.Params(); var m=new CountingModel();
        var r=svc(p,m,new Ed43TestSupport.Usage(),new Ed43TestSupport.Logs())
            .chat(Ed43TestSupport.T, req("por que minha conta aumentou?","EXPLAIN_BILL",Set.of("portal.bill.read")));
        assertEquals(ChatbotService.Outcome.ANSWERED, r.outcome()); assertEquals(1, m.calls.get());
        String sent = m.last.get().messages().get(0).maskedText();
        assertTrue(sent.contains("Periodo de leitura de 30 dias"), sent);
        assertTrue(sent.contains("FAIXA-2"), sent);
        assertFalse(sent.contains("aumentou"), sent); // a pergunta crua NAO e a entrada: quem entra e o resultado deterministico
    }
    @Test void m4_explainBillWithoutWiringTransfersFailClosed(){
        var p=new Ed43TestSupport.Params(); var m=new CountingModel();
        var s=new ChatbotService(p, t->Optional.of(Ed43TestSupport.okReg()), m, Ed43TestSupport.registryOk(p), new Ed43TestSupport.Logs(), new Ed43TestSupport.Usage(),
            Clock.fixed(Instant.parse("2026-11-08T12:00:00Z"), ZoneOffset.UTC), ()->0L, ()->BigDecimal.ZERO);
        var r=s.chat(Ed43TestSupport.T, req("por que minha conta aumentou?","EXPLAIN_BILL",Set.of("portal.bill.read")));
        assertEquals(ChatbotService.Outcome.TRANSFERRED_TO_HUMAN, r.outcome()); assertEquals("bill-explain-not-wired", r.handoverReason()); assertEquals(0, m.calls.get());
    }
    @Test void m4_explainBillParamOffTransfersThroughService(){
        var p=new Ed43TestSupport.Params(); p.bill=false; var m=new CountingModel();
        var r=svc(p,m,new Ed43TestSupport.Usage(),new Ed43TestSupport.Logs())
            .chat(Ed43TestSupport.T, req("por que minha conta aumentou?","EXPLAIN_BILL",Set.of("portal.bill.read")));
        assertEquals(ChatbotService.Outcome.TRANSFERRED_TO_HUMAN, r.outcome()); assertEquals("bill-explain-unavailable", r.handoverReason()); assertEquals(0, m.calls.get());
    }
    @Test void m4_explainBillMissingContextTransfers(){
        var p=new Ed43TestSupport.Params(); var m=new CountingModel();
        var s=new ChatbotService(p, t->Optional.of(Ed43TestSupport.okReg()), m, Ed43TestSupport.registryOk(p), new Ed43TestSupport.Logs(), new Ed43TestSupport.Usage(),
            Clock.fixed(Instant.parse("2026-11-08T12:00:00Z"), ZoneOffset.UTC), ()->0L, ()->BigDecimal.ZERO, Ed43TestSupport.billExplain(p), (t,c)->Optional.empty());
        var r=s.chat(Ed43TestSupport.T, req("por que minha conta aumentou?","EXPLAIN_BILL",Set.of("portal.bill.read")));
        assertEquals(ChatbotService.Outcome.TRANSFERRED_TO_HUMAN, r.outcome()); assertEquals("bill-context-missing", r.handoverReason()); assertEquals(0, m.calls.get());
    }

    // ---------- m1: chamada descartada tambem e medida ----------
    @Test void m1_discardedLeakCallIsMeasuredInEd41(){
        var p=new Ed43TestSupport.Params(); var m=new CountingModel(); m.out="seu cpf e 123.456.789-09"; m.tokens=7;
        var u=new Ed43TestSupport.Usage();
        var r=svc(p,m,u,new Ed43TestSupport.Logs()).chat(Ed43TestSupport.T, req("qual minha conta?","HELP_SEARCH",Set.of("portal.help.read")));
        assertEquals(ChatbotService.Outcome.OUTPUT_DISCARDED_LEAK, r.outcome());
        assertEquals(1, u.rows.size()); assertEquals(UsageMetric.AI_TOKENS, u.rows.get(0).metric()); assertEquals(7, u.rows.get(0).quantity());
    }

    // ---------- m2: cota/teto fail-closed + reconciliacao real ----------
    @Test void m2_spentNullOrEstimateNullDoesNotSkipCeiling(){
        assertEquals(AiQuotaRule.Verdict.CEILING_EXCEEDED, AiQuotaRule.check(100L,0,5,new BigDecimal("10"), null, new BigDecimal("1")));
        assertEquals(AiQuotaRule.Verdict.CEILING_EXCEEDED, AiQuotaRule.check(100L,0,5,new BigDecimal("10"), BigDecimal.ZERO, null));
        assertEquals(AiQuotaRule.Verdict.ALLOWED, AiQuotaRule.check(100L,0,5,new BigDecimal("10"), BigDecimal.ZERO, new BigDecimal("1")));
    }
    @Test void m2_serviceSpentNullBlocksWithoutModelCall(){
        var p=new Ed43TestSupport.Params(); var m=new CountingModel();
        var s=new ChatbotService(p, t->Optional.of(Ed43TestSupport.okReg()), m, Ed43TestSupport.registryOk(p), new Ed43TestSupport.Logs(), new Ed43TestSupport.Usage(),
            Clock.fixed(Instant.parse("2026-11-08T12:00:00Z"), ZoneOffset.UTC), ()->0L, ()->null);
        var r=s.chat(Ed43TestSupport.T, req("qual minha conta?","HELP_SEARCH",Set.of("portal.help.read")));
        assertEquals(ChatbotService.Outcome.QUOTA_BLOCKED, r.outcome()); assertEquals(0, m.calls.get());
    }
    @Test void m2_realCostAboveCeilingBlocksAfterCallButMeasures(){
        var p=new Ed43TestSupport.Params(); p.ceiling=new BigDecimal("0.30"); var m=new CountingModel(); m.cost=new BigDecimal("0.50");
        var u=new Ed43TestSupport.Usage();
        var r=svc(p,m,u,new Ed43TestSupport.Logs()).chat(Ed43TestSupport.T, req("qual minha conta?","HELP_SEARCH",Set.of("portal.help.read")));
        assertEquals(ChatbotService.Outcome.QUOTA_BLOCKED, r.outcome()); assertNull(r.reply()); assertEquals(42, r.tokensUsed());
        assertEquals(1, u.rows.size());
    }
    @Test void m2_realTokensAboveQuotaBlockAfterCallButMeasure(){
        var p=new Ed43TestSupport.Params(); p.quota=550L; var m=new CountingModel(); m.tokens=600; m.cost=BigDecimal.ZERO;
        var u=new Ed43TestSupport.Usage();
        var r=svc(p,m,u,new Ed43TestSupport.Logs()).chat(Ed43TestSupport.T, req("qual minha conta?","HELP_SEARCH",Set.of("portal.help.read")));
        assertEquals(ChatbotService.Outcome.QUOTA_BLOCKED, r.outcome()); assertEquals(1, u.rows.size()); assertEquals(600, u.rows.get(0).quantity());
    }
    @Test void m2_reconcilesUnitRule(){
        assertTrue(AiQuotaRule.reconciles(1000L, 0, 100, new BigDecimal("10"), BigDecimal.ZERO, new BigDecimal("1")));
        assertFalse(AiQuotaRule.reconciles(1000L, 0, 100, new BigDecimal("10"), null, new BigDecimal("1")));
        assertFalse(AiQuotaRule.reconciles(1000L, 950, 100, new BigDecimal("10"), BigDecimal.ZERO, new BigDecimal("1")));
    }

    // ---------- m3: human_review_required e intent ligados ao fluxo ----------
    @Test void m3_paraphrasesOfForbiddenTopicsTransfer(){
        var p=new Ed43TestSupport.Params(); var m=new CountingModel(); var s=svc(p,m,new Ed43TestSupport.Usage(),new Ed43TestSupport.Logs());
        assertEquals(ChatbotService.Outcome.TRANSFERRED_TO_HUMAN, s.chat(Ed43TestSupport.T, req("quero o desligamento do fornecimento","PROTOCOL_STATUS",Set.of("portal.protocol.read"))).outcome());
        assertEquals(ChatbotService.Outcome.TRANSFERRED_TO_HUMAN, s.chat(Ed43TestSupport.T, req("vou ficar com o nome sujo por causa disso","PROTOCOL_STATUS",Set.of("portal.protocol.read"))).outcome());
        assertEquals(0, m.calls.get());
    }
    @Test void m3_humanReviewRequiredLockIsNamedInService(){
        var p=new Ed43TestSupport.Params(); var m=new CountingModel(); // Params de prova: humanReviewRequired == true (travado)
        var r=svc(p,m,new Ed43TestSupport.Usage(),new Ed43TestSupport.Logs())
            .chat(Ed43TestSupport.T, req("vou ser negativado?","PROTOCOL_STATUS",Set.of("portal.protocol.read")));
        assertEquals(ChatbotService.Outcome.TRANSFERRED_TO_HUMAN, r.outcome()); assertEquals("human-review-required-locked", r.handoverReason());
        var d = HumanHandover.decide("vou ser negativado?", false, true, false, SentimentAndIntent.Sentiment.NEUTRAL, false, true, false);
        assertEquals("human-review-required-locked", d.reason());
        var d2 = HumanHandover.decide("vou ser negativado?", false, true, false, SentimentAndIntent.Sentiment.NEUTRAL, false, false, false);
        assertEquals("forbidden-decision-human-review-locked", d2.reason()); // sem a trava, a regua dura decide com o nome dela
    }
    @Test void m3_newIntentTransfersForCurationEvenWithFunction(){
        var p=new Ed43TestSupport.Params(); var m=new CountingModel();
        var rq=new ChatbotService.ChatRequest(UUID.randomUUID(), "quero comprar acoes", "QUERO_COMPRAR_ACOES", "HELP_SEARCH", Set.of("portal.help.read"), false, List.of(), 1, List.of());
        var r=svc(p,m,new Ed43TestSupport.Usage(),new Ed43TestSupport.Logs()).chat(Ed43TestSupport.T, rq);
        assertEquals(ChatbotService.Outcome.TRANSFERRED_TO_HUMAN, r.outcome()); assertEquals("new-intent-curation", r.handoverReason()); assertEquals(0, m.calls.get());
    }

    // ---------- m4: ramo de mascara-falhou grava log (SG-701) ----------
    @Test void m4_maskingFailedBranchLogsRetainedMarker(){
        var p=new Ed43TestSupport.Params(); var m=new CountingModel(); var l=new Ed43TestSupport.Logs();
        var r=svc(p,m,new Ed43TestSupport.Usage(),l)
            .chat(Ed43TestSupport.T, req("meu cpf e 1 2 3 4 5 6 7 8 9 0 9, ajuda","HELP_SEARCH",Set.of("portal.help.read")));
        assertEquals(ChatbotService.Outcome.TRANSFERRED_TO_HUMAN, r.outcome()); assertEquals("masking-failed-fail-closed", r.handoverReason());
        assertEquals(1, l.rows.size()); assertEquals("MASKING_FAILED_FAIL_CLOSED", l.rows.get(0).outcome());
        assertEquals("[CONTEUDO-RETIDO]", l.rows.get(0).maskedUserText()); // o log NUNCA guarda o texto ofensor
        assertEquals(0, m.calls.get());
    }
    @Test void m4_transferLogNeverStoresResidualPii(){
        var p=new Ed43TestSupport.Params(); var m=new CountingModel(); var l=new Ed43TestSupport.Logs();
        var r=svc(p,m,new Ed43TestSupport.Usage(),l)
            .chat(Ed43TestSupport.T, req("vou ser negativado, cpf 1 2 3 4 5 6 7 8 9 0 9","PROTOCOL_STATUS",Set.of("portal.protocol.read")));
        assertEquals(ChatbotService.Outcome.TRANSFERRED_TO_HUMAN, r.outcome());
        assertEquals(1, l.rows.size()); assertEquals("[CONTEUDO-RETIDO]", l.rows.get(0).maskedUserText());
    }

    // ---------- m5: rascunho ganha transicao para revisado (OPE-40) ----------
    @Test void m5_draftReviewTransitionMakesItUsable(){
        var svcDraft = new AssistDraftService(new Ed43TestSupport.Params());
        var d = svcDraft.osOpinionDraft(Ed43TestSupport.T, "parecer da os");
        assertThrows(IllegalStateException.class, d::requireReviewed); // nasce nao revisado
        var reviewed = svcDraft.reviewByExecutor(d);
        assertTrue(reviewed.reviewedByExecutor()); assertSame(reviewed, reviewed.requireReviewed());
        assertFalse(d.reviewedByExecutor()); // original imutavel, continua nao revisado
        var d2 = svcDraft.shiftSummaryDraft(Ed43TestSupport.T, "resumo do turno");
        var reviewed2 = d2.markReviewedByExecutor();
        assertSame(reviewed2, reviewed2.requireReviewed());
    }

    // ---------- m6: retencao com chamador real + SQL (guarda/unicidade) ----------
    @Test void m6_retentionSweeperSelectsOnlyExpired(){
        var now = Instant.parse("2026-11-08T12:00:00Z");
        var old = new AiConversationLog(Ed43TestSupport.T, UUID.randomUUID(), AiFeature.CHATBOT, "m", "chatbot@current", "texto", null, 1, "ANSWERED", false, now.minusSeconds(100L*86400));
        var fresh = new AiConversationLog(Ed43TestSupport.T, UUID.randomUUID(), AiFeature.CHATBOT, "m", "chatbot@current", "texto", null, 1, "ANSWERED", false, now.minusSeconds(10L*86400));
        assertEquals(List.of(old), AiRetentionSweeper.expiredAmong(List.of(old, fresh), 90, now));
        var purged = new ArrayList<AiConversationLog>();
        assertEquals(1, AiRetentionSweeper.sweep(List.of(old, fresh), 90, now, purged::add));
        assertEquals(List.of(old), purged);
        assertEquals(0, AiRetentionSweeper.sweep(List.of(old, fresh), 0, now, purged::add)); // retencao desligada: nada vence
    }
    @Test void m6_sqlHasRerunGuardAndTenantUniqueness() throws Exception {
        String rel = "workspace/ziipsan-producao/ed-43/entrega/platform/ziipsan-platform/src/main/resources/db/pendente/ED-43__ai_chatbot.sql";
        java.nio.file.Path path = null;
        for (String home : new String[]{System.getenv("HOME"), System.getProperty("user.home"), "/home/hatch"}) {
            if (home == null) continue;
            var candidate = java.nio.file.Path.of(home, rel);
            if (java.nio.file.Files.exists(candidate)) { path = candidate; break; }
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(path != null, "SQL da entrega fora desta VM");
        String sql = java.nio.file.Files.readString(path);
        assertTrue(sql.contains("pg_constraint"), "guarda de reexecucao ausente");
        assertTrue(sql.contains("ck_ai_foreign_needs_clauses"));
        assertTrue(sql.contains("uq_ai_provider_registrations_tenant"));
        assertTrue(sql.contains("UNIQUE (tenant_id)"));
    }
}
