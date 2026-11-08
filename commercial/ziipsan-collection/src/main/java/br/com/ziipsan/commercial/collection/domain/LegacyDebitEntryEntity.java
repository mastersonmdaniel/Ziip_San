package br.com.ziipsan.commercial.collection.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Debito legado de um ciclo (P3.24, V102): chave da baixa idempotente do
 * retorno. {@code outcomeCode} guarda o codigo do banco VERBATIM - nenhuma
 * tabela de codigos e presumida [layout/codigos SEM FONTE, ver relatorio].
 * Nao usar {@code @Builder}.
 */
@Entity
@Table(name = "legacy_debit_entries")
@Getter
@Setter
public class LegacyDebitEntryEntity extends TenantAwareEntity {

    @Column(name = "authorization_id", nullable = false)
    private UUID authorizationId;

    @Column(name = "enrollment_id", nullable = false, length = 40)
    private String enrollmentId;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "debit_date", nullable = false)
    private LocalDate debitDate;

    /** REMETIDO, BAIXADO, CANCELADO. */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "REMETIDO";

    @Column(name = "remittance_count", nullable = false)
    private int remittanceCount = 1;

    @Column(name = "outcome_code", length = 40)
    private String outcomeCode;

    @Column(name = "settled_at")
    private Instant settledAt;
}
