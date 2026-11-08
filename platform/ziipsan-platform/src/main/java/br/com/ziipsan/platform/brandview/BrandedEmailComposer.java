package br.com.ziipsan.platform.brandview;

import br.com.ziipsan.platform.brand.BrandContrast;
import br.com.ziipsan.platform.notification.RenderedMessage;
import br.com.ziipsan.platform.templates.TenantPdfBrand;

import java.util.ArrayList;
import java.util.List;

/**
 * Aplica a marca do tenant sobre a mensagem de e-mail (P3.21 sobre a
 * P3.5/P2.30): envolve o corpo montado pelo motor de modelos numa carta
 * com cabeçalho (logo do tema + nome, sobre a cor da marca), corpo intacto
 * e rodapé (contato do tenant e, conforme o pacote, a atribuição
 * "operado com").
 *
 * <p>Garantias:
 * <ul>
 *   <li><b>o corpo não é tocado:</b> no texto puro ele aparece byte a byte;
 *       no HTML, escapado e com {@code white-space:pre-wrap} — a marca é
 *       camada ao redor, como a P2.17 faz nos PDFs (conteúdo legal e
 *       mensagens da P2.32 que estejam no corpo atravessam intactos);</li>
 *   <li><b>o assunto não muda</b> (nem prefixo de marca): assunto é parte
 *       da mensagem registrada na tentativa de entrega da P3.5;</li>
 *   <li><b>escape total:</b> nome, contato, assunto visual e corpo são
 *       escapados no HTML — marca cadastrada com {@code <} não vira
 *       marcação;</li>
 *   <li><b>logo por URL da integração:</b> os bytes do logo são do pipeline
 *       P1.19/P1.24; quem chama resolve a URL pública do ativo do tema
 *       (rota de logo da P1.19) e a passa em {@code logoUrl}. Sem URL, o
 *       cabeçalho sai só com o nome — nunca uma {@code <img>} quebrada
 *       nem URL inventada por esta camada. O {@code alt} é o nome do
 *       tenant (acessibilidade);</li>
 *   <li><b>sem nome, não há cabeçalho (decisão declarada):</b> o cabeçalho
 *       da carta é o nome do tenant — a faixa na cor da marca e o logo são
 *       acompanhamentos dele. Marca com cor e/ou logo mas SEM
 *       {@code displayName} NÃO ganha cabeçalho: nem a faixa colorida nem
 *       a {@code <img>} são geradas no HTML, e o texto puro começa direto
 *       no corpo. É a mesma regra da P2.17 nos documentos
 *       ({@code TenantPdfBrand.hasDisplayName}: "sem nome, não há
 *       cabeçalho de marca a aplicar"; o compositor de documentos só
 *       acrescenta o bloco de cabeçalho quando a marca tem nome, com o
 *       logo dentro dele) — nenhum cabeçalho alternativo só de cor/logo
 *       foi inventado aqui. A omissão é SÓ do cabeçalho: corpo, rodapé de
 *       contato e atribuição do pacote não dependem do nome e saem
 *       normalmente (comportamento coberto por teste);</li>
 *   <li><b>cores só dos tokens conhecidos:</b> fundo claro {@code #FFFFFF}
 *       e texto {@code #0F172A}; fundo escuro {@code #0F172A} e texto
 *       {@code #FFFFFF} (o par de corpo confere 17,8525:1 nos testes);
 *       faixa do cabeçalho na cor da marca com o texto de
 *       {@code BrandContrast.textOnColor} da P1.19 (com a ressalva de
 *       luminância intermediária registrada na revisão). Sem cor
 *       cadastrada, não há faixa colorida — o cabeçalho é o nome sobre o
 *       fundo do tema.</li>
 * </ul>
 *
 * <p>Marca nula é carta válida sem camada de marca (o envio da P3.5 de
 * tenant sem marca segue possível); a atribuição, quando ligada pela
 * porta do pacote, aparece mesmo sem marca — ela é do pacote contratado,
 * não do cadastro de marca.
 */
public final class BrandedEmailComposer {

    /** Fundo/texto dos temas (tokens do MARCA-E-TEMAS já usados no {@code BrandContrast}). */
    public static final String LIGHT_BACKGROUND = BrandContrast.LIGHT_TEXT;
    public static final String LIGHT_TEXT = BrandContrast.DARK_TEXT;
    public static final String DARK_BACKGROUND = BrandContrast.DARK_TEXT;
    public static final String DARK_TEXT = BrandContrast.LIGHT_TEXT;

