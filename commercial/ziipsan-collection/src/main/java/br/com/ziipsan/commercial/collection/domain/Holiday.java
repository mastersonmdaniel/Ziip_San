package br.com.ziipsan.commercial.collection.domain;

import br.com.ziipsan.commercial.collection.surcharge.BusinessCalendar;
import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Feriado do calendário de dias úteis (nacional, estadual ou de um município). Nenhum feriado é
 * semeado: o tenant cadastra, com a norma que o institui em {@code source}.
 *
 * <p>Não usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "holidays")
@Getter
@Setter
public class Holiday extends TenantAwareEntity {

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 10)
    private BusinessCalendar.Scope scope;

    @Column(name = "state_code", length = 2)
    private String stateCode;

    @Column(name = "municipality_ibge", length = 7)
    private String municipalityIbge;

    @Column(name = "description", nullable = false, length = 150)
    private String description;

    @Column(name = "source", nullable = false, length = 300)
    private String source;

    public BusinessCalendar.HolidayEntry toEntry() {
        return new BusinessCalendar.HolidayEntry(holidayDate, scope, stateCode, municipalityIbge);
    }
}
