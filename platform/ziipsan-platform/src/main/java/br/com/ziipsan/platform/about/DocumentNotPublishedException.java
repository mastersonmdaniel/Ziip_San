package br.com.ziipsan.platform.about;

/**
 * P1.24: o documento não existe no catálogo ou está inativo — não há versão
 * vigente para aceitar. O controller traduz para 404: o documento "não
 * existe para este tenant", inclusive quando o código é válido mas ainda
 * não foi publicado (textos legais são conteúdo do dono; sem publicação,
 * não há o que aceitar).
 */
public class DocumentNotPublishedException extends RuntimeException {

    public DocumentNotPublishedException(String code) {
        super("Documento não publicado: " + code);
    }
}
