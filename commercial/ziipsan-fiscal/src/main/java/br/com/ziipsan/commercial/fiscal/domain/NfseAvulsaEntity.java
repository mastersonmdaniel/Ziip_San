package br.com.ziipsan.commercial.fiscal.domain;

import br.com.ziipsan.commercial.fiscal.nfse.NfseEtapa;
import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * NFS-e avulsa gerada a partir de uma ordem de serviço (P3.6). Os CNPJs são gravados na
 * forma normalizada validada pela P1.39 ({@code DocumentNumber}); cifragem/índice cego de
 * documento, quando aplicável ao prestador/tomador, seguem a política do cadastro (P0.11) e
 * não são decididos nesta tarefa.
 *
 * <p>Não usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "fiscal_nfse_avulsas")
@Getter
@Setter
public class NfseAvulsaEntity extends TenantAwareEntity {

    @Column(name = "service_order_id", nullable = false)
    private UUID serviceOrderId;

    @Column(name = "service_type_code", nullable = false, length = 40)
    private String serviceTypeCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "etapa_aplicavel", nullable = false, length = 40)
    private NfseEtapa etapaAplicavel;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "prestador_cnpj", nullable = false, length = 14)
    private String prestadorCnpj;

    @Column(name = "tomador_cnpj", nullable = false, length = 14)
    private String tomadorCnpj;

    @Column(name = "service_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal serviceAmount;

    @Column(name = "com_grupos_ibs_cbs", nullable = false)
    private boolean comGruposIbsCbs;

    @Column(name = "ambiente", nullable = false, length = 30)
    private String ambiente;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "dps_payload", nullable = false, columnDefinition = "jsonb")
    private String dpsPayload;
}
