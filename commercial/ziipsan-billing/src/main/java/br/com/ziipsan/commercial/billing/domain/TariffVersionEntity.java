package br.com.ziipsan.commercial.billing.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Versao (vigencia por data) de uma tarifa. Intervalos da mesma tarifa nao se sobrepoem (restricao de exclusao no banco). {@code socialMinChargeDiscountPct} nulo = a versao nao preve tarifa social.
 *
 * <p>Os campos de gestao tarifaria (P2.29, V4_9) guardam, por versao, a
 * origem da mudanca e os fatos do reajuste/revisao: tipo, versao de origem,
 * data-base, indice e percentuais, formula, homologacao e o metodo de
 * calculo mantido para a transparencia (NR 13, arts. 61 e 62). Versoes
 * anteriores a P2.29 tem todos eles nulos.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "tariff_versions")
@Getter
@Setter
public class TariffVersionEntity extends TenantAwareEntity {

    @Column(name = "tariff_id", nullable = false)
    private UUID tariffId;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "social_min_charge_discount_pct", precision = 5, scale = 2)
    private BigDecimal socialMinChargeDiscountPct;

    @Column(name = "regulatory_act", length = 200)
    private String regulatoryAct;

    @Column(name = "published_on")
    private LocalDate publishedOn;

    // ---- Gestao tarifaria (P2.29, V4_9) ----

    @Column(name = "change_type", length = 20)
    private String changeType;

    @Column(name = "source_version_id")
    private UUID sourceVersionId;

    @Column(name = "base_date")
    private LocalDate baseDate;

    @Column(name = "index_code", length = 20)
    private String indexCode;

    @Column(name = "index_pct", precision = 9, scale = 4)
    private BigDecimal indexPct;

    @Column(name = "factor_x_pct", precision = 9, scale = 4)
    private BigDecimal factorXPct;

    @Column(name = "applied_pct", precision = 9, scale = 4)
    private BigDecimal appliedPct;

    @Column(name = "readjust_formula", length = 30)
    private String readjustFormula;

    @Column(name = "approval_mode", length = 20)
    private String approvalMode;

    @Column(name = "approved_on")
    private LocalDate approvedOn;

    @Column(name = "calculation_method", columnDefinition = "text")
    private String calculationMethod;
}
