package br.com.ziipsan.commercial.billing.content;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Teste de conteudo do modelo por perfil (P2.32): modelo completo aprova;
 * campo faltante RECUSA a aprovacao, nomeando o campo e a norma.
 */
class InvoiceTemplateContentRuleTest {

    private static final LocalDate DATE = LocalDate.of(2026, 6, 1);

    private static Set<String> completeFields(String profileKey) {
        Set<String> fields = new HashSet<>();
        for (BillFieldRequirement f : RequiredBillContent.requiredFor(profileKey, DATE)) {
            fields.add(f.fieldKey());
        }
        return fields;
    }

    private static Set<String> lockedFields(String profileKey) {
        Set<String> locked = new HashSet<>();
        for (BillFieldRequirement f : RequiredBillContent.requiredFor(profileKey, DATE)) {
            if (f.lockedBlock()) {
                locked.add(f.fieldKey());
            }
        }
        return locked;
    }

    @Test
    @DisplayName("modelo completo aprova em todos os 7 perfis da P2.31")
    void completeTemplateApprovesForEveryProfile() {
        for (String profile : RequiredBillContent.profileKeys()) {
            assertThatCode(() -> InvoiceTemplateContentRule.assertApprovable(
                    completeFields(profile), lockedFields(profile), profile, DATE))
                    .as("perfil " + profile)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("ARIS: faltando a divulgacao de encargos (RN 19/2019, art. 108), a aprovacao e recusada")
    void arisMissingChargesDisclosureIsRefused() {
        Set<String> fields = completeFields("ARIS_RN19_2019");
        fields.remove(RequiredBillContent.CHARGES_DISCLOSURE);
        assertThatThrownBy(() -> InvoiceTemplateContentRule.assertApprovable(
                fields, lockedFields("ARIS_RN19_2019"), "ARIS_RN19_2019", DATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(RequiredBillContent.CHARGES_DISCLOSURE)
                .hasMessageContaining("RN 19/2019");
    }

    @Test
    @DisplayName("ARESC e AGIR: o campo de encargos cita o ato de cada perfil (art. 104 x art. 129)")
    void profileFieldsCarryTheirOwnAct() {
        assertThat(RequiredBillContent.profileFields("ARESC_RES046_2016"))
                .anySatisfy(f -> {
                    assertThat(f.fieldKey()).isEqualTo(RequiredBillContent.CHARGES_DISCLOSURE);
                    assertThat(f.sourceReference()).isEqualTo("art. 104");
                });
        assertThat(RequiredBillContent.profileFields("AGIR_RN001_2013"))
                .anySatisfy(f -> {
                    assertThat(f.fieldKey()).isEqualTo(RequiredBillContent.CHARGES_DISCLOSURE);
                    assertThat(f.sourceReference()).isEqualTo("art. 129");
                });
    }

    @Test
    @DisplayName("CISAM-SUL e AGR-Tubarao: so a base federal (texto da ERI nao confirmado nao inventa campo)")
    void unconfirmedProfilesHaveOnlyFederalFields() {
        for (String profile : new String[]{"CISAM_SUL_RN03_2017", "AGR_TUBARAO_RES007_2013"}) {
            assertThat(RequiredBillContent.requiredFor(profile, DATE))
                    .hasSize(RequiredBillContent.federalFields().size());
        }
    }

    @Test
    @DisplayName("bloco do CDC art. 42-A presente mas FORA do bloco travado recusa a aprovacao")
    void supplierBlockOutsideLockedBlockIsRefused() {
        Set<String> fields = completeFields("ARESC_RES046_2016");
        assertThat(InvoiceTemplateContentRule.contentProblems(fields, Set.of(), "ARESC_RES046_2016", DATE))
                .anyMatch(p -> p.contains(RequiredBillContent.SUPPLIER_IDENTIFICATION_BLOCK)
                        && p.contains("travado"));
    }

    @Test
    @DisplayName("RAS-28: modelo com valor aproximado de tributos e recusado enquanto nao ha parecer")
    void approximateTaxesFieldIsRefusedWithoutLegalOpinion() {
        Set<String> fields = completeFields("ARIS_RN19_2019");
        fields.add(PendingTaxDisclosure.FIELD_KEY);
        assertThatThrownBy(() -> InvoiceTemplateContentRule.assertApprovable(
                fields, lockedFields("ARIS_RN19_2019"), "ARIS_RN19_2019", DATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RAS-28");
        // E o campo pendente NAO e exigido em perfil nenhum.
        for (String profile : RequiredBillContent.profileKeys()) {
            assertThat(completeFields(profile)).doesNotContain(PendingTaxDisclosure.FIELD_KEY);
        }
    }

    @Test
    @DisplayName("faltante por perfil: remover QUALQUER campo de ERI de QUALQUER perfil recusa, nomeando o campo")
    void anyMissingProfileFieldIsRefusedForEveryProfile() {
        // [Revisao cruzada: a versao original so testava o faltante da ARIS;
        // o relatorio afirmava "faltante recusa" por perfil sem o teste.]
        for (String profile : RequiredBillContent.profileKeys()) {
            for (BillFieldRequirement f : RequiredBillContent.profileFields(profile)) {
                Set<String> fields = completeFields(profile);
                fields.remove(f.fieldKey());
                assertThat(InvoiceTemplateContentRule.contentProblems(fields, lockedFields(profile), profile, DATE))
                        .as("perfil " + profile + " sem " + f.fieldKey())
                        .anyMatch(p -> p.contains(f.fieldKey()) && p.contains(f.sourceNorm()));
            }
        }
    }

    @Test
    @DisplayName("faltante federal: sem o bloco de qualidade da agua, qualquer perfil recusa nomeando Decreto 5.440/2005")
    void missingFederalFieldIsRefused() {
        Set<String> fields = completeFields("AGIR_RN001_2013");
        fields.remove(RequiredBillContent.WATER_QUALITY_MONTHLY_BLOCK);
        assertThat(InvoiceTemplateContentRule.contentProblems(fields, lockedFields("AGIR_RN001_2013"),
                "AGIR_RN001_2013", DATE))
                .anyMatch(p -> p.contains(RequiredBillContent.WATER_QUALITY_MONTHLY_BLOCK)
                        && p.contains("Decreto 5.440/2005"));
    }

    @Test
    @DisplayName("perfil desconhecido e erro explicito, nunca aprovacao silenciosa")
    void unknownProfileIsRefused() {
        assertThatThrownBy(() -> InvoiceTemplateContentRule.assertApprovable(
                Set.of(), Set.of(), "ERI_INEXISTENTE", DATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ERI_INEXISTENTE");
    }
}
