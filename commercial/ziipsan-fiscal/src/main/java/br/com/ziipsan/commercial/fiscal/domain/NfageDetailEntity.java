package br.com.ziipsan.commercial.fiscal.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Extensão do modelo 75 sobre {@code fiscal_documents} (V4_5, DAD-18): o que é
 * específico da NFAg-e — município do destinatário (endereço da P1.3, DAD-04),
 * contingência com motivo, versão do leiaute e os campos do destinatário
 * <b>condicionais ao MOC definitivo</b> (sem documento; documento somente cifrado,
 * com o hash como índice cego da forma normalizada da P1.39). O documento fiscal em
 * si (emitente, série, número, chave, estados, protocolo, XML) vive no dicionário e
 * não é duplicado aqui.
 *
 * <p>Não usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "fiscal_nfage_details")
@Getter
@Setter
public class NfageDetailEntity extends TenantAwareEntity {

    @Column(name = "fiscal_document_id", nullable = false)
    private UUID fiscalDocumentId;

    @Column(name = "layout_version", nullable = false, length = 20)
    private String layoutVersion;

    @Column(name = "recipient_municipality_ibge", nullable = false, length = 7)
    private String recipientMunicipalityIbge;

    // CONDICIONAL AO MOC DEFINITIVO: indicador de destinatário sem CPF ou CNPJ.
    @Column(name = "recipient_without_document", nullable = false)
    private boolean recipientWithoutDocument;

    // CONDICIONAL AO MOC DEFINITIVO: documento do destinatário SOMENTE cifrado.
    @Column(name = "recipient_document_encrypted", columnDefinition = "text")
    private String recipientDocumentEncrypted;

    // CONDICIONAL AO MOC DEFINITIVO: índice cego (SHA-256 da forma normalizada).
    @Column(name = "recipient_document_hash", length = 64)
    private String recipientDocumentHash;

    @Column(name = "contingency_mode", nullable = false, length = 20)
    private String contingencyMode;

    @Column(name = "contingency_reason", length = 500)
    private String contingencyReason;

    @Column(name = "contingency_started_at")
    private OffsetDateTime contingencyStartedAt;
}
