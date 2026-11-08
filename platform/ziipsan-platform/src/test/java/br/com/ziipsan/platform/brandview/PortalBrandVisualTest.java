package br.com.ziipsan.platform.brandview;

import br.com.ziipsan.platform.brand.BrandContrast;
import br.com.ziipsan.platform.templates.TenantPdfBrand;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Teste visual do PORTAL por tema (aceite da P3.21): na forma automatizável
 * nesta camada de backend — a vista que o frontend renderiza (nome, logo do
 * tema, cores e variáveis CSS) conferida campo a campo, com o contraste do
 * §3.4 provado pelos números do {@link BrandContrast}. A conferência em
 * tela (screenshot/E2E sobre o frontend) é pendência de integração
 * declarada no relatório: não há frontend nas entregas locais da P3.3.
 */
class PortalBrandVisualTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID LOGO = UUID.randomUUID();
    private static final UUID LOGO_DARK = UUID.randomUUID();

    private static TenantPdfBrand fullBrand() {
        return new TenantPdfBrand(TENANT, "SAMAE Exemplo", "SAMAE", "#0369A0",
                "Rodape do tenant", "0800 000 0000", null, "www.exemplo.invalid", null,
                LOGO, LOGO_DARK);
    }

    private static PortalBrandService service(boolean attribution) {
        return new PortalBrandService(
                tenantId -> TENANT.equals(tenantId) ? Optional.of(fullBrand()) : Optional.empty(),
                tenantId -> attribution);
    }

    @Test
    @DisplayName("tema claro: portal com nome, logo LOGO e cor do tenant, com variáveis CSS da marca")
    void lightThemeShowsTenantBrand() {
        PortalBrandView view = service(true).viewFor(TENANT, BrandTheme.LIGHT).orElseThrow();
        assertThat(view.displayName()).isEqualTo("SAMAE Exemplo");
        assertThat(view.shortName()).isEqualTo("SAMAE");
        assertThat(view.logoAssetId()).isEqualTo(LOGO);
        assertThat(view.logoKind()).isEqualTo(PortalBrandView.LOGO_KIND);
        assertThat(view.primaryColor()).isEqualTo("#0369A0");
        assertThat(view.cssVariables())
                .containsEntry("--brand-primary", "#0369A0")
                .containsEntry("--brand-on-primary", "#FFFFFF")
                .containsEntry("--brand-text", "#0369A0");
    }

    @Test
    @DisplayName("tema escuro: portal usa a variante LOGO_DARK do tenant")
    void darkThemeUsesDarkLogoVariant() {
        PortalBrandView view = service(true).viewFor(TENANT, BrandTheme.DARK).orElseThrow();
        assertThat(view.logoAssetId()).isEqualTo(LOGO_DARK);
        assertThat(view.logoKind()).isEqualTo(PortalBrandView.LOGO_DARK_KIND);
        assertThat(view.displayName()).isEqualTo("SAMAE Exemplo");
        assertThat(view.primaryColor()).isEqualTo("#0369A0");
    }

    @Test
    @DisplayName("tema escuro sem variante escura recua ao logo único do tenant (kind declarado)")
    void darkThemeFallsBackToOnlyLogo() {
        TenantPdfBrand onlyLogo = new TenantPdfBrand(TENANT, "SAMAE Exemplo", "SAMAE", "#0369A0",
                null, null, null, null, null, LOGO, null);
        PortalBrandService service = new PortalBrandService(
                tenantId -> Optional.of(onlyLogo), tenantId -> false);
        PortalBrandView view = service.viewFor(TENANT, BrandTheme.DARK).orElseThrow();
        assertThat(view.logoAssetId()).isEqualTo(LOGO);
        assertThat(view.logoKind()).isEqualTo(PortalBrandView.LOGO_KIND);
    }

    @Test
    @DisplayName("tema claro sem LOGO não usa a variante de fundo escuro: sem logo, só o nome")
    void lightThemeDoesNotUseDarkVariant() {
        TenantPdfBrand onlyDark = new TenantPdfBrand(TENANT, "SAMAE Exemplo", "SAMAE", "#0369A0",
                null, null, null, null, null, null, LOGO_DARK);
        PortalBrandService service = new PortalBrandService(
                tenantId -> Optional.of(onlyDark), tenantId -> false);
        PortalBrandView view = service.viewFor(TENANT, BrandTheme.LIGHT).orElseThrow();
        assertThat(view.hasLogo()).isFalse();
        assertThat(view.logoAssetId()).isNull();
    }

    @Test
    @DisplayName("contraste por tema: texto sobre a cor e cor como texto sobre o fundo REAL de cada tema atingem 4,5:1 (§3.4)")
    void contrastHoldsPerTheme() {
        // Correção D1: a versão anterior iterava os dois temas mas media a
        // cor de texto sempre contra "#FFFFFF" — "provava" o tema escuro
        // contra um fundo que não é o dele (a cor ajustada para branco,
        // #0369A0, sobre o fundo escuro #0F172A dá só 3,0027:1). Agora o
        // fundo é o de cada tema: branco no claro, #0F172A no escuro (os
        // tokens de fundo que o BrandedEmailComposer usa nas cartas).
        for (BrandTheme theme : BrandTheme.values()) {
            PortalBrandView view = service(true).viewFor(TENANT, theme).orElseThrow();
            String themeBackground = theme == BrandTheme.DARK
                    ? BrandContrast.DARK_TEXT : BrandContrast.LIGHT_TEXT;
            assertThat(BrandContrast.ratio(view.onPrimaryColor(), view.primaryColor()))
                    .isGreaterThanOrEqualTo(BrandContrast.MIN_RATIO);
            assertThat(BrandContrast.ratio(view.brandTextColor(), themeBackground))
                    .isGreaterThanOrEqualTo(BrandContrast.MIN_RATIO);
            assertThat(BrandContrast.ratio(
                    view.cssVariables().get("--brand-text"), themeBackground))
                    .isGreaterThanOrEqualTo(BrandContrast.MIN_RATIO);
        }
    }

    @Test
    @DisplayName("tema escuro: a cor como texto é a variante clareada para #0F172A; no claro, o valor não muda")
    void darkBrandTextIsLightenedForDarkBackground() {
        // Vetor #0369A0 (conferido à mão): sobre branco dá 5,9454:1 (sem
        // ajuste no claro); sobre #0F172A dá só 3,0027:1 (reprova). A
        // variante escura é o clareamento de 21% em direção ao branco:
        // #3889B4, com 4,5953:1 sobre #0F172A.
        PortalBrandView light = service(true).viewFor(TENANT, BrandTheme.LIGHT).orElseThrow();
        PortalBrandView dark = service(true).viewFor(TENANT, BrandTheme.DARK).orElseThrow();
        assertThat(light.brandTextColor()).isEqualTo("#0369A0");
        assertThat(light.cssVariables()).containsEntry("--brand-text", "#0369A0");
        assertThat(dark.brandTextColor()).isEqualTo("#3889B4");
        assertThat(dark.cssVariables()).containsEntry("--brand-text", "#3889B4");
        assertThat(BrandContrast.ratio(dark.brandTextColor(), "#0F172A"))
                .isGreaterThanOrEqualTo(BrandContrast.MIN_RATIO);
    }

    @Test
    @DisplayName("cor clara como texto é a efetiva escurecida do §3.4; a primária gravada não muda")
    void lightColorIsAdjustedForTextUse() {
        // Vetor #FFCC00 (conferido à mão): como texto sobre branco dá 1,512
        // (reprova) e o ajuste do §3.4 o escurece até >= 4,5:1 (#8F7200 =
        // 4,5903); o texto SOBRE a cor é o escuro, com 11,8075:1.
        // O vetor NÃO é #FF0000 de propósito: sobre o vermelho puro, nem o
        // branco (3,9985) nem o #0F172A (4,4648) atingem 4,5:1 no
        // textOnColor da P1.19 — limite pré-existente registrado na revisão
        // desta tarefa, fora do escopo corrigir aqui.
        TenantPdfBrand yellow = new TenantPdfBrand(TENANT, "SAAE Amarelo", "SAAE", "#FFCC00",
                null, null, null, null, null, null, null);
        PortalBrandService service = new PortalBrandService(
                tenantId -> Optional.of(yellow), tenantId -> false);
        PortalBrandView view = service.viewFor(TENANT, BrandTheme.LIGHT).orElseThrow();
        assertThat(view.primaryColor()).isEqualTo("#FFCC00");
        assertThat(view.brandTextColor()).isNotEqualTo("#FFCC00");
        assertThat(BrandContrast.ratio(view.brandTextColor(), "#FFFFFF"))
                .isGreaterThanOrEqualTo(BrandContrast.MIN_RATIO);
        assertThat(BrandContrast.ratio(view.onPrimaryColor(), view.primaryColor()))
                .isGreaterThanOrEqualTo(BrandContrast.MIN_RATIO);
    }

    @Test
    @DisplayName("atribuição 'operado com' aparece só quando o pacote contratado a exibe (os dois temas)")
    void attributionFollowsPackageDecision() {
        for (BrandTheme theme : BrandTheme.values()) {
            assertThat(service(true).viewFor(TENANT, theme).orElseThrow().attribution())
                    .isEqualTo(PortalBrandView.OPERATED_WITH_LINE);
            assertThat(service(false).viewFor(TENANT, theme).orElseThrow().hasAttribution())
                    .isFalse();
        }
    }

    @Test
    @DisplayName("tenant sem marca não tem vista: nenhuma marca emprestada no portal")
    void tenantWithoutBrandHasNoView() {
        assertThat(service(true).viewFor(UUID.randomUUID(), BrandTheme.LIGHT)).isEmpty();
        assertThat(service(true).viewFor(UUID.randomUUID(), BrandTheme.DARK)).isEmpty();
    }

    @Test
    @DisplayName("vista construída direto recusa logo incoerente: kind sem ativo, ativo sem kind, kind desconhecido, LOGO_DARK no claro")
    void viewRejectsIncoherentLogoCombinations() {
        assertThatThrownBy(() -> new PortalBrandView(TENANT, BrandTheme.LIGHT,
                "SAMAE Exemplo", "SAMAE", "#0369A0", "#FFFFFF", "#0369A0",
                null, PortalBrandView.LOGO_KIND, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PortalBrandView(TENANT, BrandTheme.LIGHT,
                "SAMAE Exemplo", "SAMAE", "#0369A0", "#FFFFFF", "#0369A0",
                LOGO, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PortalBrandView(TENANT, BrandTheme.LIGHT,
                "SAMAE Exemplo", "SAMAE", "#0369A0", "#FFFFFF", "#0369A0",
                LOGO, "FAVICON", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PortalBrandView(TENANT, BrandTheme.LIGHT,
                "SAMAE Exemplo", "SAMAE", "#0369A0", "#FFFFFF", "#0369A0",
                LOGO_DARK, PortalBrandView.LOGO_DARK_KIND, null))
                .isInstanceOf(IllegalArgumentException.class);
        // As combinações que o serviço produz são aceitas: LOGO_DARK no
        // escuro, LOGO no escuro (recuo) e nenhum logo nos dois temas.
        assertThat(new PortalBrandView(TENANT, BrandTheme.DARK,
                "SAMAE Exemplo", "SAMAE", "#0369A0", "#FFFFFF", "#3889B4",
                LOGO_DARK, PortalBrandView.LOGO_DARK_KIND, null).hasLogo()).isTrue();
        assertThat(new PortalBrandView(TENANT, BrandTheme.DARK,
                "SAMAE Exemplo", "SAMAE", "#0369A0", "#FFFFFF", "#3889B4",
                LOGO, PortalBrandView.LOGO_KIND, null).hasLogo()).isTrue();
        assertThat(new PortalBrandView(TENANT, BrandTheme.LIGHT,
                "SAMAE Exemplo", "SAMAE", "#0369A0", "#FFFFFF", "#0369A0",
                null, null, null).hasLogo()).isFalse();
    }

    @Test
    @DisplayName("vista construída direto recusa atribuição diferente da linha 'operado com'")
    void viewRejectsForeignAttributionLine() {
        assertThatThrownBy(() -> new PortalBrandView(TENANT, BrandTheme.LIGHT,
                "SAMAE Exemplo", "SAMAE", "#0369A0", "#FFFFFF", "#0369A0",
                LOGO, PortalBrandView.LOGO_KIND, "Operado com OutraMarca"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new PortalBrandView(TENANT, BrandTheme.LIGHT,
                "SAMAE Exemplo", "SAMAE", "#0369A0", "#FFFFFF", "#0369A0",
                LOGO, PortalBrandView.LOGO_KIND, PortalBrandView.OPERATED_WITH_LINE)
                .hasAttribution()).isTrue();
    }

    @Test
    @DisplayName("tenant ou tema nulos são recusados")
    void nullArgumentsAreRefused() {
        assertThatThrownBy(() -> service(true).viewFor(null, BrandTheme.LIGHT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service(true).viewFor(TENANT, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
