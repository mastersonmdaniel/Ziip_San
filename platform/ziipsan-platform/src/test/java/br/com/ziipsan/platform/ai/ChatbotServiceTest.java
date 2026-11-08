package br.com.ziipsan.platform.ai;
import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*; import java.util.*;
class ChatbotServiceTest {
    @Test void answersViaClosedFunctionMasksAndMeasuresAiTokens(){
        var p=new Ed43TestSupport.Params(); var u=new Ed43TestSupport.Usage(); var l=new Ed43TestSupport.Logs();
        var svc=Ed43TestSupport.service(p, ModelPort.honestStub(), u, l);
        var r=svc.chat(Ed43TestSupport.T, Ed43TestSupport.req("Maria Silva cpf 123.456.789-09: por que a conta aumentou?","EXPLAIN_BILL",Set.of("portal.bill.read")));
        assertEquals(ChatbotService.Outcome.ANSWERED, r.outcome());
        assertEquals(1, u.rows.size()); assertEquals(br.com.ziipsan.platform.notification.multichannel.UsageMetric.AI_TOKENS, u.rows.get(0).metric());
        assertFalse(l.rows.get(0).maskedUserText().contains("123.456.789-09")); assertFalse(l.rows.get(0).maskedUserText().contains("Maria Silva"));
    }
    @Test void featureDisabledByDefaultPatternBlocks(){
        var p=new Ed43TestSupport.Params(); p.features=EnumSet.noneOf(AiFeature.class);
        var svc=Ed43TestSupport.service(p, ModelPort.honestStub(), new Ed43TestSupport.Usage(), new Ed43TestSupport.Logs());
        assertEquals(ChatbotService.Outcome.FEATURE_DISABLED, svc.chat(Ed43TestSupport.T, Ed43TestSupport.req("oi","HELP_SEARCH",Set.of("portal.help.read"))).outcome());
    }
    @Test void noProviderConfiguredBlocksWithoutDefault(){
        var p=new Ed43TestSupport.Params(); p.model=null; p.cred=null;
        var svc=Ed43TestSupport.service(p, ModelPort.honestStub(), new Ed43TestSupport.Usage(), new Ed43TestSupport.Logs());
        assertEquals(ChatbotService.Outcome.NO_CREDENTIAL, svc.chat(Ed43TestSupport.T, Ed43TestSupport.req("oi","HELP_SEARCH",Set.of("portal.help.read"))).outcome());
    }
    @Test void humanAndForbiddenTransferThroughService(){
        var p=new Ed43TestSupport.Params(); var svc=Ed43TestSupport.service(p, ModelPort.honestStub(), new Ed43TestSupport.Usage(), new Ed43TestSupport.Logs());
        assertEquals(ChatbotService.Outcome.TRANSFERRED_TO_HUMAN, svc.chat(Ed43TestSupport.T, Ed43TestSupport.req("falar com humano","REQUEST_HUMAN",Set.of("portal.chat.use"))).outcome());
        assertEquals(ChatbotService.Outcome.TRANSFERRED_TO_HUMAN, svc.chat(Ed43TestSupport.T, Ed43TestSupport.req("vou ser negativado?","PROTOCOL_STATUS",Set.of("portal.protocol.read"))).outcome());
    }
    @Test void leakingModelOutputIsDiscarded(){
        var p=new Ed43TestSupport.Params();
        ModelPort leaky = call -> new ModelPort.ModelResult("seu cpf e 123.456.789-09", 5, java.math.BigDecimal.ZERO);
        var svc=Ed43TestSupport.service(p, leaky, new Ed43TestSupport.Usage(), new Ed43TestSupport.Logs());
        assertEquals(ChatbotService.Outcome.OUTPUT_DISCARDED_LEAK, svc.chat(Ed43TestSupport.T, Ed43TestSupport.req("qual minha conta?","EXPLAIN_BILL",Set.of("portal.bill.read"))).outcome());
    }
    @Test void quotaBlockBeforeModel(){
        var p=new Ed43TestSupport.Params(); p.quota=null;
        var svc=Ed43TestSupport.service(p, ModelPort.honestStub(), new Ed43TestSupport.Usage(), new Ed43TestSupport.Logs());
        assertEquals(ChatbotService.Outcome.QUOTA_BLOCKED, svc.chat(Ed43TestSupport.T, Ed43TestSupport.req("qual minha conta?","EXPLAIN_BILL",Set.of("portal.bill.read"))).outcome());
    }
}
