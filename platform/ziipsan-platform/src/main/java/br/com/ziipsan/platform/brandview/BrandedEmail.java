package br.com.ziipsan.platform.brandview;

/**
 * Um e-mail com a marca do tenant aplicada (P3.21): o assunto e a
 * proveniência ({@code templateCode}/{@code templateVersion}) são os da
 * mensagem montada pelo motor da P2.30 que a P3.5 despacha
 * ({@code RenderedMessage}) — mudar o modelo depois não muda o que foi
 * enviado, e aplicar a marca não muda o assunto nem o corpo (ver
 * {@link BrandedEmailComposer}).
 *
 * <p>Duas formas da mesma carta: {@code html} (a visual, com cabeçalho na
 * cor da marca, logo do tema e rodapé de contato/atribuição) e
 * {@code plainText} (a alternativa de texto puro, com o corpo byte a byte
 * e o mesmo rodapé em linhas). O gateway da P0.45/P3.5 decide o transporte;
 * esta camada só monta as duas formas.
 */
public record BrandedEmail(
        String subject,
        String html,
        String plainText,
        String templateCode,
        int templateVersion) {

    public BrandedEmail {
        if (subject == null || subject.isBlank() || html == null || html.isBlank()
                || plainText == null || plainText.isBlank()
                || templateCode == null || templateCode.isBlank() || templateVersion < 1) {
            throw new IllegalArgumentException(
                    "e-mail com marca exige assunto, html, texto puro, modelo e versão >= 1");
        }
    }
}
