package br.com.ziipsan.platform.templates;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BrandProvider da P2.17 sobre o motor da P2.30: as variáveis brand.* vêm
 * do instantâneo da marca; o render do motor as resolve; sem marca, nada
 * é emprestado; valor do chamador prevalece sobre o da marca.
 */
class TenantBrandProviderTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID LOGO = UUID.randomUUID();

    private static TenantPdfBrand fullBrand() {
        return new TenantPdfBrand(TENANT, "SAMAE Exemplo", "SAMAE", "#0369A0",
                "Rodape do tenant", "0800 000 0000", null, "www.exemplo.invalid", null,
                LOGO, null);
    }

    private static TenantBrandProvider provider() {
        return new TenantBrandProvider(tenantId ->
                TENANT.equals(tenantId) ? Optional.of(fullBrand()) : Optional.empty());
    }

    @Test
    @DisplayName("variáveis brand.* do instantâneo: só as que a marca tem (e-mail ausente não entra)")
    void brandVariablesFromSnapshot() {
        Map<String, String> variables = provider().brandVariables(TENANT);
        assertThat(variables)
                .containsEntry("brand.name", "SAMAE Exemplo")
                .containsEntry("brand.short_name", "SAMAE")
                .containsEntry("brand.color", "#0369A0")
                .containsEntry("brand.text_on_color", "#FFFFFF")
                .containsEntry("brand.footer", "Rodape do tenant")
                .containsEntry("brand.contact_phone", "0800 000 0000")
                .containsEntry("brand.contact_site", "www.exemplo.invalid")
                .containsEntry("brand.logo_asset_id", LOGO.toString())
                .doesNotContainKeys("brand.contact_email", "brand.contact_address",
                        "brand.logo_dark_asset_id");
    }

    @Test
    @DisplayName("tenant sem marca e geração sem tenant devolvem mapa vazio (sem marca emprestada)")
    void noBrandNoVariables() {
        assertThat(provider().brandVariables(UUID.randomUUID())).isEmpty();
        assertThat(provider().brandVariables(null)).isEmpty();
    }

    @Test
    @DisplayName("render do motor resolve {{brand.name}} e {{brand.footer}} pela marca do tenant")
    void engineRendersBrandVariables() {
        List<TemplateBlock> blocks = BrandedDocumentComposer.compose(
                List.of(new TemplateBlock("corpo", TemplateBlock.Kind.FREE,
                        TemplateBlock.Origin.TENANT, "Corpo do documento.")),
                fullBrand());
        String rendered = TemplateEngine.render(TemplateDocumentType.CERTIFICATE, blocks,
                Map.of(), provider(), TENANT);
        assertThat(rendered).contains("SAMAE Exemplo")
                .contains("Corpo do documento.")
                .contains("Rodape do tenant");
    }

    @Test
    @DisplayName("valor do chamador prevalece sobre o da marca (putIfAbsent do motor)")
    void callerValueWinsOverBrand() {
        List<TemplateBlock> blocks = List.of(new TemplateBlock("corpo", TemplateBlock.Kind.FREE,
                TemplateBlock.Origin.TENANT, "{{brand.name}}"));
        String rendered = TemplateEngine.render(TemplateDocumentType.CERTIFICATE, blocks,
                Map.of("brand.name", "Nome do chamador"), provider(), TENANT);
        assertThat(rendered).isEqualTo("Nome do chamador");
    }

    @Test
    @DisplayName("sem provedor (prévia da P2.30), referência brand.* segue erro explícito, não texto vazio")
    void previewWithoutProviderStillRefusesBrandReference() {
        List<TemplateBlock> blocks = List.of(new TemplateBlock("corpo", TemplateBlock.Kind.FREE,
                TemplateBlock.Origin.TENANT, "{{brand.name}}"));
        assertThatThrownBy(() -> TemplateEngine.preview(TemplateDocumentType.CERTIFICATE, blocks))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("brand.name");
    }

    @Test
    @DisplayName("metadados do PDF: autor é o tenant da marca; sem marca, autor cai no título")
    void pdfMetadataAuthorIsTenant() {
        PdfMetadata withBrand = PdfMetadata.of("Certidao negativa", TemplateDocumentType.CERTIFICATE,
                fullBrand());
        assertThat(withBrand.author()).isEqualTo("SAMAE Exemplo");
        assertThat(withBrand.subject()).isEqualTo("Certidão");
        assertThat(withBrand.creator()).isEqualTo("ZiipSan");
        PdfMetadata withoutBrand = PdfMetadata.of("Certidao negativa",
                TemplateDocumentType.CERTIFICATE, null);
        assertThat(withoutBrand.author()).isEqualTo("Certidao negativa");
    }
}
