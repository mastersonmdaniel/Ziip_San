package br.com.ziipsan.platform.brandview;

import br.com.ziipsan.platform.brand.BrandContrast;
import br.com.ziipsan.platform.templates.BrandSource;
import br.com.ziipsan.platform.templates.TenantPdfBrand;

import java.util.Optional;
import java.util.UUID;

/**
 * Monta a {@link PortalBrandView} do tenant para um tema (P3.21): lê o
 * instantâneo da marca pela MESMA porta da P2.17 ({@link BrandSource},
 * que a integração liga à P1.19) e a decisão de atribuição pela
 * {@link PackageAttributionSource} — nenhuma das duas é duplicada aqui.
 *
 * <p>Regras:
 * <ul>
 *   <li>tenant sem marca cadastrada → {@link Optional#empty()}: o portal
 *       NÃO ganha marca emprestada nesta camada (a marca do produto para
 *       slug desconhecido é comportamento pré-login da P1.19, resolvido
 *       antes desta vista);</li>
 *   <li>logo do tema: claro usa {@code branding.logo}; escuro usa
 *       {@code branding.logo_dark} quando existe e recua ao
 *       {@code branding.logo} quando o tenant só cadastrou uma variante
 *       (decisão desta tarefa, testada: sem variante escura, o logo único
 *       do tenant é melhor que nenhum logo — e o {@code logoKind} devolvido
 *       diz qual ativo foi usado, sem disfarce);</li>
 *   <li>cores: a primária é a cor gravada; o texto sobre ela vem de
 *       {@link BrandContrast} (§3.4). A cor efetiva como texto depende do
 *       fundo do tema: no claro, sobre branco, é o
 *       {@code adjustForWhiteBackground} da P1.19; no escuro, sobre
 *       {@code #0F172A}, é a variante clareada de
 *       {@link #adjustForDarkBackground(String)} — devolver no tema escuro
 *       a cor ajustada para branco reprovaria no contraste (ex.: a
 *       primária {@code #0369A0} sobre {@code #0F172A} dá só 3,0027:1);</li>
 *   <li>atribuição: presente só quando a porta do pacote contratado diz
 *       que exibe — a regra de quais pacotes exibem NÃO está no plano
 *       (ver a porta); aqui só o mecanismo, com os dois ramos testados.</li>
 * </ul>
 */
public final class PortalBrandService {

    private final BrandSource brandSource;
    private final PackageAttributionSource attributionSource;

    public PortalBrandService(BrandSource brandSource, PackageAttributionSource attributionSource) {
        if (brandSource == null || attributionSource == null) {
            throw new IllegalArgumentException("BrandSource e PackageAttributionSource sao obrigatorias");
        }
        this.brandSource = brandSource;
        this.attributionSource = attributionSource;
    }

    /** A vista de marca do tenant no tema; vazia quando o tenant não tem marca. */
    public Optional<PortalBrandView> viewFor(UUID tenantId, BrandTheme theme) {
        if (tenantId == null || theme == null) {
            throw new IllegalArgumentException("a vista de marca exige tenant e tema");
        }
        Optional<TenantPdfBrand> brand = brandSource.brandFor(tenantId);
        if (brand.isEmpty()) {
            return Optional.empty();
        }
        TenantPdfBrand snapshot = brand.get();
        String primary = snapshot.color();
        String onPrimary = primary == null ? null : BrandContrast.textOnColor(primary);
        String brandText = primary == null ? null : brandTextColor(primary, theme);
        UUID logoId;
        String logoKind;
        if (theme == BrandTheme.DARK && snapshot.logoDarkAssetId() != null) {
            logoId = snapshot.logoDarkAssetId();
            logoKind = PortalBrandView.LOGO_DARK_KIND;
        } else if (snapshot.logoAssetId() != null) {
            logoId = snapshot.logoAssetId();
            logoKind = PortalBrandView.LOGO_KIND;
        } else {
            // Sem recuo inverso: no tema claro, uma variante feita para
            // fundo escuro (arte clara) ficaria invisível sobre branco —
            // sem LOGO, o portal mostra o nome, não um logo ilegível.
            logoId = null;
            logoKind = null;
        }
        String attribution = attributionSource.showOperatedWithAttribution(tenantId)
                ? PortalBrandView.OPERATED_WITH_LINE : null;
        return Optional.of(new PortalBrandView(tenantId, theme, snapshot.displayName(),
                snapshot.shortName(), primary, onPrimary, brandText, logoId, logoKind, attribution));
    }

    /**
     * A cor efetiva da marca como texto sobre o fundo do tema da vista:
     * no tema claro (fundo branco), o ajuste da P1.19; no tema escuro
     * (fundo {@code #0F172A}), a variante de {@link #adjustForDarkBackground}.
     */
    private static String brandTextColor(String primary, BrandTheme theme) {
        return theme == BrandTheme.DARK
                ? adjustForDarkBackground(primary)
                : BrandContrast.adjustForWhiteBackground(primary).saved();
    }

    /**
     * Espelho, para o fundo escuro, do {@code adjustForWhiteBackground} da
     * P1.19 (que só ajusta para branco): se a cor como texto sobre
     * {@code #0F172A} já dá 4,5:1, ela volta intacta; senão, é clareada em
     * direção ao branco (cada canal aproximado do 255, preservando o matiz)
     * em passos de 1% até dar 4,5:1 — no limite, o próprio branco, que dá
     * 17,8525:1 sobre {@code #0F172A}, então o laço sempre termina. A razão
     * e o limiar são os do {@link BrandContrast} (§3.4); só a mistura em
     * direção ao branco é desta camada, porque a P1.19 não oferece ajuste
     * para fundo escuro.
     */
    private static String adjustForDarkBackground(String color) {
        String normalized = color.toUpperCase();
        if (BrandContrast.ratio(normalized, BrandContrast.DARK_TEXT) >= BrandContrast.MIN_RATIO) {
            return normalized;
        }
        int r = Integer.parseInt(normalized.substring(1, 3), 16);
        int g = Integer.parseInt(normalized.substring(3, 5), 16);
        int b = Integer.parseInt(normalized.substring(5, 7), 16);
        for (int pct = 1; pct <= 100; pct++) {
            double tint = pct / 100.0;
            String lightened = String.format("#%02X%02X%02X",
                    (int) Math.round(r + (255 - r) * tint),
                    (int) Math.round(g + (255 - g) * tint),
                    (int) Math.round(b + (255 - b) * tint));
            if (BrandContrast.ratio(lightened, BrandContrast.DARK_TEXT) >= BrandContrast.MIN_RATIO) {
                return lightened;
            }
        }
        return BrandContrast.LIGHT_TEXT;
    }
}
