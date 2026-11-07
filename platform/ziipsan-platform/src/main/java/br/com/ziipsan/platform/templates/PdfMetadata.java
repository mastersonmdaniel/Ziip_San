package br.com.ziipsan.platform.templates;

/**
 * Metadados do PDF gerado (P2.17): o que o renderizador de PDF do
 * consumidor do motor grava nas propriedades do documento (título, autor,
 * assunto). O motor da P2.30 renderiza texto; a conversão para PDF é do
 * consumidor (ver {@link TemplateChannel}) — esta tarefa entrega os
 * VALORES da marca, não um renderizador.
 *
 * <p>Regras: o autor é o nome de exibição do tenant (a marca é dele, não
 * do produto); sem marca, autor é o próprio título do documento (nunca a
 * marca do produto fingindo autoria do tenant). Criador é o sistema que
 * gerou, declarado como está — {@code ZiipSan} é o nome do produto no
 * repositório, não um dado do tenant.
 */
public record PdfMetadata(String title, String author, String subject, String creator) {

    /** Criador declarado nos metadados: o sistema gerador. */
    public static final String CREATOR = "ZiipSan";

    public PdfMetadata {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title e obrigatorio nos metadados do PDF");
        }
        title = title.strip();
        author = author == null || author.isBlank() ? title : author.strip();
        subject = subject == null ? null : subject.strip();
        creator = CREATOR;
    }

    /**
     * Metadados de um documento gerado: título do documento, autor = nome
     * da marca do tenant (quando há marca), assunto = tipo do documento.
     */
    public static PdfMetadata of(String documentTitle, TemplateDocumentType documentType,
                                 TenantPdfBrand brand) {
        if (documentType == null) {
            throw new IllegalArgumentException("documentType e obrigatorio nos metadados do PDF");
        }
        String author = brand != null && brand.hasDisplayName() ? brand.displayName() : null;
        return new PdfMetadata(documentTitle, author, documentType.label(), CREATOR);
    }
}
