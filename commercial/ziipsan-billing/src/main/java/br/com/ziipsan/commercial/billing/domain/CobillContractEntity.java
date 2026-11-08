package br.com.ziipsan.commercial.billing.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Contrato de cofaturamento por servico (ED-25, tabela pendente
 * {@code cobill_contracts}): prestador, rotulo discriminado, vigencia,
 * data do aviso (P2.32) e inicio da cobranca. Espelha
 * {@link br.com.ziipsan.commercial.billing.cobill.CobillContract}.
 *
 * <p>Nao usar {@code @Builder}: padrao das entidades do billing (ED-22).
 */
@Entity
@Table(name = "cobill_contracts")
@Getter
@Setter
public class CobillContractEntity extends TenantAwareEntity {

    @Column(name = "service_kind", nullable = false, length = 30)
    private String serviceKind;

    @Column(name = "provider_id", nullable = false)
    private UUID providerId;

    @Column(name = "provider_name", nullable = false)
    private String providerName;

    @Column(name = "bill_label", nullable = false)
    private String billLabel;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "notice_date", nullable = false)
    private LocalDate noticeDate;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
}
