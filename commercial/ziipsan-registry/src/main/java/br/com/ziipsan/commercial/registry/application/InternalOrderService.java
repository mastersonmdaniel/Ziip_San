package br.com.ziipsan.commercial.registry.application;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.serviceorder.ServiceOrderEnums;
import br.com.ziipsan.core.serviceorder.ServiceOrderService;
import br.com.ziipsan.core.serviceorder.TargetType;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

/**
 * OS interna do cadastro (P1.3, sobre o motor da P1.31).
 *
 * <p>Fluxo por escrita (tela, API ou CSV): a escrita abre UMA OS interna do
 * tipo do campo alterado (ver {@code CadastralChangeService}), grava o
 * histórico e os pedidos com o id dessa OS e, ao fim da escrita, submete,
 * executa e encerra a OS como executada. Sem alvos com efeito: a OS não
 * carrega {@code service_order_targets} — o efeito é a própria escrita, já
 * aplicada na transação do chamador, e o aplicador de efeitos da P1.31 (que
 * aplicaria o efeito SÓ no encerramento, com idempotência) ainda não está
 * ligado ao cadastro. Consequência honesta: hoje a OS nasce e encerra na
 * mesma chamada, e um rollback da escrita depois da abertura deixa uma OS
 * DRAFT órfã (visível, sem efeito falso de "executada"). A ligação
 * efeito-no-encerramento é trabalho de integração com a P1.31.
 *
 * <p>Tipo ausente no tenant: o tipo é declarado na hora (INTERNAL, sem
 * aprovação, effectCode = o próprio código, alvos CONSUMER_UNIT). O catálogo
 * de tipos é política do tenant, mas o motor exige a linha para abrir a
 * ordem; o tipo autodeclarado é editável pelo tenant depois (nome, aprovação
 * e prazo), e a declaração é idempotente por código.
 */
@Service
public class InternalOrderService {

    private final DataSource dataSource;

    public InternalOrderService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Abre uma OS interna em DRAFT e devolve o id. */
    public UUID open(String serviceTypeCode) {
        ServiceOrderService engine = engine();
        ensureType(engine, serviceTypeCode);
        UUID actor = actor();
        return engine.createOrder(new ServiceOrderService.OrderInput(serviceTypeCode,
                ServiceOrderEnums.Origin.INTERNAL, ServiceOrderEnums.ExecutionMode.INTERNAL,
                ServiceOrderEnums.Priority.NORMAL, actor, null, null, null, null)).id();
    }

    /** Submete, executa e encerra como executada uma OS aberta por {@link #open}. */
    public void executeAndClose(UUID orderId) {
        ServiceOrderService engine = engine();
        UUID actor = actor();
        engine.submit(orderId, actor);
        engine.execute(orderId, actor);
        engine.closeExecuted(orderId, actor);
    }

    private ServiceOrderService engine() {
        String schema = TenantContextHolder.getTenant();
        UUID tenantId = RegistryContext.tenantId();
        if (schema == null || schema.isBlank()) {
            throw new IllegalStateException("sem schema de tenant no contexto da requisição");
        }
        return new ServiceOrderService(dataSource, schema, tenantId);
    }

    private static UUID actor() {
        UUID user = TenantContextHolder.getUserId();
        if (user == null) {
            throw new IllegalStateException("sem usuário no contexto da requisição para abrir a OS interna");
        }
        return user;
    }

    private static void ensureType(ServiceOrderService engine, String code) {
        try {
            engine.findTypeByCode(code);
        } catch (ApiException.NotFound e) {
            engine.declareType(new ServiceOrderService.ServiceTypeInput(code, "OS interna do cadastro: " + code,
                    code, code, null, ServiceOrderEnums.TypeIndicator.INTERNAL, false, false, null,
                    List.of(TargetType.CONSUMER_UNIT), null, null, null,
                    false, false, false, false, List.of()), actor());
        }
    }
}
