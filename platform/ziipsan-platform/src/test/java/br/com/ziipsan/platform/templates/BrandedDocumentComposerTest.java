package br.com.ziipsan.platform.templates;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Marca sem tocar o legal (P2.17; CAD-96, RAS-04): modelo com marca
 * mantém TODOS os campos obrigatórios de TODOS os perfis da P2.32, os
 * blocos SYSTEM/LOCKED atravessam a composição byte a byte, e a guarda
 * nomeia qualquer violação.
 *
 * <p>As chaves por perfil abaixo espelham o catálogo
 * {@code RequiredBillContent} da P2.32 (base federal de 5 campos + os de
 * cada ERI ali declarados); o teste irmão no billing
 * ({@code BrandedBillTemplateContentTest}) confere as MESMAS composições
 * contra o catálogo real e a regra de aprovação da P2.32 — divergência
 * entre os dois lados falha em um deles.
 */
class BrandedDocumentComposerTest {

    private static final List<String> FEDERAL = List.of(
            "bill.regulatory_model_identification",
            "supplier.identification_block",
            "water_quality.monthly_block",
            "annual_discharge.declaration",
            "holder.printed_name");
    private static final String LOCKED_FEDERAL = "supplier.identification_block";

    private static final Map<String, List<String>> PROFILE_EXTRA = new LinkedHashMap<>();

    static {
        List<String> three = List.of("charges.fine_interest_correction_disclosure",
                "reconnection.sla_disclosure", "cut.notice_disclosure");
        PROFILE_EXTRA.put("ARIS_RN19_2019", three);
        PROFILE_EXTRA.put("ARESC_RES046_2016", three);
        PROFILE_EXTRA.put("AGIR_RN001_2013", three);
        PROFILE_EXTRA.put("CISAM_MO_RN01_2017", List.of("cut.notice_disclosure"));
        PROFILE_EXTRA.put("CISAM_MO_RN05_2026", List.of("cut.notice_disclosure"));
        PROFILE_EXTRA.put("AGR_TUBARAO_RES007_2013", List.of());
        PROFILE_EXTRA.put("CISAM_SUL_RN03_2017", List.of());
    }

    private static TenantPdfBrand brand() {
        return new TenantPdfBrand(UUID.randomUUID(), "SAMAE Exemplo", "SAMAE", "#0369A0",
                "Rodape do tenant", null, null, null, null, UUID.randomUUID(), null);
    }

    /** Modelo da conta do perfil: blocos SYSTEM da P2.32 + um bloco livre do tenant. */
    private static List<TemplateBlock> billModel(String profile) {
        List<String> keys = new ArrayList<>(FEDERAL);
        keys.addAll(PROFILE_EXTRA.get(profile));
        List<String> texts = keys.stream().map(k -> "texto de " + k).toList();
        List<TemplateBlock> blocks = new ArrayList<>(
                LegalTemplateBlocks.systemBlocks(keys, texts));
        blocks.add(new TemplateBlock("saudacao", TemplateBlock.Kind.FREE,
                TemplateBlock.Origin.TENANT, "Prezado usuario,"));
        return blocks;
    }

