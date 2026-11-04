package br.com.ziipsan.commercial.registry.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Separação de cadastros (P1.41, V3_8): o inverso operacional da fusão para
 * o caso em que um registro na verdade representa duas pessoas.
 *
 * <p>O cliente "novo" já precisa existir (criado pela API normal de
 * clientes, com documento cifrado e checagens próprias): a separação é uma
 * operação de religação de vínculos, não de criação de cliente. O pedido
 * lista os vínculos vigentes da origem a mover; na aprovação por outra
 * pessoa (OS CUSTOMER_SPLIT), eles passam ao novo cliente. Conflito (o novo
 * cliente já tem vínculo vigente com o mesmo papel na mesma unidade)
 * recusa a separação inteira — a prévia mostra o conflito antes. O
 * desfazimento é por OS inversa, que move os vínculos de volta.
 *
 * <p>Prévia e snapshot: só identificadores e datas, como em
 * {@link CustomerMerge}.
 *
 * <p>Não usar {@code @Builder}: o Lombok descarta os campos herdados de
 * {@code BaseEntity}.
 */
@Entity
@Table(name = "customer_splits")
@Getter
@Setter
public class CustomerSplit extends TenantAwareEntity {

    @Column(name = "source_customer_id", nullable = false)
    private UUID sourceCustomerId;

    @Column(name = "new_customer_id", nullable = false)
    private UUID newCustomerId;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING_APPROVAL";

    @Column(name = "service_order_id")
    private UUID serviceOrderId;

    @Column(name = "reversed_by_service_order_id")
    private UUID reversedByServiceOrderId;

    @Column(name = "preview", nullable = false, columnDefinition = "jsonb")
    private String preview = "{}";

    @Column(name = "snapshot", nullable = false, columnDefinition = "jsonb")
    private String snapshot = "{}";
}
