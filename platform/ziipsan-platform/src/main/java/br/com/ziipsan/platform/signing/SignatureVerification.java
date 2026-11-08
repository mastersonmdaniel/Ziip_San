package br.com.ziipsan.platform.signing;

import br.com.ziipsan.core.json.Json;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Verificacao publica da assinatura (aceite da P3.34): codigo de verificacao
 * impresso no documento (inclusive como QR) que aponta para a pagina de
 * validacao do Ziip e para o VALIDAR do ITI. A pagina publica mostra o
 * resultado da conferencia do registro — nivel, tipo, signatario MASCARADO,
 * instante e hash — sem expor o conteudo do documento nem o signatario
 * integral (LGPD, correcao D10 da revisao cruzada).
 *
 * <p><b>O digest e um CHECKSUM DE INTEGRIDADE, nao protecao contra
 * adulteracao</b> (correcao D1 da revisao cruzada): ele e SHA-256 SEM SEGREDO
 * sobre dados publicos (hash do conteudo + codigo de verificacao + instante).
 * Quem tiver escrita na tabela pode trocar o conteudo, recalcular o checksum
 * com dados publicos e o registro volta a "validar" — ha um teste que prova
 * exatamente isso ({@code SignatureVerificationTest}). O checksum detecta
 * corrupcao acidental e reescrita inconsistente (quem adultera um campo so),
 * e NADA alem disso. A spec da P3.34 nao preve segredo, chave ou parametro
 * {@code sign.*} de integridade; a protecao real contra adulteracao (HMAC com
 * segredo fora da base ou assinatura do digest) e PENDENCIA NOMEADA de
 * integracao/spec — ver {@code CORRECAO-P3.34.md} (D1) e o relatorio da
 * tarefa. Alegacao sem mecanismo, nunca.
 *
 * <p>A URL do VALIDAR e a do servico publico do ITI nomeado na spec. A base
 * da pagina do Ziip NAO e fixada aqui: dominio e decisao de implantacao, e
 * quem monta o QR informa a base (ver {@link #qrPayload}).
 */
public final class SignatureVerification {

    /** VALIDAR do ITI, nomeado na spec como destino ao lado da pagina do Ziip. */
    public static final String ITI_VALIDAR_URL = "https://validar.iti.gov.br/";

    /** Caminho da pagina de validacao do Ziip, relativo a base da implantacao. */
    public static final String ZIIP_VALIDATION_PATH = "/validar-assinatura/";

    /**
     * Desfechos da verificacao. {@code RECORD_TAMPERED} significa "o registro
     * nao confere com o proprio checksum" — corrupcao ou adulteracao parcial,
     * indistinguiveis sem segredo (ver o javadoc da classe). {@code RATE_LIMITED}
     * so aparece no registro de tentativas (a consulta estourada responde 429 e
     * nao devolve desfecho ao chamador).
     */
    public enum Outcome { VALID, RECORD_TAMPERED, NOT_FOUND, RATE_LIMITED }

    private SignatureVerification() {
    }

    /**
     * Forma canonica do instante para qualquer calculo de checksum e para o
     * {@code signedAt} persistido (correcao D2 da revisao cruzada): o
     * {@code timestamptz} do PostgreSQL guarda MICROSEGUNDOS, mas um
     * {@code Clock} real devolve nanossegundos — sem truncar na fonte, o
     * checksum de um registro gravado e relido divergia do checksum da criacao
     * e a verificacao acusava {@code RECORD_TAMPERED} sem adulteracao alguma.
     * TODOS os caminhos que alimentam o checksum passam por aqui.
     */
    public static Instant canonical(Instant instant) {
        Objects.requireNonNull(instant, "instante e obrigatorio");
        return instant.truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * Checksum de integridade do registro: SHA-256 sem segredo de
     * hash do conteudo + codigo + instante canonico. NAO e protecao contra
     * adulteracao (ver o javadoc da classe, D1).
     */
    public static String digestOf(String contentHash, String verificationCode, Instant signedAt) {
        return SignatureHashes.sha256Hex(contentHash + "|" + verificationCode + "|" + canonical(signedAt));
    }

    /** Conferencia do checksum do registro gravado contra o recalculado. */
    public static Outcome check(DocumentSigner.SignatureRecord record) {
        if (record == null) {
            return Outcome.NOT_FOUND;
        }
        String expected = digestOf(record.contentHash(), record.verificationCode(), record.signedAt());
        return expected.equals(record.verificationDigest()) ? Outcome.VALID : Outcome.RECORD_TAMPERED;
    }

    /**
     * Carga do QR: a URL da pagina de validacao do Ziip para este codigo. O QR
     * em si (imagem) e renderizacao de quem emite o documento; a carga e esta.
     */
    public static String qrPayload(String ziipBaseUrl, String verificationCode) {
        if (ziipBaseUrl == null || ziipBaseUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "base da pagina de validacao do Ziip e obrigatoria (dominio da implantacao, nao fixado no codigo)");
        }
        if (verificationCode == null || verificationCode.isBlank()) {
            throw new IllegalArgumentException("codigo de verificacao e obrigatorio");
        }
        String base = ziipBaseUrl.endsWith("/") ? ziipBaseUrl.substring(0, ziipBaseUrl.length() - 1) : ziipBaseUrl;
        return base + ZIIP_VALIDATION_PATH + verificationCode;
    }

    /**
     * Mascara do signatario para a superficie publica (correcao D10/LGPD):
     * o codigo de verificacao e uma credencial de portador — quem o tem nao
     * precisa (nem deve) ver o sujeito integral, que pode ser CPF ou nome
     * completo. Mostra so as duas primeiras e as duas ultimas letras.
     */
    public static String maskSignerSubject(String signerSubject) {
        if (signerSubject == null || signerSubject.length() <= 4) {
            return "***";
        }
        return signerSubject.substring(0, 2) + "***" + signerSubject.substring(signerSubject.length() - 2);
    }

    /**
     * JSON publico da validacao: sem conteudo, sem evidencia interna e sem
     * signatario integral (D10). Se o desfecho nao e {@code VALID}, o JSON
     * carrega SO o desfecho, o codigo e o link do ITI — num registro que nao
     * conferiu com o proprio checksum, nenhum campo dele e confiavel nem
     * deve ser exposto.
     */
    public static String publicJson(DocumentSigner.SignatureRecord record, Outcome outcome) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("outcome", outcome.name());
        map.put("itiValidarUrl", ITI_VALIDAR_URL);
        if (record != null) {
            map.put("verificationCode", record.verificationCode());
        }
        if (record != null && outcome == Outcome.VALID) {
            map.put("documentType", record.documentType().name());
            map.put("level", record.level().name());
            map.put("signerSubjectMasked", maskSignerSubject(record.signerSubject()));
            map.put("signedAt", record.signedAt().toString());
            map.put("contentHash", record.contentHash());
            if (record.level() == SignatureLevel.QUALIFIED) {
                map.put("signatureFormat", record.signatureFormat());
                map.put("pscProvider", record.pscProvider());
                map.put("signaturePolicyId", record.signaturePolicyId());
                map.put("timestampAuthority", record.timestampAuthority());
            }
        }
        return Json.write(map);
    }
}
