package br.com.ziipsan.platform.brandview;

/**
 * Tema de exibição da marca (P3.21): o aceite pede "teste visual do portal e
 * de um e-mail **por tema**" — os dois temas são o claro e o escuro, os
 * mesmos das duas variantes de logo da P1.19: {@code branding.logo} e
 * {@code branding.logo_dark}.
 *
 * <p>O tema decide, nesta camada: qual variante de logo usar e qual fundo de
 * página/carta aplicar. A cor da marca em si NÃO muda com o tema — o que
 * muda é a cor de texto que a acompanha (ver {@link PortalBrandView}).
 */
public enum BrandTheme {

    /** Tema claro: fundo branco (o papel, {@code #FFFFFF}). */
    LIGHT,

    /** Tema escuro: fundo no texto escuro padrão do MARCA-E-TEMAS ({@code #0F172A}). */
    DARK
}
