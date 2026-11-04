package br.com.ziipsan.commercial.registry.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Fusão de cadastros duplicados (P1.41, V3_8).
 *
 * <p>Fluxo: pedido com prévia (o que será religado) → OS CUSTOMER_MERGE com
 * aprovação por outra pessoa → na aprovação, vínculos, contatos, endereços,
 * representações, validações e consentimentos da origem são religados ao
 * destino, e a origem é excluída logicamente. O desfazimento é por OS
 * inversa (CUSTOMER_MERGE com {@code reverses_order_id} apontando para a OS
 * da fusão), que devolve o estado anterior a partir do snapshot.
 *
 * <p>Prévia e snapshot levam SÓ identificadores, contagens, códigos e datas
 * — nunca nomes nem documentos, nem mascarados — para nada pessoal vazar no
 * audit_log através do JSONB. A tela resolve a exibição pelos endpoints de
 * leitura existentes.
 *
 * <p>Limites honestos desta frente: protocolos (P1.36), débitos e contas
 * (P2.5) e contas do portal (P3.3) ainda não existem nesta camada, então a
 * prévia lista essas seções como vazias; o serviço as preenche quando os
 * módulos existirem, sem mudar o formato. A detecção de candidatos é
 * sugestão do operador (mesmo documento, ou mesmo nome + nascimento), nunca
 * fusão automática.
 *
 * <p>Não usar {@code @Builder}: o Lombok descarta os campos herdados de
 * {@code BaseEntity}.
 */
@Entity
@Table(name = "customer_merges")
@Getter
@Setter
public class CustomerMerge extends TenantAwareEntity {

    @Column(name = "source_customer_id", nullable = false)
    private UUID sourceCustomerId;

    @Column(name = "target_customer_id", nullable = false)
    private UUID targetCustomerId;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING_APPROVAL";

    @Column(name = "service_order_id")
    private UUID serviceOrderId;

    @Column(name = "reversed_by_service_order_id")
    private UUID reversedByServiceOrderId;

    @Column(name = "merge_end_reason_id")
    private UUID mergeEndReasonId;

    @Column(name = "preview", nullable = false, columnDefinition = "jsonb")
    private String preview = "{}";

    @Column(name = "snapshot", nullable = false, columnDefinition = "jsonb")
    private String snapshot = "{}";
}
