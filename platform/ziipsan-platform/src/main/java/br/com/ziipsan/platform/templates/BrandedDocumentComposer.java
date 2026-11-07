package br.com.ziipsan.platform.templates;

import java.util.ArrayList;
import java.util.List;

/**
 * Aplicação da marca sobre o modelo (P2.17): compõe a lista de blocos do
 * documento com marca a partir dos blocos do modelo aprovado, SEM tocar
 * nos blocos existentes.
 *
 * <p>GARANTIA ESTRUTURAL (CAD-96, RAS-04): a composição só ACRESCENTA
 * blocos novos, de origem {@code TENANT} e tipo {@code FREE}, com ids
 * próprios do namespace {@code brand.*}:
 * <ul>
 *   <li>um bloco de cabeçalho no início, quando a marca tem nome —
 *       o texto é a referência {@code {{brand.name}}} (resolvida pelo
 *       {@link TenantBrandProvider} na geração), precedida do marcador de
 *       logo {@code {{brand.logo_asset_id}}} quando há logo efetivo: o
 *       renderizador de PDF posiciona o logo pelo id, ao lado do nome —
 *       a marca entra como camada própria, nunca dentro do texto de
 *       outro bloco;</li>
 *   <li>um bloco de rodapé no fim, quando a marca tem
 *       {@code branding.document_footer} — texto {@code {{brand.footer}}}.</li>
 * </ul>
 * Todo bloco do modelo — em especial os SYSTEM travados do conteúdo legal
 * da P2.32 e os blocos das mensagens legais — atravessa a composição
 * byte a byte, na mesma ordem relativa. {@link BrandedTemplateGuard}
 * confere essa garantia e os testes a provam também contra o catálogo de
 * campos obrigatórios da P2.32.
 *
 * <p>Sem marca (ou marca sem nome e sem rodapé), a composição devolve os
 * blocos como vieram: documento sem marca é válido; marca emprestada, não.
 */
public final class BrandedDocumentComposer {

    /** Id do bloco de cabeçalho de marca acrescentado pela composição. */
    public static final String HEADER_BLOCK_ID = "brand.header";

    /** Id do bloco de rodapé de marca acrescentado pela composição. */
    public static final String FOOTER_BLOCK_ID = "brand.footer";

    private BrandedDocumentComposer() {
    }

    /** Blocos do documento com a marca aplicada sobre os blocos do modelo. */
    public static List<TemplateBlock> compose(List<TemplateBlock> modelBlocks, TenantPdfBrand brand) {
        if (modelBlocks == null) {
            throw new IllegalArgumentException("modelBlocks e obrigatorio");
        }
        if (brand == null || (!brand.hasDisplayName() && !brand.hasFooter())) {
            return List.copyOf(modelBlocks);
        }
        // A cor efetiva do PDF é conferida antes de compor: marca que não
        // pode ser impressa pelo §3.4 não entra no documento em silêncio.
        PdfBrandContrast.assertPrintable(brand);
        List<TemplateBlock> composed = new ArrayList<>();
        if (brand.hasDisplayName()) {
            StringBuilder header = new StringBuilder();
            if (brand.hasLogo()) {
                header.append("{{brand.logo_asset_id}}\n");
            }
            header.append("{{brand.name}}\n");
            composed.add(new TemplateBlock(HEADER_BLOCK_ID, TemplateBlock.Kind.FREE,
                    TemplateBlock.Origin.TENANT, header.toString()));
        }
        composed.addAll(modelBlocks);
        if (brand.hasFooter()) {
            composed.add(new TemplateBlock(FOOTER_BLOCK_ID, TemplateBlock.Kind.FREE,
                    TemplateBlock.Origin.TENANT, "{{brand.footer}}"));
        }
        return List.copyOf(composed);
    }
}
