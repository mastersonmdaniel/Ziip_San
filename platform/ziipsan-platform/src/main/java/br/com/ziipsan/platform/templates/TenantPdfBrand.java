package br.com.ziipsan.platform.templates;

import br.com.ziipsan.platform.brand.BrandContrast;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Instantâneo da marca do tenant para documentos (P2.17, ADR-0016): o que a
 * P1.19 cadastrou em {@code tenant_settings} (chaves {@code branding.*}) e em
 * {@code tenant_assets} (logos), projetado para a camada de documento.
 *
 * <p>Fontes (P1.19, {@code TenantBrandService} / V9_2):
 * <ul>
 *   <li>{@code displayName} = {@code branding.display_name} (até 60);</li>
 *   <li>{@code shortName} = {@code branding.short_name} (até 20);</li>
 *   <li>{@code color} = {@code branding.color} ({@code #RRGGBB}, já ajustada
 *       ao contraste na gravação pela P1.19);</li>
 *   <li>{@code documentFooter} = {@code branding.document_footer} (até 300) —
 *       a V9_2 o exclui da projeção pré-login justamente por ser conteúdo de
 *       documento: é ESTA camada que o consome;</li>
 *   <li>{@code contact} = {@code branding.contact} (só {@code phone},
 *       {@code email}, {@code site}, {@code address});</li>
 *   <li>{@code logoAssetId} / {@code logoDarkAssetId} = ids dos ativos
 *       EFETIVOS ({@code branding.logo} / {@code branding.logo_dark}), que a
 *       P1.19 só aponta para ativo {@code CLEAN}. Aqui circula só o id
 *       (metadado): os bytes são do pipeline da P1.19/P1.24 e o renderizador
 *       de PDF os resolve pelo id — esta tarefa não cria nem copia binário.</li>
 * </ul>
 *
 * <p>Campo ausente (null/em branco) = a marca não o tem: a variável
 * correspondente NÃO entra no mapa de {@link #brandVariables()} (o motor
 * recusa referência sem valor na geração — erro explícito, nunca texto
 * vazio fingindo marca).
 */
public record TenantPdfBrand(
        UUID tenantId,
        String displayName,
        String shortName,
        String color,
        String documentFooter,
        String contactPhone,
        String contactEmail,
        String contactSite,
        String contactAddress,
        UUID logoAssetId,
        UUID logoDarkAssetId) {

    public TenantPdfBrand {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId e obrigatorio no instantaneo de marca");
        }
        if (color != null && !color.isBlank() && !BrandContrast.isHexColor(color.strip())) {
            throw new IllegalArgumentException("cor da marca fora do formato #RRGGBB");
        }
        color = color == null || color.isBlank() ? null : color.strip().toUpperCase();
        displayName = blankToNull(displayName);
        shortName = blankToNull(shortName);
        documentFooter = blankToNull(documentFooter);
        contactPhone = blankToNull(contactPhone);
        contactEmail = blankToNull(contactEmail);
        contactSite = blankToNull(contactSite);
        contactAddress = blankToNull(contactAddress);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** Tem nome de exibição? (sem nome, não há cabeçalho de marca a aplicar) */
    public boolean hasDisplayName() {
        return displayName != null;
    }

    /** Tem rodapé de documento cadastrado? */
    public boolean hasFooter() {
        return documentFooter != null;
    }

    /** Tem logo efetivo (CLEAN, apontado pela P1.19)? */
    public boolean hasLogo() {
        return logoAssetId != null;
    }

    /**
     * Cor do texto sobre a cor da marca (MARCA-E-TEMAS §3.4, via
     * {@link BrandContrast#textOnColor}): branco ou o texto escuro padrão,
     * o que der maior razão. Null quando a marca não tem cor.
     */
    public String textOnColor() {
        return color == null ? null : BrandContrast.textOnColor(color);
    }

    /**
     * As variáveis {@code brand.*} que o {@link TemplateEngine} resolve na
     * geração (o conjunto canônico desta tarefa — nenhum modelo de fábrica
     * da P2.30 referencia {@code brand.*} ainda; quem referencia passa a ter
     * exatamente estas chaves disponíveis):
     * {@code brand.name}, {@code brand.short_name}, {@code brand.color},
     * {@code brand.text_on_color}, {@code brand.footer},
     * {@code brand.contact_phone}, {@code brand.contact_email},
     * {@code brand.contact_site}, {@code brand.contact_address},
     * {@code brand.logo_asset_id}, {@code brand.logo_dark_asset_id}.
     * Só entram as que a marca tem (ver o javadoc da classe).
     */
    public Map<String, String> brandVariables() {
        Map<String, String> variables = new LinkedHashMap<>();
        if (displayName != null) {
            variables.put("brand.name", displayName);
        }
        if (shortName != null) {
            variables.put("brand.short_name", shortName);
        }
        if (color != null) {
            variables.put("brand.color", color);
            variables.put("brand.text_on_color", textOnColor());
        }
        if (documentFooter != null) {
            variables.put("brand.footer", documentFooter);
        }
        if (contactPhone != null) {
            variables.put("brand.contact_phone", contactPhone);
        }
        if (contactEmail != null) {
            variables.put("brand.contact_email", contactEmail);
        }
        if (contactSite != null) {
            variables.put("brand.contact_site", contactSite);
        }
        if (contactAddress != null) {
            variables.put("brand.contact_address", contactAddress);
        }
        if (logoAssetId != null) {
            variables.put("brand.logo_asset_id", logoAssetId.toString());
        }
        if (logoDarkAssetId != null) {
            variables.put("brand.logo_dark_asset_id", logoDarkAssetId.toString());
        }
        return Map.copyOf(variables);
    }
}
