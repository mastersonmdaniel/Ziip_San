package br.com.ziipsan.platform.ai;
import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*; import java.util.Set;
class ClosedFunctionAndHandoverTest {
    @Test void functionOutsideListRefused(){ assertFalse(ClosedFunctionRegistry.allowed("TRANSFER_MONEY", Set.of("portal.bill.read"))); assertFalse(ClosedFunctionRegistry.isClosed("TRANSFER_MONEY")); }
    @Test void functionWithoutConsumerPermissionRefused(){ assertFalse(ClosedFunctionRegistry.allowed("EXPLAIN_BILL", Set.of("portal.help.read"))); assertTrue(ClosedFunctionRegistry.allowed("EXPLAIN_BILL", Set.of("portal.bill.read"))); }
    @Test void humanRequestAlwaysTransfersEvenWithFunction(){
        var d = HumanHandover.decide("quero falar com humano", false, true, false, SentimentAndIntent.Sentiment.NEUTRAL, false);
        assertTrue(d.transfer()); assertEquals("human-requested", d.reason());
    }
    @Test void forbiddenAndInjectionAndNoFunctionTransfer(){
        assertTrue(HumanHandover.decide("vao cortar minha agua?", false, true, false, SentimentAndIntent.Sentiment.NEUTRAL, false).transfer());
        assertTrue(HumanHandover.decide("oi", false, true, true, SentimentAndIntent.Sentiment.NEUTRAL, false).transfer());
        assertTrue(HumanHandover.decide("oi", false, false, false, SentimentAndIntent.Sentiment.NEUTRAL, false).transfer());
    }
    @Test void sentimentNewIntentAndAnomaly(){
        assertEquals(SentimentAndIntent.Sentiment.NEGATIVE, SentimentAndIntent.sentiment("isso e um absurdo, vou ao procon"));
        assertTrue(SentimentAndIntent.isNewIntent("QUERO_COMPRAR_ACOES"));
        assertTrue(SentimentAndIntent.anomalous(25, java.util.List.of()));
        assertTrue(SentimentAndIntent.anomalous(3, java.util.List.of("x","x","x")));
    }
    @Test void referenceRegressionSetPassesFully(){ assertEquals(AiRegressionRunner.referenceSet().size(), AiRegressionRunner.runAll()); }
}
