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
 * Vinculo unidade -> orgao centralizador (ED-22, tabela pendente
 * {@code central_billing_links}), criado pela OS CENTRAL_BILLING_LINK.
 * Nao usar {@code @Builder} (padrao do billing).
 */
@Entity
@Table(name = "central_billing_links")
@Getter
@Setter
public class CentralBillingLinkEntity extends TenantAwareEntity {

    @Column(name = "centralizer_customer_id", nullable = false)
    private UUID centralizerCustomerId;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "link_os_id", nullable = false)
    private UUID linkOsId;

    @Column(name = "start_month", nullable = false)
    private LocalDate startMonth;

    @Column(name = "end_month")
    private LocalDate endMonth;
}
