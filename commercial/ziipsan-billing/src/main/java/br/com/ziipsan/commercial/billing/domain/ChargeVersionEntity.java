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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Versao de cobranca do catalogo unico (Camada 1 da arquitetura
 * tarifaria): espelho JPA de {@code charge_versions} e do record
 * {@code ChargeVersion} do pacote {@code catalog}. Os canais nomeados
 * ({@code tariffVersionId}, {@code priceItemCode},
 * {@code externalContractId}) sao colunas proprias; os valores do
 * modelo vivem nas colunas explodidas ({@code percent},
 * {@code fixedAmount}, {@code unitPrice}) e o {@code parameters}
 * guarda a serializacao jsonb desses parametros, espelhando o
 * {@code ChargeParameters} do dominio (desenho da Camada 0, secao
 * 1.3), no mesmo padrao jsonb do {@code BillEntity}. O dominio puro
 * nao depende desta entidade.
 *
 * <p>A coluna {@code tariff_version_ids} ({@code UUID[]}) do SQL
 * pendente NAO e mapeada aqui: e a pendencia de integracao P-C1-01,
 * que subsiste — a forma de ligacao (array x tabela) e decisao da
 * integracao, nao desta camada.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados
 * de {@code BaseEntity}.
 */
@Entity
@Table(name = "charge_versions")
@Getter
@Setter
public class ChargeVersionEntity extends TenantAwareEntity {

    @Column(name = "charge_definition_id", nullable = false)
    private UUID chargeDefinitionId;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "calculation_model", nullable = false, length = 40)
    private String calculationModel;

    @Column(name = "tariff_version_id")
    private UUID tariffVersionId;

    @Column(name = "price_item_code", length = 100)
    private String priceItemCode;

    @Column(name = "external_contract_id")
    private UUID externalContractId;

    @Column(name = "percent", precision = 10, scale = 4)
    private BigDecimal percent;

    @Column(name = "fixed_amount", precision = 15, scale = 2)
    private BigDecimal fixedAmount;

    @Column(name = "unit_price", precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters", columnDefinition = "jsonb")
    private String parameters;
}
