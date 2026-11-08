package br.com.ziipsan.core.certification.env14001;

import br.com.ziipsan.core.sanitation.SanitationAspectCatalog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnvironmentalAspectTest {

    @Test
    void aspectFromFactoryTemplate() {
        EnvironmentalAspect a = EnvironmentalAspect.fromTemplate(TestSupport.enabledTarget(),
                TestSupport.id(), TestSupport.TENANT, "WWTP-EFFLUENT-DISCHARGE",
                EnvironmentalAspect.Situation.NORMAL);
        assertThat(a.area()).isEqualTo(SanitationAspectCatalog.AspectArea.WWTP);
        assertThat(a.sludgeLinked()).isFalse();
        assertThat(a.situation()).isEqualTo(EnvironmentalAspect.Situation.NORMAL);
    }

    @Test
    void allThreeSituationsAccepted() {
        for (EnvironmentalAspect.Situation s : EnvironmentalAspect.Situation.values()) {
            EnvironmentalAspect a = EnvironmentalAspect.declareCustom(TestSupport.enabledTarget(),
                    TestSupport.id(), TestSupport.TENANT, SanitationAspectCatalog.AspectArea.NETWORK,
                    "SYN-aspecto " + s, "SYN-impacto " + s, s);
            assertThat(a.situation()).isEqualTo(s);
        }
        assertThat(EnvironmentalAspect.Situation.values())
                .containsExactly(EnvironmentalAspect.Situation.NORMAL,
                        EnvironmentalAspect.Situation.ABNORMAL,
                        EnvironmentalAspect.Situation.EMERGENCY);
    }

    @Test
    void sludgeAspectOnlyViaSludgeTemplate() {
        EnvironmentalAspect a = EnvironmentalAspect.fromTemplate(TestSupport.enabledTarget(),
                TestSupport.id(), TestSupport.TENANT, "WTP-SLUDGE",
                EnvironmentalAspect.Situation.NORMAL);
        assertThat(a.sludgeLinked()).isTrue();
        // declareCustom nunca marca sludgeLinked:
        EnvironmentalAspect custom = EnvironmentalAspect.declareCustom(TestSupport.enabledTarget(),
                TestSupport.id(), TestSupport.TENANT, SanitationAspectCatalog.AspectArea.WTP,
                "SYN-aspecto livre", "SYN-impacto", EnvironmentalAspect.Situation.NORMAL);
        assertThat(custom.sludgeLinked()).isFalse();
        // a tentativa direta pelo construtor, marcando lodo sem template, recusa:
        assertThatThrownBy(() -> new EnvironmentalAspect(TestSupport.enabledTarget(),
                TestSupport.id(), TestSupport.TENANT, null, SanitationAspectCatalog.AspectArea.WTP,
                "SYN-lodo livre", "SYN-impacto", EnvironmentalAspect.Situation.NORMAL, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ED-105");
    }

    @Test
    void templateAreaMismatchAndUnknownTemplateRefused() {
        assertThatThrownBy(() -> new EnvironmentalAspect(TestSupport.enabledTarget(),
                TestSupport.id(), TestSupport.TENANT, "WTP-SLUDGE",
                SanitationAspectCatalog.AspectArea.NETWORK, "SYN-a", "SYN-i",
                EnvironmentalAspect.Situation.NORMAL, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EnvironmentalAspect.fromTemplate(TestSupport.enabledTarget(),
                TestSupport.id(), TestSupport.TENANT, "SYN-DESCONHECIDO",
                EnvironmentalAspect.Situation.NORMAL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void disabledNormRefusesAspectBirthByFactoryAndCanonicalConstructor() {
        assertThatThrownBy(() -> EnvironmentalAspect.fromTemplate(TestSupport.disabled(),
                TestSupport.id(), TestSupport.TENANT, "WTP-SLUDGE",
                EnvironmentalAspect.Situation.NORMAL))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new EnvironmentalAspect(TestSupport.disabled(),
                TestSupport.id(), TestSupport.TENANT, null,
                SanitationAspectCatalog.AspectArea.WTP, "SYN-a", "SYN-i",
                EnvironmentalAspect.Situation.NORMAL, false))
                .isInstanceOf(IllegalStateException.class);
    }
}