    private BrandedEmailComposer() {
    }

    /**
     * Compõe o e-mail com marca.
     *
     * @param brand           instantâneo da marca (P2.17), ou null sem marca
     * @param message         a mensagem montada (P3.5/P2.30), obrigatória
     * @param theme           o tema da carta (decide fundo e variante do logo)
     * @param showAttribution decisão da {@link PackageAttributionSource} para o tenant
     * @param logoUrl         URL pública do logo do tema, resolvida pela integração; null = sem imagem
     */
    public static BrandedEmail compose(TenantPdfBrand brand, RenderedMessage message,
                                       BrandTheme theme, boolean showAttribution, String logoUrl) {
        if (message == null || theme == null) {
            throw new IllegalArgumentException("a composicao do e-mail exige a mensagem e o tema");
        }
        String background = theme == BrandTheme.DARK ? DARK_BACKGROUND : LIGHT_BACKGROUND;
        String text = theme == BrandTheme.DARK ? DARK_TEXT : LIGHT_TEXT;
        String html = html(brand, message, background, text, showAttribution, logoUrl);
        String plain = plainText(brand, message, showAttribution);
        return new BrandedEmail(message.subject(), html, plain,
                message.templateCode(), message.templateVersion());
    }

    private static String html(TenantPdfBrand brand, RenderedMessage message, String background,
                               String text, boolean showAttribution, String logoUrl) {
        StringBuilder out = new StringBuilder();
        out.append("<div style=\"margin:0;padding:24px;background:").append(background)
                .append(";color:").append(text)
                .append(";font-family:Arial,Helvetica,sans-serif;\">");
        if (brand != null && brand.displayName() != null) {
            if (brand.color() != null) {
                out.append("<div style=\"padding:16px;background:").append(brand.color())
                        .append(";color:").append(BrandContrast.textOnColor(brand.color())).append(";\">");
            } else {
                out.append("<div style=\"padding:16px;\">");
            }
            if (logoUrl != null && !logoUrl.isBlank()) {
                out.append("<img src=\"").append(escape(logoUrl.strip()))
                        .append("\" alt=\"").append(escape(brand.displayName()))
                        .append("\" style=\"max-height:48px;display:block;margin-bottom:8px;\">");
            }
            out.append("<strong style=\"font-size:20px;\">")
                    .append(escape(brand.displayName())).append("</strong></div>");
        }
        out.append("<div style=\"padding:16px;white-space:pre-wrap;\">")
                .append(escape(message.body())).append("</div>");
        List<String> footer = footerLines(brand, showAttribution);
        if (!footer.isEmpty()) {
            out.append("<div style=\"padding:16px;font-size:12px;\">");
            for (String line : footer) {
                out.append("<div>").append(escape(line)).append("</div>");
            }
            out.append("</div>");
        }
        out.append("</div>");
        return out.toString();
    }

    private static String plainText(TenantPdfBrand brand, RenderedMessage message,
                                    boolean showAttribution) {
        StringBuilder out = new StringBuilder();
        if (brand != null && brand.displayName() != null) {
            out.append(brand.displayName()).append("\n\n");
        }
        out.append(message.body());
        List<String> footer = footerLines(brand, showAttribution);
        if (!footer.isEmpty()) {
            out.append("\n\n--\n");
            out.append(String.join("\n", footer));
        }
        return out.toString();
    }

    /**
     * Linhas do rodapé, na ordem: contato cadastrado (só os campos que a
     * marca tem) e a atribuição do pacote. O rodapé de documento
     * ({@code branding.document_footer}) NÃO entra aqui: ele é conteúdo de
     * documento (P2.17), não de e-mail.
     */
    private static List<String> footerLines(TenantPdfBrand brand, boolean showAttribution) {
        List<String> lines = new ArrayList<>();
        if (brand != null) {
            if (brand.contactPhone() != null) {
                lines.add(brand.contactPhone());
            }
            if (brand.contactEmail() != null) {
                lines.add(brand.contactEmail());
            }
            if (brand.contactSite() != null) {
                lines.add(brand.contactSite());
            }
            if (brand.contactAddress() != null) {
                lines.add(brand.contactAddress());
            }
        }
        if (showAttribution) {
            lines.add(PortalBrandView.OPERATED_WITH_LINE);
        }
        return List.copyOf(lines);
    }

    /** Escape HTML mínimo e total para valores interpolados na carta. */
    static String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
