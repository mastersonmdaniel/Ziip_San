package br.com.ziipsan.commercial.billing.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Motivo de cancelamento do catalogo {@code bill_cancel_reasons} (P2.20,
 * DAD-16). Substitui o texto livre de {@code bills.cancel_reason}.
 * {@code coveredDebitTypeCodes} declara os tipos de debito que o motivo
 * abrange: no cancelamento, debito de tipo abrangido NAO volta a ser
 * cobrado; os demais voltam (ver {@code CancellationSettlement}). O
 * catalogo comeca vazio -- politica do prestador, nada semeado. O JSON e
 * gravado/lido pela aplicacao via classe {@code Json} do core.
 *
 * <p>Nao usar {@code @Builder}.
 */
@Entity
@Table(name = "bill_cancel_reasons")
@Getter
@Setter
public class BillCancelReasonEntity extends TenantAwareEntity {

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "description", nullable = false, length = 200)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "covered_debit_type_codes", columnDefinition = "jsonb", nullable = false)
    private String coveredDebitTypeCodes = "[]";

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
