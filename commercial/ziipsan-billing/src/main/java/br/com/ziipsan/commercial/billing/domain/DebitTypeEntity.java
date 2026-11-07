package br.com.ziipsan.commercial.billing.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Tipo de debito do catalogo {@code debit_types} (DAD-20, P2.5; trecho F3 da V4_4). Substitui o
 * {@code type_code} em texto livre de {@code debit_receivables}. O catalogo comeca vazio: tipos
 * de debito sao politica do prestador, nunca dado inventado (mesma regra da V4_1/V4_2).
 *
 * <p>{@code generatesBill} nao tem padrao: e decisao do tipo de debito, cadastrada aqui — a
 * coluna sai de {@code debit_receivables} e passa a viver so no catalogo.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "debit_types")
@Getter
@Setter
public class DebitTypeEntity extends TenantAwareEntity {

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "description", nullable = false, length = 200)
    private String description;

    @Column(name = "generates_bill", nullable = false)
    private Boolean generatesBill;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
