package br.com.ziipsan.commercial.registry.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Representação do titular (P1.41, V3_8): representante legal e terceiro
 * autorizado, com escopo (poderes), validade e documento comprobatório
 * obrigatório.
 *
 * <p>Regras, aplicadas pelo serviço (o banco é backstop):
 * <ul>
 *   <li>representado ≠ representante, ambos clientes cadastrados;</li>
 *   <li>ao menos um poder, todos do domínio
 *       (VIEW, REQUEST, PAY, CHANGE_BILLING, OWNERSHIP);</li>
 *   <li>unidade opcional (representação geral quando nula);</li>
 *   <li>documento comprobatório obrigatório (proof_document_id, referência ao
 *       documento no pipeline da P1.20 — nunca o conteúdo);</li>
 *   <li>efeito só no encerramento da OS REPRESENTATIVE_SET, com aprovação
 *       por outra pessoa: o pedido nasce PENDING_APPROVAL e só vira ACTIVE
 *       na aprovação;</li>
 *   <li>representação vencida deixa de valer: a vigência é conferida no uso
 *       ({@code HolderSituationService.isRepresentationValid}) — não há
 *       varredura que a marque EXPIRED; o status EXPIRED existe para o
 *       encerramento explícito pelo backoffice.</li>
 * </ul>
 *
 * <p>Não usar {@code @Builder}: o Lombok descarta os campos herdados de
 * {@code BaseEntity}.
 */
@Entity
@Table(name = "customer_representations")
@Getter
@Setter
public class CustomerRepresentation extends TenantAwareEntity {

    @Column(name = "represented_id", nullable = false)
    private UUID representedId;

    @Column(name = "representative_id", nullable = false)
    private UUID representativeId;

    @Column(name = "type", nullable = false, length = 25)
    private String type;

    /** Poderes, como array de texto (VIEW, REQUEST, PAY, CHANGE_BILLING, OWNERSHIP). */
    @Column(name = "powers", nullable = false, columnDefinition = "text[]")
    private String[] powers;

    @Column(name = "unit_id")
    private UUID unitId;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "proof_document_id", nullable = false)
    private UUID proofDocumentId;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING_APPROVAL";

    @Column(name = "service_order_id")
    private UUID serviceOrderId;

    /** Tipos de representação do plano (P1.41). */
    public static final class Types {
        public static final String ATTORNEY = "ATTORNEY";
        public static final String GUARDIAN = "GUARDIAN";
        public static final String CURATOR = "CURATOR";
        public static final String CONDO_MANAGER = "CONDO_MANAGER";
        public static final String ESTATE_ADMINISTRATOR = "ESTATE_ADMINISTRATOR";
        public static final String LEGAL_REPRESENTATIVE = "LEGAL_REPRESENTATIVE";
        public static final String AUTHORIZED_THIRD_PARTY = "AUTHORIZED_THIRD_PARTY";

        private Types() {
        }
    }

    /** Poderes do plano (P1.41). */
    public static final class Powers {
        public static final String VIEW = "VIEW";
        public static final String REQUEST = "REQUEST";
        public static final String PAY = "PAY";
        public static final String CHANGE_BILLING = "CHANGE_BILLING";
        public static final String OWNERSHIP = "OWNERSHIP";

        private Powers() {
        }
    }
}
