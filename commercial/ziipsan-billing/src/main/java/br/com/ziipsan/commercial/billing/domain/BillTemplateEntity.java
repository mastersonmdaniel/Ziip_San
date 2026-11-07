package br.com.ziipsan.commercial.billing.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Modelo de conta (V4_4, P2.5): o desenho da fatura que o parametro
 * {@code bill.invoice_template} (catalogo da core V3, tipo VERSION) referencia.
 * O conteudo versionado vive em {@link BillTemplateVersionEntity}; o conteudo
 * minimo da fatura e a emissao sao da P2.32.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "bill_templates")
@Getter
@Setter
public class BillTemplateEntity extends TenantAwareEntity {

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
