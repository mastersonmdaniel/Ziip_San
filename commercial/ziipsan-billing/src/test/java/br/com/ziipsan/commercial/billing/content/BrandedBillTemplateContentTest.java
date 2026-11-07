package br.com.ziipsan.commercial.billing.content;

import br.com.ziipsan.platform.templates.BrandedDocumentComposer;
import br.com.ziipsan.platform.templates.BrandedTemplateGuard;
import br.com.ziipsan.platform.templates.LegalTemplateBlocks;
import br.com.ziipsan.platform.templates.TemplateBlock;
import br.com.ziipsan.platform.templates.TenantPdfBrand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Aceite da P2.17 contra o catálogo REAL da P2.32: o modelo da conta COM
 * MARCA continua aprovável pela regra de conteúdo
 * ({@link InvoiceTemplateContentRule}) em todos os perfis — a marca não
 * cobre campo faltante, não destrava o bloco do CDC e não altera nenhum
 * bloco SYSTEM.
 */
class BrandedBillTemplateContentTest {

    private static final LocalDate DATE = LocalDate.of(2026, 6, 1);

    private static TenantPdfBrand brand() {
        return new TenantPdfBrand(UUID.randomUUID(), "SAMAE Exemplo", "SAMAE", "#0369A0",
                "Rodape do tenant", null, null, null, null, UUID.randomUUID(), null);
    }

    /** Modelo da conta do perfil, montado como a P2.32 manda (blocos SYSTEM). */
    private static List<TemplateBlock> billModel(String profileKey) {
        List<BillFieldRequirement> required = RequiredBillContent.requiredFor(profileKey, DATE);
        List<String> keys = required.stream().map(BillFieldRequirement::fieldKey).toList();
        List<String> texts = keys.stream().map(k -> "texto de " + k).toList();
        List<TemplateBlock> blocks = new ArrayList<>(LegalTemplateBlocks.systemBlocks(keys, texts));
        blocks.add(new TemplateBlock("saudacao", TemplateBlock.Kind.FREE,
                TemplateBlock.Origin.TENANT, "Prezado usuario,"));
        return blocks;
    }

    @Test
    @DisplayName("modelo com marca aprova pelo conteúdo em todos os 7 perfis da P2.32")
    void brandedModelApprovesForEveryProfile() {
        for (String profile : RequiredBillContent.profileKeys()) {
            List<TemplateBlock> branded = BrandedDocumentComposer.compose(billModel(profile), brand());
            Set<String> fields = LegalTemplateBlocks.fieldKeysOf(branded);
            Set<String> locked = LegalTemplateBlocks.lockedFieldKeysOf(branded);
            assertThatCode(() -> InvoiceTemplateContentRule.assertApprovable(
                    fields, locked, profile, DATE))
                    .as("perfil " + profile)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("a marca não cobre campo faltante: sem a qualidade da água, o modelo com marca continua recusado")
    void brandDoesNotMaskMissingField() {
        List<TemplateBlock> model = billModel("ARIS_RN19_2019");
        model.removeIf(b -> b.id().equals(RequiredBillContent.WATER_QUALITY_MONTHLY_BLOCK));
        List<TemplateBlock> branded = BrandedDocumentComposer.compose(model, brand());
        assertThatThrownBy(() -> InvoiceTemplateContentRule.assertApprovable(
                LegalTemplateBlocks.fieldKeysOf(branded),
                LegalTemplateBlocks.lockedFieldKeysOf(branded), "ARIS_RN19_2019", DATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(RequiredBillContent.WATER_QUALITY_MONTHLY_BLOCK);
    }

    @Test
    @DisplayName("guarda da P2.17 + regra da P2.32 concordam: composição preserva chaves e travas do catálogo real")
    void guardAgreesWithContentRule() {
        for (String profile : RequiredBillContent.profileKeys()) {
            List<TemplateBlock> model = billModel(profile);
            List<TemplateBlock> branded = BrandedDocumentComposer.compose(model, brand());
            Set<String> required = new HashSet<>();
            Set<String> lockedRequired = new HashSet<>();
            for (BillFieldRequirement f : RequiredBillContent.requiredFor(profile, DATE)) {
                required.add(f.fieldKey());
                if (f.lockedBlock()) {
                    lockedRequired.add(f.fieldKey());
                }
            }
            assertThat(BrandedTemplateGuard.preservationProblems(model, branded))
                    .as("perfil " + profile).isEmpty();
            assertThat(BrandedTemplateGuard.requiredContentProblems(branded, required, lockedRequired))
                    .as("perfil " + profile).isEmpty();
        }
    }
}
