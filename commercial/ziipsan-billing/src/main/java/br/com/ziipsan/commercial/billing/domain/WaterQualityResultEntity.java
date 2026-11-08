package br.com.ziipsan.commercial.billing.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * Um resultado mensal de qualidade da agua (P2.24, Decreto 5.440/2005),
 * tal como reportado pela fonte vigente ({@code bill.water_quality_source}):
 * a planilha mensal do laboratorio aplicada pelo importador generico da
 * P1.40 (esquema {@code billing.water_quality_lab}, no modulo registry,
 * mesmo desenho da ED-57) ou o modulo de qualidade da ED-50. Todos os
 * valores sao texto do laboratorio/modulo — nenhum parametro, limite ou
 * resultado e presumido pelo produto.
 *
 * <p>PENDENCIA DE MIGRATION: o plano v3.0 SS3 nao reserva versao Flyway
 * para a P2.24. A tabela {@code water_quality_results} esta pronta como
 * DDL em {@code db/migration-pendente/} e so vira migration quando uma
 * versao for reservada (ver o relatorio da tarefa). Ate la esta entidade
 * nao tem tabela em banco nenhum e o aplicador do esquema de importacao
 * nao tem onde gravar.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados.
 */
@Entity
@Table(name = "water_quality_results")
@Getter
@Setter
public class WaterQualityResultEntity extends TenantAwareEntity {

    @Column(name = "reference_month", nullable = false)
    private LocalDate referenceMonth;

    /** Sistema de abastecimento a que o resultado se refere (chave textual da fonte). */
    @Column(name = "supply_system", nullable = false, length = 120)
    private String supplySystem;

    @Column(name = "parameter_key", nullable = false, length = 80)
    private String parameterKey;

    @Column(name = "parameter_name", nullable = false, length = 160)
    private String parameterName;

    @Column(name = "unit", length = 30)
    private String unit;

    /** O resultado como reportado (valor ou resultado qualitativo), nunca convertido. */
    @Column(name = "result_text", nullable = false, length = 120)
    private String resultText;

    /** A referencia/limite como reportado, quando a fonte o traz. */
    @Column(name = "reference_text", length = 240)
    private String referenceText;

    /** Fonte do resultado: LAB_SPREADSHEET ou QUALITY_MODULE (bill.water_quality_source). */
    @Column(name = "source", nullable = false, length = 30)
    private String source;
}
