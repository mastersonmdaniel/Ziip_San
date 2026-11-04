package br.com.ziipsan.commercial.registry.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Resultado da validação externa de CPF/CNPJ (P1.41, V3_8).
 *
 * <p>SÓ situação e data são guardadas — nunca o número do documento (que
 * continua cifrado em customers, com índice cego e máscara). Base legal
 * registrada: LGPD, art. 7º. A credencial do provedor vive no cofre e a
 * consulta é paga por tenant com custo medido (frente de integração futura:
 * esta frente registra o resultado informado pelo operador ou pelo provedor,
 * não executa a consulta).
 *
 * <p>Efeitos do resultado (serviço, não banco):
 * <ul>
 *   <li>IRREGULAR gera alerta, não bloqueio: o registro fica consultável
 *       como irregular; a operação que o originou prossegue;</li>
 *   <li>DECEASED abre alerta de óbito (um por unidade com vínculo vigente
 *       do cliente, ou um sem unidade) com OS HOLDER_DECEASED, sem efeito
 *       automático — revisão humana obrigatória;</li>
 *   <li>condição da troca de titularidade: com
 *       {@code registry.validate_document_online} diferente de NONE, a
 *       troca exige validação REGULAR registrada (e, no modo
 *       STATUS_AND_BIOMETRICS, com biometric_verified).</li>
 * </ul>
 *
 * <p>Não usar {@code @Builder}: o Lombok descarta os campos herdados de
 * {@code BaseEntity}.
 */
@Entity
@Table(name = "customer_document_validations")
@Getter
@Setter
public class CustomerDocumentValidation extends TenantAwareEntity {

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "document_kind", nullable = false, length = 5)
    private String documentKind;

    @Column(name = "result", nullable = false, length = 20)
    private String result;

    @Column(name = "provider", length = 100)
    private String provider;

    @Column(name = "legal_basis", nullable = false, length = 100)
    private String legalBasis = "LGPD art. 7º";

    @Column(name = "biometric_verified", nullable = false)
    private boolean biometricVerified;

    @Column(name = "validated_at", nullable = false)
    private OffsetDateTime validatedAt;

    @Column(name = "service_order_id")
    private UUID serviceOrderId;

    /** Resultados possíveis da validação externa. */
    public static final class Results {
        public static final String REGULAR = "REGULAR";
        public static final String IRREGULAR = "IRREGULAR";
        public static final String NOT_FOUND = "NOT_FOUND";
        public static final String UNABLE_TO_VERIFY = "UNABLE_TO_VERIFY";
        public static final String DECEASED = "DECEASED";

        private Results() {
        }
    }
}
