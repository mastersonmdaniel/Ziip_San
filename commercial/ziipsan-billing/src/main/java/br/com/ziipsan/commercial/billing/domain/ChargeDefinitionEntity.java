package br.com.ziipsan.commercial.billing.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Definicao de cobranca do catalogo unico (Camada 1 da arquitetura
 * tarifaria): espelho JPA de {@code charge_definitions} e do record
 * {@code ChargeDefinition} do pacote {@code catalog}. O dominio puro
 * nao depende desta entidade; ela existe para a persistencia, no
 * mesmo padrao das vizinhas ({@code TariffEntity}/{@code BillEntity}).
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados
 * de {@code BaseEntity}.
 */
@Entity
@Table(name = "charge_definitions")
@Getter
@Setter
public class ChargeDefinitionEntity extends TenantAwareEntity {

    @Column(name = "charge_code", nullable = false, length = 100)
    private String chargeCode;

    @Column(name = "name", nullable = false, length = 300)
    private String name;

    @Column(name = "owner", nullable = false, length = 20)
    private String owner;

    @Column(name = "revenue_nature", nullable = false, length = 30)
    private String revenueNature;

    @Column(name = "calculation_model", nullable = false, length = 40)
    private String calculationModel;

    @Column(name = "third_party_provider_id")
    private UUID thirdPartyProviderId;

    @Column(name = "homologation_required", nullable = false)
    private boolean homologationRequired;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "field_calculable", nullable = false)
    private boolean fieldCalculable;

    @Column(name = "accounting_key_ref")
    private String accountingKeyRef;

    // Escopo municipal tipado (item 45 / D-01): codigo IBGE de 7
    // digitos do municipio, ou NULL = escopo "empresa" (todo o
    // tenant, comportamento vigente). Espelho da coluna
    // municipality_ibge e do campo municipalityIbge do record.
    @Column(name = "municipality_ibge", length = 7)
    private String municipalityIbge;
}
