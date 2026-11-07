package br.com.ziipsan.platform.templates;

import java.util.Map;
import java.util.UUID;

/**
 * O {@code BrandProvider} do aceite (P2.17): implementa o
 * {@link TemplateBrandProvider} que o motor da P2.30 já consome em
 * {@link TemplateEngine#render} — as referências {@code {{brand.*}}} do
 * corpo do modelo são preenchidas com as variáveis de
 * {@link TenantPdfBrand#brandVariables()} do tenant do documento.
 *
 * <p>Semântica herdada do motor (não redefinida aqui): valor de marca só
 * preenche referência SEM valor do chamador ({@code putIfAbsent}) — o
 * chamador sempre pode sobrepor pontualmente; referência {@code brand.*}
 * sem marca e sem valor do chamador recusa a geração com erro explícito.
 *
 * <p>Tenant sem marca (ou geração sem tenant, como a prévia da P2.30):
 * mapa vazio — nenhum documento ganha marca emprestada.
 */
public final class TenantBrandProvider implements TemplateBrandProvider {

    private final BrandSource source;

    public TenantBrandProvider(BrandSource source) {
        if (source == null) {
            throw new IllegalArgumentException("BrandSource e obrigatoria");
        }
        this.source = source;
    }

    @Override
    public Map<String, String> brandVariables(UUID tenantId) {
        if (tenantId == null) {
            return Map.of();
        }
        return source.brandFor(tenantId)
                .map(TenantPdfBrand::brandVariables)
                .orElseGet(Map::of);
    }
}
