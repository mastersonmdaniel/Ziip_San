package br.com.ziipsan.commercial.collection.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Callback Pix gravado na caixa de entrada (P3.2, V6_3). A gravacao na
 * {@code pix_inbox} e o ato que autoriza a resposta 200 ao PSP: primeiro grava,
 * depois confirma e baixa. A idempotencia e por {@code (tenant, psp, endToEndId)}.
 *
 * <p>A tabela e particionada por mes de {@code receivedAt}; a entidade nao
 * declara a particao (isso e do banco). {@code payload} e o JSON bruto do
 * callback, produzido/lido somente pela classe {@code Json} do core. A
 * unicidade real de {@code (tenant, psp, endToEndId)} entre particoes e da
 * tabela NAO particionada {@code pix_inbox_dedup} (V6_3, correcao M1
 * pos-revisao), gravada pelo adaptador na mesma transacao - nao desta
 * entidade.
 *
 * <p>{@code routeSegment} so existe para {@code source = CALLBACK}: linhas
 * de varredura ({@code SWEEP}) e de consulta gravam {@code null}, por
 * modelagem explicita da V6_3 (correcao M3 pos-revisao) - o adaptador JPA
 * persiste o {@code null} que a porta da varredura entrega, sem inventar
 * segmento. {@code decision} nasce {@code RECEBIDA} (default da coluna e
 * desta entidade) ate o {@code updateDecision} da regra gravar a decisao.
 *
 * <p>PK da tabela particionada e composta {@code (tenant_id, id,
 * received_at)} no banco; o mapeamento JPA de id simples desta entidade e
 * pendencia nomeada da integracao (conferir na fiacao; se o Hibernate
 * reclamar, a inbox vira acesso via porta/JDBC sem mudar a regra).
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados.
 */
@Entity
@Table(name = "pix_inbox")
@Getter
@Setter
public class PixInboxEntity extends TenantAwareEntity {

    @Column(name = "psp", nullable = false, length = 40)
    private String psp;

    @Column(name = "end_to_end_id", nullable = false, length = 32)
    private String endToEndId;

    @Column(name = "txid", length = 35)
    private String txid;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;

    @Column(name = "payer_document", length = 14)
    private String payerDocument;

    /** CALLBACK, SWEEP (varredura GET /pix) ou CONSULTA (GET /pix/{e2eid}). */
    @Column(name = "source", nullable = false, length = 12)
    private String source;

    /** Preenchido somente em CALLBACK; SWEEP/CONSULTA gravam null (V6_3). */
    @Column(name = "route_segment", length = 64)
    private String routeSegment;

    @Column(name = "mtls_verified", nullable = false)
    private boolean mtlsVerified;

    @Column(name = "secret_verified")
    private Boolean secretVerified;

    @Column(name = "confirmation", nullable = false, length = 24)
    private String confirmation = "NAO_CONFIRMADA";

    /** Nasce RECEBIDA (default da V6_3); a regra sobrescreve via updateDecision. */
    @Column(name = "decision", nullable = false, length = 28)
    private String decision = "RECEBIDA";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
}
