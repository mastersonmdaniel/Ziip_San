package br.com.ziipsan.grs.mtr.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

// ED-104 (produtor, sessão 6d762d43-e1a9-4984-b50d-571ba0643b52) — E2.
// Ligação N:N entre o CDF interno e os MTRs que ele agrega no período. Um MTR destinado entra
// num só CDF (constraint única sobre mtr_id no SQL pendente): agregar duas vezes o mesmo MTR
// duplicaria a massa certificada.
@Entity
@Table(name = "waste_cdf_mtr", indexes = {
        @Index(name = "idx_waste_cdf_mtr_cdf", columnList = "cdf_id"),
        @Index(name = "idx_waste_cdf_mtr_mtr", columnList = "mtr_id", unique = true)
})
@Getter
@Setter
public class WasteCdfMtr extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cdf_id", nullable = false)
    private WasteCdf cdf;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mtr_id", nullable = false, unique = true)
    private WasteMtr mtr;
}
