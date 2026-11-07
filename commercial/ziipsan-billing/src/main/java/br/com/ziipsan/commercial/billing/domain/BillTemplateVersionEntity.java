package br.com.ziipsan.commercial.billing.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Versao (vigencia) de um modelo de conta (V4_4, P2.5), no mesmo desenho de
 * {@code TariffVersionEntity}: intervalo [validFrom, validTo] em datas, sem
 * sobreposicao por modelo (restricao de exclusao no banco). {@code layout} e a
 * estrutura do modelo em JSON (blocos e ordem), gravada pela classe Json do
 * core -- nenhum dado pessoal entra aqui.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "bill_template_versions")
@Getter
@Setter
public class BillTemplateVersionEntity extends TenantAwareEntity {

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "layout", nullable = false, columnDefinition = "jsonb")
    private String layout;
}
