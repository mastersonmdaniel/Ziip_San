package br.com.ziipsan.core.sanitation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SanitationAspectCatalogTest {

    @Test
    void factoryCatalogCoversWtpWwtpAndNetwork() {
        assertThat(SanitationAspectCatalog.byArea(SanitationAspectCatalog.AspectArea.WTP))
                .isNotEmpty();
        assertThat(SanitationAspectCatalog.byArea(SanitationAspectCatalog.AspectArea.WWTP))
                .isNotEmpty();
        assertThat(SanitationAspectCatalog.byArea(SanitationAspectCatalog.AspectArea.NETWORK))
                .isNotEmpty();
        assertThat(SanitationAspectCatalog.all())
                .extracting(SanitationAspectCatalog.AspectTemplate::code)
                .doesNotHaveDuplicates();
    }

    @Test
    void sludgeTemplatesAreExactlyTheTwoStationSludges() {
        assertThat(SanitationAspectCatalog.sludgeTemplates())
                .extracting(SanitationAspectCatalog.AspectTemplate::code)
                .containsExactlyInAnyOrder("WTP-SLUDGE", "WWTP-SLUDGE");
    }

    @Test
    void byCodeFindsAndMissesHonestly() {
        assertThat(SanitationAspectCatalog.byCode("WWTP-EFFLUENT-DISCHARGE")).isPresent();
        assertThat(SanitationAspectCatalog.byCode("SYN-INEXISTENTE")).isEmpty();
        assertThat(SanitationAspectCatalog.byCode(null)).isEmpty();
    }
}
