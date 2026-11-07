package br.com.ziipsan.commercial.registry.legacyxml;

import java.util.Optional;

/**
 * Porta de validação da assinatura XMLDSig do XML de legado (P2.19): o
 * importador só aceita documento cuja assinatura valide criptograficamente
 * contra a chave pública que o próprio XML carrega no {@code KeyInfo}
 * (certificado X.509 do assinante ou, em fixture de teste, a chave pública).
 *
 * <p>A implementação entregue nesta tarefa é a do JDK
 * ({@link JdkXmlSignatureValidator}, {@code javax.xml.crypto.dsig}, sem
 * dependência nova): ela confere a validade do núcleo da assinatura E de
 * cada referência (o resumo do conteúdo assinado), de modo que qualquer
 * alteração posterior à assinatura reprova. A amarração do certificado do
 * assinante a uma cadeia ICP-Brasil NÃO é feita aqui — como no restante do
 * projeto local, a prova de cadeia é do ambiente/da integração; o que esta
 * camada prova é integridade + posse da chave que assinou.
 *
 * @param valida          verdadeiro somente se a assinatura existe, é única
 *                        e valida por inteiro
 * @param signerDocument  documento do assinante extraído do assunto do
 *                        certificado X.509 do {@code KeyInfo} (sufixo
 *                        {@code :CNPJ} do CN), quando há certificado; vazio
 *                        quando o {@code KeyInfo} traz só a chave pública
 * @param detail          motivo legível da reprovação, sem ecoar conteúdo
 *                        do XML (o XML real traz dado pessoal do destinatário)
 */
public interface XmlSignatureValidatorPort {

    record SignatureResult(boolean valida, Optional<String> signerDocument, String detail) {
        static SignatureResult ok(Optional<String> signerDocument) {
            return new SignatureResult(true, signerDocument, "assinatura válida");
        }

        static SignatureResult invalid(String detail) {
            return new SignatureResult(false, Optional.empty(), detail);
        }
    }

    SignatureResult validate(byte[] xmlBytes);
}
