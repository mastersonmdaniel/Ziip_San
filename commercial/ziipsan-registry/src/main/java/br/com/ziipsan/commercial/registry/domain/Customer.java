package br.com.ziipsan.commercial.registry.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Cliente (pessoa fisica ou juridica). O documento NUNCA e guardado em claro: ciphertext (AES-256-GCM por tenant), hash (indice cego) e mascara.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "customers")
@Getter
@Setter
public class Customer extends TenantAwareEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "person_type", nullable = false)
    private PersonType personType;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "trade_name", length = 200)
    private String tradeName;

    @Column(name = "document_ciphertext", columnDefinition = "text")
    private String documentCiphertext;

    @Column(name = "document_hash", length = 64)
    private String documentHash;

    @Column(name = "document_masked", length = 30)
    private String documentMasked;

    @Column(name = "birth_date")
    private java.time.LocalDate birthDate;

    /**
     * P0.16 opcao 1: campos que o frontend ja envia. customer_type_id e activity_branch_id
     * sao UUIDs SEM FK: as tabelas de catalogo ainda nao existem no backend (a frente de
     * catalogos as criara; ai a FK podera ser acrescentada). parent_customer_id TEM FK
     * (auto-referencia composta por tenant, ver V3_4): a tabela referenciada existe.
     */
    @Column(name = "customer_type_id")
    private java.util.UUID customerTypeId;

    @Column(name = "activity_branch_id")
    private java.util.UUID activityBranchId;

    @Column(name = "parent_customer_id")
    private java.util.UUID parentCustomerId;

    /** Motivo para cadastrar sem documento (texto livre, max 300; redigido na auditoria). */
    @Column(name = "document_exemption_reason", length = 300)
    private String documentExemptionReason;

    /**
     * P1.3 (V3_12): motivo de dispensa por catálogo (document_exemption_reasons).
     * O texto livre acima FICA (compatibilidade); o catálogo é a referência nova.
     */
    @Column(name = "document_exemption_reason_id")
    private java.util.UUID documentExemptionReasonId;

    /** P1.3 (V3_12): esfera de governo; obrigatória para ente público (serviço). */
    @Column(name = "government_sphere", length = 10)
    private String governmentSphere;

    /** P1.3 (V3_12): motivo de exclusão por catálogo (customer_delete_reasons). */
    @Column(name = "delete_reason_id")
    private java.util.UUID deleteReasonId;

    /**
     * P1.41 (V3_8): nome social, só em PF (CHECK no banco). Usado no
     * atendimento, no portal e nas mensagens; o nome civil fica no documento
     * fiscal e nos termos (ver HolderSituationService.displayName e o
     * parâmetro registry.social_name_display).
     */
    @Column(name = "social_name", length = 200)
    private String socialName;

    @Column(name = "due_day")
    private Short dueDay;

    /** Cobranca: vivem em customers, onde due_day ja vive (nao ha tabela de cobranca). */
    @Column(name = "next_month_due", nullable = false)
    private boolean nextMonthDue;

    @Column(name = "advance_invoice", nullable = false)
    private boolean advanceInvoice;

    /** Apenas persistido: nenhuma regra de negativacao e aplicada por este flag aqui. */
    @Column(name = "block_negativation", nullable = false)
    private boolean blockNegativation;

    @Column(name = "deleted_at")
    private java.time.OffsetDateTime deletedAt;

    @Column(name = "deleted_reason", length = 200)
    private String deletedReason;

    public enum PersonType { PF, PJ }

}
