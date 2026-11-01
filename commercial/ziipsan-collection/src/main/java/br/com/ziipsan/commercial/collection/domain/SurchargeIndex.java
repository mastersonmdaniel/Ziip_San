package br.com.ziipsan.commercial.collection.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * Fator mensal de um índice de correção monetária, carregado pelo tenant com a fonte. Nenhum
 * índice é semeado.
 *
 * <p>Não usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "surcharge_indexes")
@Getter
@Setter
public class SurchargeIndex extends TenantAwareEntity {

    @Column(name = "index_code", nullable = false, length = 40)
    private String indexCode;

    /** Mês de referência no formato AAAAMM. */
    @Column(name = "reference", nullable = false)
    private int reference;

    @Column(name = "factor", nullable = false, precision = 20, scale = 10)
    private BigDecimal factor;

    @Column(name = "source", nullable = false, length = 300)
    private String source;

    public YearMonth month() {
        return YearMonth.of(reference / 100, reference % 100);
    }

    public static int referenceOf(YearMonth month) {
        return month.getYear() * 100 + month.getMonthValue();
    }
}
