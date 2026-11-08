package br.com.ziipsan.platform.ai;
import br.com.ziipsan.platform.portal.selfcare.*; import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal; import java.util.*;
class BillCopilotAssistTest {
    private BillExplanation.RecordedCalculation calc(){ return new BillExplanation.RecordedCalculation(30,30,new BigDecimal("12"),new BigDecimal("10"),new BigDecimal("11"),"FAIXA-2",BigDecimal.ZERO,new BigDecimal("60"),new BigDecimal("48"),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("108")); }
    @Test void billExplainDelegatesToDeterministicEd38WhenEnabled(){
        var p=new Ed43TestSupport.Params(); AssistantBillFunction fn = AssistantBillFunction.over((t,id)->Optional.of(calc()), (t,s,id)->true);
        var out = new BillExplainFunction(p, fn).explain(Ed43TestSupport.T, "consumer-1", UUID.randomUUID());
        assertTrue(out.fullText().contains("Periodo de leitura de 30 dias"));
    }
    @Test void billExplainRefusedWhenParamOff(){
        var p=new Ed43TestSupport.Params(); p.bill=false; AssistantBillFunction fn = AssistantBillFunction.over((t,id)->Optional.of(calc()), (t,s,id)->true);
        assertThrows(IllegalStateException.class, ()-> new BillExplainFunction(p, fn).explain(Ed43TestSupport.T,"c",UUID.randomUUID()));
    }
    @Test void copilotSummaryIsMasked(){ String s = AttendantCopilot.summarizeInput(List.of("Maria Silva cpf 123.456.789-09 relatou vazamento"), List.of("Maria Silva")); assertFalse(s.contains("123.456.789-09")); assertFalse(s.contains("Maria Silva")); }
    @Test void osOpinionTruncatesTo400AndRequiresReview(){
        var svc = new AssistDraftService(new Ed43TestSupport.Params());
        var d = svc.osOpinionDraft(Ed43TestSupport.T, "x".repeat(500)); assertEquals(400, d.text().length());
        assertThrows(IllegalStateException.class, d::requireReviewed);
    }
    @Test void dictationRequiresVoiceAndProvider(){
        var p=new Ed43TestSupport.Params(); p.assist=null; assertThrows(IllegalStateException.class, ()-> new AssistDraftService(p).requireDictationReady(Ed43TestSupport.T));
        var p2=new Ed43TestSupport.Params(); p2.voice=false; assertThrows(IllegalStateException.class, ()-> new AssistDraftService(p2).requireDictationReady(Ed43TestSupport.T));
    }
}