    @Test
    @DisplayName("modelo com marca mantém todos os campos obrigatórios dos 7 perfis da P2.32")
    void brandedModelKeepsEveryRequiredFieldOfEveryProfile() {
        for (String profile : PROFILE_EXTRA.keySet()) {
            List<TemplateBlock> model = billModel(profile);
            List<TemplateBlock> branded = BrandedDocumentComposer.compose(model, brand());
            Set<String> required = Set.copyOf(LegalTemplateBlocks.fieldKeysOf(model));
            assertThat(BrandedTemplateGuard.requiredContentProblems(branded, required,
                    Set.of(LOCKED_FEDERAL)))
                    .as("perfil " + profile)
                    .isEmpty();
            assertThat(BrandedTemplateGuard.preservationProblems(model, branded))
                    .as("perfil " + profile)
                    .isEmpty();
            assertThatCode(() -> BrandedTemplateGuard.assertPreserved(model, branded, required,
                    Set.of(LOCKED_FEDERAL)))
                    .as("perfil " + profile)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("blocos SYSTEM e LOCKED atravessam a composição byte a byte, na mesma ordem relativa")
    void legalBlocksSurviveByteByByte() {
        List<TemplateBlock> model = billModel("ARESC_RES046_2016");
        List<TemplateBlock> branded = BrandedDocumentComposer.compose(model, brand());
        for (TemplateBlock original : model) {
            assertThat(branded).contains(original);
        }
        assertThat(LegalTemplateBlocks.fieldKeysOf(branded))
                .isEqualTo(LegalTemplateBlocks.fieldKeysOf(model));
        assertThat(LegalTemplateBlocks.lockedFieldKeysOf(branded))
                .isEqualTo(LegalTemplateBlocks.lockedFieldKeysOf(model));
        // A marca acrescenta exatamente cabeçalho e rodapé, FREE/TENANT.
        assertThat(branded).hasSize(model.size() + 2);
        assertThat(branded.get(0).id()).isEqualTo(BrandedDocumentComposer.HEADER_BLOCK_ID);
        assertThat(branded.get(branded.size() - 1).id())
                .isEqualTo(BrandedDocumentComposer.FOOTER_BLOCK_ID);
        assertThat(branded.get(0)).matches(b -> b.kind() == TemplateBlock.Kind.FREE
                && b.origin() == TemplateBlock.Origin.TENANT);
    }

    @Test
    @DisplayName("guarda nomeia bloco legal alterado, removido, destravado e reordenado")
    void guardNamesEveryViolation() {
        List<TemplateBlock> model = billModel("ARIS_RN19_2019");
        List<TemplateBlock> branded = BrandedDocumentComposer.compose(model, brand());

        List<TemplateBlock> altered = new ArrayList<>(branded);
        int idx = indexOf(altered, LOCKED_FEDERAL);
        TemplateBlock old = altered.get(idx);
        altered.set(idx, new TemplateBlock(old.id(), old.kind(), old.origin(), "texto adulterado"));
        assertThat(BrandedTemplateGuard.preservationProblems(model, altered))
                .anyMatch(p -> p.contains(LOCKED_FEDERAL));

        List<TemplateBlock> removed = new ArrayList<>(branded);
        removed.removeIf(b -> b.id().equals(LOCKED_FEDERAL));
        assertThat(BrandedTemplateGuard.preservationProblems(model, removed))
                .anyMatch(p -> p.contains(LOCKED_FEDERAL));
        assertThat(BrandedTemplateGuard.requiredContentProblems(removed,
                Set.copyOf(LegalTemplateBlocks.fieldKeysOf(model)), Set.of(LOCKED_FEDERAL)))
                .anyMatch(p -> p.contains(LOCKED_FEDERAL));

        List<TemplateBlock> unlocked = new ArrayList<>(branded);
        int uidx = indexOf(unlocked, LOCKED_FEDERAL);
        TemplateBlock uold = unlocked.get(uidx);
        unlocked.set(uidx, new TemplateBlock(uold.id(), TemplateBlock.Kind.FREE,
                uold.origin(), uold.text()));
        assertThat(BrandedTemplateGuard.preservationProblems(model, unlocked))
                .anyMatch(p -> p.contains(LOCKED_FEDERAL));

        List<TemplateBlock> reordered = new ArrayList<>(branded);
        int a = indexOf(reordered, FEDERAL.get(0));
        int b = indexOf(reordered, "water_quality.monthly_block");
        TemplateBlock tmp = reordered.get(a);
        reordered.set(a, reordered.get(b));
        reordered.set(b, tmp);
        assertThat(BrandedTemplateGuard.preservationProblems(model, reordered))
                .anyMatch(p -> p.contains("reordenados"));
    }

    @Test
    @DisplayName("sem marca (ou marca vazia), a composição devolve o modelo intacto")
    void noBrandLeavesModelUntouched() {
        List<TemplateBlock> model = billModel("AGIR_RN001_2013");
        assertThat(BrandedDocumentComposer.compose(model, null)).isEqualTo(model);
        TenantPdfBrand empty = new TenantPdfBrand(UUID.randomUUID(), null, null, null,
                null, null, null, null, null, null, null);
        assertThat(BrandedDocumentComposer.compose(model, empty)).isEqualTo(model);
    }

    @Test
    @DisplayName("render do modelo com marca: cabeçalho e rodapé da marca, conteúdo legal literal no meio")
    void renderedBrandedDocumentKeepsLegalTextLiteral() {
        List<TemplateBlock> model = billModel("CISAM_SUL_RN03_2017");
        TenantPdfBrand brand = brand();
        List<TemplateBlock> branded = BrandedDocumentComposer.compose(model, brand);
        TenantBrandProvider provider = new TenantBrandProvider(
                tenantId -> java.util.Optional.of(brand));
        String rendered = TemplateEngine.render(TemplateDocumentType.CERTIFICATE, branded,
                Map.of(), provider, brand.tenantId());
        assertThat(rendered).startsWith(brand.logoAssetId() + "\nSAMAE Exemplo\n");
        for (String key : FEDERAL) {
            assertThat(rendered).contains("texto de " + key);
        }
        assertThat(rendered).endsWith("Rodape do tenant");
    }

    private static int indexOf(List<TemplateBlock> blocks, String id) {
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i).id().equals(id)) {
                return i;
            }
        }
        throw new IllegalStateException("bloco nao encontrado: " + id);
    }
}
