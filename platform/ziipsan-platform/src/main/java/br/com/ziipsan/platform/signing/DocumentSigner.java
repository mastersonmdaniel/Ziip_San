package br.com.ziipsan.platform.signing;

import java.time.Instant;
import java.util.UUID;

/**
 * Servico de assinatura (aceite da P3.34): a interface unica que os
 * consumidores (P4.1, ED-19, ED-23, ED-38) chamam. Uma implementacao por nivel
 * da Lei 14.063/2020: {@code SimpleDocumentSigner}, {@code AdvancedDocumentSigner}
 * e {@code QualifiedDocumentSigner}. Quem escolhe a implementacao e o
 * {@code SignatureService}, depois de conferir o nivel minimo do tenant.
 */
public interface DocumentSigner {

    /** Nivel que esta implementacao produz — nunca outro. */
    SignatureLevel level();

    /**
     * Assina o conteudo. Implementacao que nao consegue produzir o seu nivel
     * com as evidencias/configuracao exigidas RECUSA com excecao explicita:
     * assinar "mais fraco" em silencio violaria o nivel minimo do tenant.
     */
    SignatureRecord sign(SignContext context);

    /**
     * Pedido de assinatura: o conteudo exato (bytes) e quem assina.
     *
     * <p>{@code signerSubject} e o sujeito exibido/auditado da assinatura
     * (quem assina, como o {@code document_signatures.signer_subject} grava).
     * Na borda HTTP ele NAO vem do corpo: vem do principal autenticado
     * (correcao D3, ver {@code SigningController}).
     *
     * <p>Os tres ultimos componentes sao a chave REAL do aceite da P1.24
     * (correcao D6): a tabela {@code platform.document_acceptance} (core
     * V9_5) identifica o aceite por {@code user_id} (UUID),
     * {@code document_code} e {@code document_version} — a assinatura SIMPLES
     * exige os tres, fornecidos por quem chama (o consumidor sabe qual aceite
     * esta invocando; este pacote NAO mantem tabela de conversao
     * tipo-de-documento → {@code document_code}, que seria mapeamento
     * inventado). Nos demais niveis eles podem ser nulos.
     */
    record SignContext(UUID tenantId, SignableDocumentType documentType, String documentReference,
                       byte[] content, String signerSubject, UUID signerUserId,
                       String acceptanceDocumentCode, String acceptanceDocumentVersion) {

        public SignContext {
            if (tenantId == null) {
                throw new IllegalArgumentException("tenant e obrigatorio");
            }
            if (documentType == null) {
                throw new IllegalArgumentException("tipo de documento e obrigatorio");
            }
            if (documentReference == null || documentReference.isBlank()) {
                throw new IllegalArgumentException("referencia do documento e obrigatoria");
            }
            if (content == null || content.length == 0) {
                throw new IllegalArgumentException("conteudo do documento e obrigatorio");
            }
            if (signerSubject == null || signerSubject.isBlank()) {
                throw new IllegalArgumentException("signatario e obrigatorio");
            }
            content = content.clone();
        }

        /** Contexto sem a chave do aceite da P1.24 (niveis ADVANCED/QUALIFIED e recusas anteriores a simples). */
        public SignContext(UUID tenantId, SignableDocumentType documentType, String documentReference,
                           byte[] content, String signerSubject) {
            this(tenantId, documentType, documentReference, content, signerSubject, null, null, null);
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }

    /**
     * Assinatura produzida e persistida em {@code document_signatures} (V1_14).
     * {@code verificationCode} e o codigo publico da pagina de validacao;
     * {@code verificationDigest} e o CHECKSUM DE INTEGRIDADE do registro
     * (SHA-256 sem segredo — detecta corrupcao/reescrita inconsistente, NAO
     * protege contra adulteracao por quem escreve na tabela; ver
     * {@link SignatureVerification}, correcao D1).
     *
     * <p>{@code signedAt} e canonizado para MICROSEGUNDOS ja neste record
     * (correcao D2): e a precisao do {@code timestamptz}, entao nenhum caminho
     * (signer, releitura do banco, reconstrucao em teste) alimenta o checksum
     * com nanossegundos que o banco descartaria.
     */
    record SignatureRecord(UUID id, UUID tenantId, SignableDocumentType documentType,
                           String documentReference, SignatureLevel level, String contentHash,
                           String signerSubject, Instant signedAt, String verificationCode,
                           String verificationDigest, String signatureFormat,
                           String pscProvider, String signaturePolicyId,
                           String timestampAuthority, String timestampTokenHash,
                           String evidenceJson) {

        public SignatureRecord {
            signedAt = signedAt == null ? null : SignatureVerification.canonical(signedAt);
        }
    }
}
