package br.com.ziipsan.commercial.collection.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
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

    /**
     * Chave de deduplicacao da remessa (B2-4), persistida pela V103 no
     * desenho expand-contract (decisao do dono, item 46): a coluna
     * fisica nasce anulavel no tempo expand da V103 e a obrigatoriedade
     * fisica fica diferida para a V104 pendente, que pousa com o
     * adaptador JPA; ao nivel da aplicacao o contrato continua
     * obrigatorio desde ja - esta anotacao mantem {@code nullable = false},
     * a chave e exigida no construtor de aplicacao e nao tem setter
     * livre. A chave recebida e a derivada por
     * {@code LegacyDebitService.remittanceKey(...)}; a formula nao se
     * move nem se duplica aqui.
     */
    @Setter(AccessLevel.NONE)
    @Column(name = "remittance_key", nullable = false, length = 104)
    private String remittanceKey;

    /** REMETIDO, BAIXADO, CANCELADO. */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "REMETIDO";

    @Column(name = "remittance_count", nullable = false)
    private int remittanceCount = 1;

    @Column(name = "outcome_code", length = 40)
    private String outcomeCode;

    @Column(name = "settled_at")
    private Instant settledAt;

    /** So para reconstituicao JPA/leitura; a aplicacao usa o construtor com a chave. */
    protected LegacyDebitEntryEntity() {
    }

    /**
     * Construcao usada pela aplicacao: exige todos os campos
     * obrigatorios da entidade, incluindo a chave de remessa ja
     * derivada pelo servico. O {@code tenantId} nao entra aqui: vive
     * na superclasse {@code TenantAwareEntity} e e atribuido pelo
     * mecanismo dela (acessor herdado), sem quebra. Os inicializadores
     * de {@code status} ("REMETIDO") e {@code remittanceCount} (1)
     * mantem-se: este construtor nao os toca, logo a entidade nasce no
     * mesmo estado inicial de sempre.
     */
    public LegacyDebitEntryEntity(UUID authorizationId, String enrollmentId, BigDecimal amount,
                                  LocalDate debitDate, String remittanceKey) {
        this.authorizationId = Objects.requireNonNull(authorizationId, "autorizacao e obrigatoria");
        this.enrollmentId = Objects.requireNonNull(enrollmentId, "matricula e obrigatoria");
        this.amount = Objects.requireNonNull(amount, "valor e obrigatorio");
        this.debitDate = Objects.requireNonNull(debitDate, "data do debito e obrigatoria");
        this.remittanceKey = Objects.requireNonNull(remittanceKey, "chave de remessa e obrigatoria");
    }
}
