package br.com.ziipsan.platform.ai;
import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
class ProviderGateTest {
    @Test void foreignWithoutStandardClausesBlocks(){
        var r = new AiProviderGate.ProviderRegistration(true,true,true,false,true,"US");
        assertEquals(AiProviderGate.Verdict.FOREIGN_WITHOUT_STANDARD_CLAUSES, AiProviderGate.check(r,"US"));
    }
    @Test void foreignWithClausesAndAllRegistrationsAllowed(){
        var r = new AiProviderGate.ProviderRegistration(true,true,true,true,true,"US");
        assertEquals(AiProviderGate.Verdict.ALLOWED, AiProviderGate.check(r,"US"));
    }
    @Test void missingDpaRipdOrTrainingAttestationBlocks(){
        assertEquals(AiProviderGate.Verdict.NOT_IN_DPA, AiProviderGate.check(new AiProviderGate.ProviderRegistration(false,true,false,true,true,"BR"),"BR"));
        assertEquals(AiProviderGate.Verdict.NOT_IN_RIPD, AiProviderGate.check(new AiProviderGate.ProviderRegistration(true,false,false,true,true,"BR"),"BR"));
        assertEquals(AiProviderGate.Verdict.TRAINING_NOT_ATTESTED, AiProviderGate.check(new AiProviderGate.ProviderRegistration(true,true,false,true,false,"BR"),"BR"));
    }
    @Test void registryRequiresEvaluatedModelAndPrompt(){
        var r = new ModelPromptRegistry(); r.registerModel(new ModelPromptRegistry.ModelVersion("m","v1",false));
        assertFalse(r.usableModel("m","v1")); r.registerModel(new ModelPromptRegistry.ModelVersion("m","v2",true)); assertTrue(r.usableModel("m","v2"));
    }
}
