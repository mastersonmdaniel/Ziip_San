package br.com.ziipsan.platform.ai;
import br.com.ziipsan.platform.notification.multichannel.*;
import br.com.ziipsan.platform.portal.selfcare.*;
import java.math.BigDecimal; import java.time.*; import java.util.*;
final class Ed43TestSupport {
    static final UUID T = UUID.randomUUID();
    static final UUID BILL_ID = UUID.randomUUID();
    static final class Params implements AiParameters {
        Set<AiFeature> features = EnumSet.allOf(AiFeature.class); String region="BR"; String model="model-x";
        String cred="vault://ai/main"; Long quota=100000L; BigDecimal ceiling=new BigDecimal("100.00"); boolean bill=true; boolean voice=true; String assist="assist-x";
        public Set<AiFeature> enabledFeatures(UUID t){return features;}
        public Optional<String> providerRegion(UUID t){return Optional.ofNullable(region);}
        public Optional<Integer> logRetentionDays(UUID t){return Optional.of(90);}
        public boolean humanReviewRequired(UUID t, AiFeature f){return true;}
        public boolean billExplainEnabled(UUID t){return bill;}
        public boolean voiceEnabled(UUID t){return voice;}
        public Optional<String> assistProvider(UUID t){return Optional.ofNullable(assist);}
        public Optional<String> modelId(UUID t){return Optional.ofNullable(model);}
        public Optional<String> credentialReference(UUID t){return Optional.ofNullable(cred);}
        public Optional<Long> tokenQuota(UUID t){return Optional.ofNullable(quota);}
        public Optional<BigDecimal> costCeiling(UUID t){return Optional.ofNullable(ceiling);}
    }
    static final class Usage implements UsageStore { final List<UsageRecord> rows=new ArrayList<>();
        public void append(UsageRecord r){rows.add(r);} public List<UsageRecord> query(UUID t,Instant a,Instant b){return rows;} }
    static final class Logs implements AiConversationLog.LogStore { final List<AiConversationLog> rows=new ArrayList<>();
        public void append(AiConversationLog l){rows.add(l);} public List<AiConversationLog> byConversation(UUID t,UUID c){return rows;} }
    static AiProviderGate.ProviderRegistration okReg(){ return new AiProviderGate.ProviderRegistration(true,true,false,true,true,"BR"); }
    static ModelPromptRegistry registryOk(Params p){ var r=new ModelPromptRegistry(); r.registerModel(new ModelPromptRegistry.ModelVersion(p.model,"current",true)); r.registerPrompt(new ModelPromptRegistry.PromptVersion("chatbot","current","tpl",true)); return r; }
    /** Calculo gravado de prova (mesmo do BillCopilotAssistTest): componentes fecham com o total. */
    static BillExplanation.RecordedCalculation calc(){ return new BillExplanation.RecordedCalculation(30,30,new BigDecimal("12"),new BigDecimal("10"),new BigDecimal("11"),"FAIXA-2",BigDecimal.ZERO,new BigDecimal("60"),new BigDecimal("48"),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("108")); }
    /** BillExplainFunction REAL sobre o AssistantBillFunction REAL da ED-38, com portas de prova. */
    static BillExplainFunction billExplain(Params p){
        AssistantBillFunction fn = AssistantBillFunction.over((t,id)->Optional.of(calc()), (t,s,id)->true);
        return new BillExplainFunction(p, fn);
    }
    static ChatbotService.BillContextPort billContext(){ return (t,c)->Optional.of(new ChatbotService.BillRef("consumer-1", BILL_ID)); }
    static ChatbotService service(Params p, ModelPort m, Usage u, Logs l){
        return new ChatbotService(p, t->Optional.of(okReg()), m, registryOk(p), l, u, Clock.fixed(Instant.parse("2026-11-08T12:00:00Z"),ZoneOffset.UTC), ()->0L, ()->BigDecimal.ZERO, billExplain(p), billContext());
    }
    static ChatbotService.ChatRequest req(String text, String fn, Set<String> perms){
        return new ChatbotService.ChatRequest(UUID.randomUUID(), text, "EXPLICAR_CONTA", fn, perms, false, List.of("Maria Silva","Rua das Flores, 123"), 1, List.of());
    }
}
