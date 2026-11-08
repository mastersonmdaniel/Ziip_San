package br.com.ziipsan.platform.brandview;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A marca do tenant pronta para o portal (P3.21, sobre a marca da P1.19):
 * nome, logo (a variante do tema) e cor, com as cores de texto que o
 * MARCA-E-TEMAS §3.4 garante — tudo calculado a partir do instantâneo da
 * P2.17 ({@code TenantPdfBrand}) e da matemática única de
 * {@code BrandContrast} (nenhuma fórmula duplicada aqui).
 *
 * <ul>
 *   <li>{@code primaryColor}: a cor gravada da marca ({@code branding.color},
 *       já ajustada ao salvar pela P1.19) — a cor das faixas, botões e
 *       destaques do portal;</li>
 *   <li>{@code onPrimaryColor}: o texto sobre a cor da marca
 *       ({@code BrandContrast.textOnColor}: branco ou {@code #0F172A}, o que
 *       der maior razão, pela regra do §3.4 — com a ressalva registrada na
 *       revisão desta tarefa: em luminâncias intermediárias (ex.:
 *       {@code #FF0000}: branco 3,9985, escuro 4,4648) nenhum dos dois
 *       atinge 4,5:1; é propriedade pré-existente da P1.19, não corrigida
 *       aqui);</li>
 *   <li>{@code brandTextColor}: a cor EFETIVA da marca como texto sobre o
 *       fundo DO TEMA desta vista — no claro, sobre branco
 *       ({@code BrandContrast.adjustForWhiteBackground}); no escuro, sobre
 *       {@code #0F172A}, a variante clareada em direção ao branco até
 *       4,5:1 (ver {@link PortalBrandService}). Para o nome do tenant e
 *       links no fundo da página do tema; nunca é a cor ajustada para o
 *       fundo do outro tema (a cor ajustada para branco sobre
 *       {@code #0F172A} pode reprovar: {@code #0369A0} dá só 3,0027:1);</li>
 *   <li>{@code logoAssetId}/{@code logoKind}: o ativo CLEAN efetivo do tema
 *       ({@code LOGO} no claro; {@code LOGO_DARK} no escuro, com recuo ao
 *       {@code LOGO} quando o tenant só tem uma variante — decisão
 *       documentada no {@link PortalBrandService}); os bytes são os do
 *       pipeline P1.19/P1.24, resolvidos pelo id — esta camada não copia
 *       binário;</li>
 *   <li>{@code attribution}: a linha "operado com" quando o pacote
 *       contratado a exibe (ver {@link PackageAttributionSource}); nula
 *       quando não exibe — ausência é o próprio dado, sem texto escondido.</li>
 * </ul>
 *
 * <p>Campo de marca ausente no instantâneo permanece nulo aqui: o portal
 * mostra o que o tenant cadastrou, nunca marca de outro tenant nem a marca
 * do produto fingindo ser a dele (a regra do slug desconhecido → marca do
 * produto é da camada pré-login da P1.19, não desta vista autenticada).
 *
 * <p>Coerência interna (recusada no construtor, no mesmo espírito das
 * demais validações do pacote): {@code logoAssetId} e {@code logoKind}
 * andam juntos — os dois nulos (sem logo no tema) ou os dois presentes;
 * {@code logoKind} só pode ser {@link #LOGO_KIND} ou
 * {@link #LOGO_DARK_KIND}, e a variante {@code LOGO_DARK} só existe no
 * tema {@link BrandTheme#DARK} (no claro não há recuo inverso — ver
 * {@link PortalBrandService}); {@code attribution} só pode ser nula ou a
 * linha {@link #OPERATED_WITH_LINE} — nenhuma outra grafia entra na vista.
 */
public record PortalBrandView(
        UUID tenantId,
        BrandTheme theme,
        String displayName,
        String shortName,
        String primaryColor,
        String onPrimaryColor,
        String brandTextColor,
        UUID logoAssetId,
        String logoKind,
        String attribution) {

    /** A linha de atribuição do aceite ("operado com"), com o nome do produto como no {@code PdfMetadata} da P2.17. */
    public static final String OPERATED_WITH_LINE = "Operado com ZiipSan";

    /** Tipos de logo da P1.19 ({@code TenantBrandService.validateKind}). */
    public static final String LOGO_KIND = "LOGO";
    public static final String LOGO_DARK_KIND = "LOGO_DARK";

    public PortalBrandView {
        if (tenantId == null || theme == null) {
            throw new IllegalArgumentException("a vista de marca do portal exige tenant e tema");
        }
        if ((logoAssetId == null) != (logoKind == null)) {
            throw new IllegalArgumentException(
                    "logoAssetId e logoKind andam juntos na vista de marca do portal");
        }
        if (logoKind != null && !LOGO_KIND.equals(logoKind) && !LOGO_DARK_KIND.equals(logoKind)) {
            throw new IllegalArgumentException(
                    "logoKind desconhecido na vista de marca do portal: " + logoKind);
        }
        if (LOGO_DARK_KIND.equals(logoKind) && theme != BrandTheme.DARK) {
            throw new IllegalArgumentException(
                    "a variante LOGO_DARK so existe no tema escuro da vista de marca");
        }
        if (attribution != null && !OPERATED_WITH_LINE.equals(attribution)) {
            throw new IllegalArgumentException(
                    "a atribuicao da vista de marca so pode ser a linha 'operado com'");
        }
    }

    /** Tem logo efetivo para o tema? */
    public boolean hasLogo() {
        return logoAssetId != null;
    }

    /** Tem atribuição "operado com" a exibir? */
    public boolean hasAttribution() {
        return attribution != null;
    }

    /**
     * Variáveis CSS que o frontend do portal aplica (nomes desta camada):
     * {@code --brand-primary}, {@code --brand-on-primary} e
     * {@code --brand-text}. Só entram as cores que a marca tem — variável
     * ausente é ausência de marca naquele papel, nunca cor presumida.
     * {@code --brand-text} é a cor efetiva sobre o fundo do tema DESTA
     * vista (branco no claro, {@code #0F172A} no escuro): aplicá-la sobre
     * o fundo da página do mesmo tema é o uso coberto pelos testes de
     * contraste.
     */
    public Map<String, String> cssVariables() {
        Map<String, String> variables = new LinkedHashMap<>();
        if (primaryColor != null) {
            variables.put("--brand-primary", primaryColor);
        }
        if (onPrimaryColor != null) {
            variables.put("--brand-on-primary", onPrimaryColor);
        }
        if (brandTextColor != null) {
            variables.put("--brand-text", brandTextColor);
        }
        return Map.copyOf(variables);
    }
}
