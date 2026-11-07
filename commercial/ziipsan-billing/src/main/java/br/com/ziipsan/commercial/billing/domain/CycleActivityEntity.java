package br.com.ziipsan.commercial.billing.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Atividade do catalogo de produto do ciclo (V4_3, P2.5/F1). A tabela nasce vazia na
 * migration, como os demais catalogos do modulo: as linhas do produto (leitura,
 * transmissao, faturamento, fechamento) sao carregadas pelo servico de ciclo a partir
 * de {@code ProductActivityCatalog}, nunca por INSERT de migration.
 *
 * <p>{@code sequenceOrder} e a ordem da atividade no ciclo; {@code mandatory} diz que
 * o grupo nao se encerra (nem a referencia fecha) sem ela; {@code repeatable} diz que
 * uma atividade realizada pode ser comandada de novo como nova ocorrencia;
 * {@code predecessorId} e a atividade que precisa estar realizada antes do comando
 * desta (nulo so na primeira da cadeia).
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "cycle_activities")
@Getter
@Setter
public class CycleActivityEntity extends TenantAwareEntity {

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "sequence_order", nullable = false)
    private short sequenceOrder;

    @Column(name = "mandatory", nullable = false)
    private boolean mandatory;

    @Column(name = "repeatable", nullable = false)
    private boolean repeatable;

    @Column(name = "predecessor_id")
    private UUID predecessorId;
}
