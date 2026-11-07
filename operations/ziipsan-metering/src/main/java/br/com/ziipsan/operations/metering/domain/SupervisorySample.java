package br.com.ziipsan.operations.metering.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Uma amostra do supervisório (ED-17): um valor de uma tag de uma estação
 * (ETA, ETE, elevatória, reservatório) vindo do agente do cliente, que leu
 * do historiador. Ingestão SOMENTE LEITURA na origem: esta tabela só recebe;
 * nenhum caminho deste módulo escreve em CLP, RTU ou IHM.
 *
 * <p>Tabela particionada por mês ({@code bucketMonth}) pelo padrão da P2.3,
 * sem auditoria por linha. {@code sampleKey} é a idempotência por amostra:
 * única por (tenant, mês, chave) — o reenvio do agente quando o enlace volta
 * não duplica a amostra.
 *
 * <p>Não usar {@code @Builder}.
 */
@Entity
@Table(name = "supervisory_samples")
@Getter
@Setter
public class SupervisorySample extends TenantAwareEntity {

    @Column(name = "station_code", nullable = false, length = 40)
    private String stationCode;

    @Column(name = "tag_code", nullable = false, length = 60)
    private String tagCode;

    @Column(name = "bucket_month", nullable = false)
    private LocalDate bucketMonth;

    @Column(name = "sampled_at", nullable = false)
    private OffsetDateTime sampledAt;

    @Column(name = "numeric_value", precision = 18, scale = 6)
    private BigDecimal numericValue;

    @Column(name = "text_value", length = 200)
    private String textValue;

    @Column(name = "unit_of_measure", length = 20)
    private String unitOfMeasure;

    /** Qualidade da amostra como o historiador a declarou (texto do agente; sem catálogo inventado). */
    @Column(name = "quality", length = 30)
    private String quality;

    @Column(name = "sample_key", nullable = false, length = 120)
    private String sampleKey;

    @Column(name = "envelope_id", length = 80)
    private String envelopeId;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;
}
