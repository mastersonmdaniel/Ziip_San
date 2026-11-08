package br.com.ziipsan.platform.brandview;

import br.com.ziipsan.platform.brand.BrandContrast;
import br.com.ziipsan.platform.notification.RenderedMessage;
import br.com.ziipsan.platform.templates.TenantPdfBrand;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Teste visual de UM E-MAIL por tema (aceite da P3.21): a carta gerada é
 * conferida nas duas formas (HTML e texto puro) — nome, cor e logo do
 * tenant no cabeçalho, corpo intacto, rodapé de contato e atribuição
 * conforme o pacote. A renderização em cliente de e-mail real é pendência
 * de integração declarada no relatório.
 */
class BrandedEmailVisualTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID LOGO = UUID.randomUUID();
    private static final UUID LOGO_DARK = UUID.randomUUID();
    private static final String LOGO_URL = "https://exemplo.invalid/logo.png";

    private static TenantPdfBrand fullBrand() {
        return new TenantPdfBrand(TENANT, "SAMAE Exemplo", "SAMAE", "#0369A0",
                "Rodape de DOCUMENTO do tenant", "0800 000 0000", "contato@exemplo.invalid",
                "www.exemplo.invalid", null, LOGO, LOGO_DARK);
    }

    private static RenderedMessage message() {
        return new RenderedMessage("Sua fatura chegou",
                "Corpo da mensagem.\nSegunda linha.", "bill-issued", 3);
    }

    @Test
    @DisplayName("tema claro: e-mail com nome, cor e logo do tenant; corpo e proveniência intactos")
    void lightEmailCarriesTenantBrand() {
        BrandedEmail email = BrandedEmailComposer.compose(
                fullBrand(), message(), BrandTheme.LIGHT, true, LOGO_URL);
        assertThat(email.html())
                .contains("background:#FFFFFF")
                .contains("background:#0369A0")
                .contains("<img src=\"" + LOGO_URL + "\" alt=\"SAMAE Exemplo\"")
                .contains("<strong style=\"font-size:20px;\">SAMAE Exemplo</strong>")
                .contains("Corpo da mensagem.\nSegunda linha.")
                .contains("0800 000 0000")
                .contains("contato@exemplo.invalid")
                .contains(PortalBrandView.OPERATED_WITH_LINE);
        assertThat(email.plainText())
                .startsWith("SAMAE Exemplo\n\n")
                .contains("Corpo da mensagem.\nSegunda linha.")
                .contains(PortalBrandView.OPERATED_WITH_LINE);
        assertThat(email.subject()).isEqualTo("Sua fatura chegou");
        assertThat(email.templateCode()).isEqualTo("bill-issued");
        assertThat(email.templateVersion()).isEqualTo(3);
    }

    @Test
    @DisplayName("tema escuro: mesmo e-mail sobre o fundo escuro, com o mesmo corpo e rodapé")
    void darkEmailUsesDarkBackground() {
        BrandedEmail email = BrandedEmailComposer.compose(
                fullBrand(), message(), BrandTheme.DARK, true, LOGO_URL);
        assertThat(email.html())
                .contains("background:#0F172A")
                .contains("background:#0369A0")
                .contains("SAMAE Exemplo")
                .contains("Corpo da mensagem.\nSegunda linha.")
                .contains(PortalBrandView.OPERATED_WITH_LINE);
        assertThat(email.plainText()).contains("Corpo da mensagem.\nSegunda linha.");
    }

    @Test
    @DisplayName("contraste da carta por tema: faixa da marca e texto do corpo atingem 4,5:1")
    void emailContrastHoldsPerTheme() {
        assertThat(BrandContrast.ratio(
                BrandContrast.textOnColor("#0369A0"), "#0369A0"))
                .isGreaterThanOrEqualTo(BrandContrast.MIN_RATIO);
        assertThat(BrandContrast.ratio(BrandedEmailComposer.LIGHT_TEXT,
                BrandedEmailComposer.LIGHT_BACKGROUND))
                .isGreaterThanOrEqualTo(BrandContrast.MIN_RATIO);
        assertThat(BrandContrast.ratio(BrandedEmailComposer.DARK_TEXT,
                BrandedEmailComposer.DARK_BACKGROUND))
                .isGreaterThanOrEqualTo(BrandContrast.MIN_RATIO);
    }

    @Test
    @DisplayName("sem decisão de atribuição do pacote, a linha 'operado com' não é gerada (HTML nem texto)")
    void attributionAbsentWhenPackageDoesNotShowIt() {
        for (BrandTheme theme : BrandTheme.values()) {
            BrandedEmail email = BrandedEmailComposer.compose(
                    fullBrand(), message(), theme, false, LOGO_URL);
            assertThat(email.html()).doesNotContain(PortalBrandView.OPERATED_WITH_LINE);
            assertThat(email.plainText()).doesNotContain(PortalBrandView.OPERATED_WITH_LINE);
        }
    }

    @Test
    @DisplayName("sem URL de logo resolvida, não sai <img> quebrada: o cabeçalho é só o nome")
    void noLogoUrlNoImageTag() {
        BrandedEmail email = BrandedEmailComposer.compose(
                fullBrand(), message(), BrandTheme.LIGHT, false, null);
        assertThat(email.html()).doesNotContain("<img").contains("SAMAE Exemplo");
    }

    @Test
    @DisplayName("corpo com HTML é escapado na carta e byte a byte no texto puro (conteúdo nunca vira marcação)")
    void bodyIsEscapedInHtmlAndVerbatimInPlainText() {
        RenderedMessage hostile = new RenderedMessage("Aviso",
                "Valor <b>10</b> & taxas", "notice", 1);
        BrandedEmail email = BrandedEmailComposer.compose(
                fullBrand(), hostile, BrandTheme.LIGHT, false, null);
        assertThat(email.html()).contains("Valor &lt;b&gt;10&lt;/b&gt; &amp; taxas")
                .doesNotContain("<b>10</b>");
        assertThat(email.plainText()).contains("Valor <b>10</b> & taxas");
    }

    @Test
    @DisplayName("nome do tenant com caracteres de marcação é escapado no cabeçalho e no alt")
    void brandNameIsEscaped() {
        TenantPdfBrand hostile = new TenantPdfBrand(TENANT, "SAAE <Teste>", "SAAE", "#0369A0",
                null, null, null, null, null, LOGO, null);
        BrandedEmail email = BrandedEmailComposer.compose(
                hostile, message(), BrandTheme.LIGHT, false, LOGO_URL);
        assertThat(email.html()).contains("SAAE &lt;Teste&gt;")
                .doesNotContain("<strong style=\"font-size:20px;\">SAAE <Teste></strong>");
    }

    @Test
    @DisplayName("rodapé só com o contato que a marca tem; o rodapé de documento (P2.17) NÃO entra no e-mail")
    void footerHasOnlyPresentContactAndNoDocumentFooter() {
        BrandedEmail email = BrandedEmailComposer.compose(
                fullBrand(), message(), BrandTheme.LIGHT, false, null);
        assertThat(email.html()).contains("0800 000 0000")
                .doesNotContain("Rodape de DOCUMENTO do tenant");
        assertThat(email.plainText()).doesNotContain("Rodape de DOCUMENTO do tenant");
        TenantPdfBrand phoneOnly = new TenantPdfBrand(TENANT, "SAMAE Exemplo", "SAMAE", null,
                null, "0800 000 0000", null, null, null, null, null);
        BrandedEmail sparse = BrandedEmailComposer.compose(
                phoneOnly, message(), BrandTheme.LIGHT, false, null);
        assertThat(sparse.plainText()).contains("0800 000 0000")
                .doesNotContain("www.exemplo.invalid");
        assertThat(sparse.html()).doesNotContain("background:#0369A0");
    }

    @Test
    @DisplayName("sem marca, a carta sai sem cabeçalho de tenant, com o corpo e a atribuição do pacote")
    void emailWithoutBrandKeepsBodyAndAttribution() {
        BrandedEmail email = BrandedEmailComposer.compose(
                null, message(), BrandTheme.DARK, true, null);
        assertThat(email.html()).doesNotContain("<strong")
                .contains("Corpo da mensagem.\nSegunda linha.")
                .contains(PortalBrandView.OPERATED_WITH_LINE);
        assertThat(email.plainText()).startsWith("Corpo da mensagem.")
                .contains(PortalBrandView.OPERATED_WITH_LINE);
    }

    @Test
    @DisplayName("marca com cor e logo mas SEM nome: sem cabeçalho (nem faixa nem <img>), corpo, contato e atribuição intactos")
    void brandWithoutDisplayNameOmitsHeaderOnly() {
        // Comportamento declarado no javadoc do BrandedEmailComposer, na
        // mesma regra da P2.17 (sem nome, não há cabeçalho de marca a
        // aplicar): a omissão é só do cabeçalho — rodapé de contato e
        // atribuição do pacote não dependem do nome.
        TenantPdfBrand nameless = new TenantPdfBrand(TENANT, null, "SAMAE", "#0369A0",
                null, "0800 000 0000", null, null, null, LOGO, LOGO_DARK);
        for (BrandTheme theme : BrandTheme.values()) {
            BrandedEmail email = BrandedEmailComposer.compose(
                    nameless, message(), theme, true, LOGO_URL);
            assertThat(email.html())
                    .doesNotContain("background:#0369A0")
                    .doesNotContain("<img")
                    .doesNotContain("<strong")
                    .contains("Corpo da mensagem.\nSegunda linha.")
                    .contains("0800 000 0000")
                    .contains(PortalBrandView.OPERATED_WITH_LINE);
            assertThat(email.plainText())
                    .startsWith("Corpo da mensagem.")
                    .contains("0800 000 0000")
                    .contains(PortalBrandView.OPERATED_WITH_LINE);
        }
    }

    @Test
    @DisplayName("mensagem ou tema nulos são recusados")
    void nullArgumentsAreRefused() {
        assertThatThrownBy(() -> BrandedEmailComposer.compose(
                fullBrand(), null, BrandTheme.LIGHT, false, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BrandedEmailComposer.compose(
                fullBrand(), message(), null, false, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
