package br.com.ziipsan.commercial.billing.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Registro de uma declaracao anual de quitacao emitida (P2.24,
 * Lei 12.007/2009): uma linha por (unidade, ano declarado). E a memoria
 * que garante a emissao unica por ano — o servico le os anos ja
 * registrados antes de avaliar ({@code alreadyIssuedYears}) — e, nos
 * canais de documento anexo e portal, a unica prova da emissao (no canal
 * de espaco na fatura, a prova da mensagem na conta e a da P2.32,
 * {@code bill_message_emissions}).
 *
 * <p>PENDENCIA DE MIGRATION: o plano v3.0 SS3 nao reserva versao Flyway
 * para a P2.24. A tabela {@code annual_discharge_statements} esta pronta
 * como DDL em {@code db/migration-pendente/} e so vira migration quando
 * uma versao for reservada (ver o relatorio da tarefa). Ate la esta
 * entidade nao tem tabela em banco nenhum.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados.
 */
@Entity
@Table(name = "annual_discharge_statements")
@Getter
@Setter
public class AnnualDischargeStatementEntity extends TenantAwareEntity {

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    /** O ano declarado (ano das competencias quitadas). */
    @Column(name = "reference_year", nullable = false)
    private int referenceYear;

    /** Canal da emissao: valor de {@code StatementChannel} (quit.statement_channel). */
    @Column(name = "channel", nullable = false, length = 30)
    private String channel;

    /** Como venceu: valor de {@code AnnualDischargeRule.Trigger} (MAY_CYCLE ou LATE_SETTLEMENT). */
    @Column(name = "emission_trigger", nullable = false, length = 30)
    private String emissionTrigger;

    /** A conta que carregou a declaracao (so no canal de espaco na fatura). */
    @Column(name = "issued_bill_id")
    private UUID issuedBillId;

    @Column(name = "issued_on", nullable = false)
    private LocalDate issuedOn;
}
