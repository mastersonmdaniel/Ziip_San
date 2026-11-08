package br.com.ziipsan.platform.portal.selfcare;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Documento autentico do portal (ED-38): certidoes, declaracoes e
 * comprovantes saem com CODIGO VERIFICADOR ou QR de autenticidade, pela
 * politica de assinatura da ED-34 ({@code doc.signature_policy}) e nos
 * niveis da P3.34 (SIMPLE/ADVANCED/QUALIFIED de {@code SignatureLevel}).
 *
 * <p>Esta classe NAO reimplementa assinatura nem codigo: o codigo e
 * obtido da porta {@link AuthenticityPort}, cujo adaptador de integracao
 * delega a {@code SignaturePolicy.verificationCode} da ED-34 (SHA-256
 * truncado, deterministico) e, quando o nivel exigido pela P3.34 for
 * ADVANCED/QUALIFIED, a assinatura ao servico da P3.34. Sem adaptador,
 * nenhum documento e emitido (falha fechada). O QR carrega a URL publica
 * de validacao + o codigo — validar e recalcular/conferir, nunca confiar
 * no papel.
 *
 * <p>Niveis: o nome do nivel vem da politica da ED-34 e tem de ser um dos
 * tres nomes da P3.34; nome fora disso recusa a emissao.
 */
public final class AuthenticDocument {

    public enum Kind { CERTIFICATE, DECLARATION, RECEIPT }

    public interface AuthenticityPort {
        /** Nivel da politica da ED-34 para o tipo de documento (SIMPLE|ADVANCED|QUALIFIED). */
        String signatureLevelFor(String documentType);

        /** Codigo verificador da ED-34 para (tipo, numero, data de emissao). */
        String verificationCode(String documentType, String documentNumber, String issuedOnIso);

        /** URL publica de validacao do tenant (dominio proprio, P2.27). */
        String publicValidationBaseUrl(UUID tenantId);
    }

    public record Issued(Kind kind, String documentType, String documentNumber, LocalDate issuedOn,
                         String signatureLevel, String verificationCode, String qrPayload) { }

    private final AuthenticityPort port;

    public AuthenticDocument(AuthenticityPort port) {
        if (port == null) {
            throw new IllegalArgumentException("porta de autenticidade (ED-34/P3.34) e obrigatoria");
        }
        this.port = port;
    }

    public Issued issue(UUID tenantId, Kind kind, String documentType, String documentNumber, LocalDate issuedOn) {
        if (tenantId == null || kind == null || documentType == null || documentType.isBlank()
                || documentNumber == null || documentNumber.isBlank() || issuedOn == null) {
            throw new IllegalArgumentException("tenant, tipo, numero e data de emissao sao obrigatorios");
        }
        String level = port.signatureLevelFor(documentType);
        if (!"SIMPLE".equals(level) && !"ADVANCED".equals(level) && !"QUALIFIED".equals(level)) {
            throw new IllegalStateException("nivel de assinatura fora dos niveis da P3.34 para " + documentType);
        }
        String code = port.verificationCode(documentType, documentNumber, issuedOn.toString());
        if (code == null || code.isBlank()) {
            throw new IllegalStateException("ED-34 nao devolveu codigo verificador para " + documentType);
        }
        String base = port.publicValidationBaseUrl(tenantId);
        if (base == null || base.isBlank()) {
            throw new IllegalStateException("tenant sem URL publica de validacao: documento nao emitido");
        }
        String qr = base.replaceAll("/+$", "") + "/validar?tipo=" + documentType
                + "&numero=" + documentNumber + "&codigo=" + code;
        return new Issued(kind, documentType, documentNumber, issuedOn, level, code, qr);
    }

    /** Conferencia publica: recalcula pela porta e compara — adulteracao de qualquer campo muda o codigo. */
    public boolean verifies(String documentType, String documentNumber, LocalDate issuedOn, String presentedCode) {
        if (presentedCode == null) {
            return false;
        }
        String expected = port.verificationCode(documentType, documentNumber, issuedOn.toString());
        return expected != null && expected.equalsIgnoreCase(presentedCode.trim());
    }
}
