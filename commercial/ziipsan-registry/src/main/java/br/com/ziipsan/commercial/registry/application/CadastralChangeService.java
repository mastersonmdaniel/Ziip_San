package br.com.ziipsan.commercial.registry.application;

import br.com.ziipsan.commercial.registry.domain.CadastralChangeRequest;
import br.com.ziipsan.commercial.registry.repository.CadastralChangeRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Alteração campo a campo do cadastro ligada à OS (P1.3, V3_12).
 *
 * <p>O mapa campo → tipo de OS é o contrato da P1.31 (mapa do 23b §3.2, linha
 * P1.31 do plano): contatos por CUSTOMER_CONTACT_UPDATE; nome, documento e
 * nome social por CUSTOMER_DATA_UPDATE; vínculo por OWNERSHIP_TRANSFER;
 * economias por ECONOMIES_CATEGORY_CHANGE; perfil e área por
 * UNIT_PROFILE_CHANGE e UNIT_PROFILE_UPDATE; vencimento por
 * ALTERNATIVE_DUE_DAY; endereço de entrega por DELIVERY_ADDRESS_CHANGE;
 * situação de água e de esgoto por OS de ligação, corte, religação,
 * supressão e tamponamento (o tipo exato depende da transição e é definido
 * pela P1.31 — até lá, a mudança de situação usa UNIT_PROFILE_UPDATE como
 * tipo de envelope da OS interna, e o histórico carrega o service_order_id);
 * inscrição por OS de alteração de inscrição (idem: UNIT_PROFILE_UPDATE até
 * a P1.31 tipificar a transição). Nenhum código novo é inventado aqui: os
 * tipos sem código próprio no mapa caem no envelope documentado acima.
 *
 * <p>Esta classe REGISTRA o pedido; a OS em si é aberta por
 * {@link InternalOrderService} e o efeito é aplicado pela escrita do serviço
 * chamador na mesma requisição. A aplicação do efeito SÓ no encerramento
 * (com o aplicador idempotente da P1.31) é trabalho de integração com o
 * motor da P1.31: hoje a escrita é imediata e a OS nasce e encerra na mesma
 * chamada, deixando o pedido ligado e auditável. Não afirmar o contrário.
 */
@Service
@Transactional(readOnly = true)
public class CadastralChangeService {

    /** Campo do cadastro → tipo de OS da P1.31. Chave: "ALVO.campo". */
    public static final Map<String, String> FIELD_OS_TYPES = Map.ofEntries(
            Map.entry("CUSTOMER.personType", "CUSTOMER_DATA_UPDATE"),
            Map.entry("CUSTOMER.name", "CUSTOMER_DATA_UPDATE"),
            Map.entry("CUSTOMER.tradeName", "CUSTOMER_DATA_UPDATE"),
            Map.entry("CUSTOMER.document", "CUSTOMER_DATA_UPDATE"),
            Map.entry("CUSTOMER.socialName", "CUSTOMER_DATA_UPDATE"),
            Map.entry("CUSTOMER.customerTypeId", "CUSTOMER_DATA_UPDATE"),
            Map.entry("CUSTOMER.parentCustomerId", "CUSTOMER_DATA_UPDATE"),
            Map.entry("CUSTOMER.governmentSphere", "CUSTOMER_DATA_UPDATE"),
            Map.entry("CUSTOMER.documentExemptionReasonId", "CUSTOMER_DATA_UPDATE"),
            Map.entry("CUSTOMER.deleteReasonId", "CUSTOMER_DATA_UPDATE"),
            Map.entry("CUSTOMER.birthDate", "CUSTOMER_DATA_UPDATE"),
            Map.entry("CUSTOMER.emails", "CUSTOMER_CONTACT_UPDATE"),
            Map.entry("CUSTOMER.phones", "CUSTOMER_CONTACT_UPDATE"),
            Map.entry("CUSTOMER.addresses", "DELIVERY_ADDRESS_CHANGE"),
            Map.entry("CONSUMER_UNIT.links", "OWNERSHIP_TRANSFER"),
            Map.entry("CONSUMER_UNIT.subcategories", "ECONOMIES_CATEGORY_CHANGE"),
            Map.entry("CONSUMER_UNIT.profileId", "UNIT_PROFILE_CHANGE"),
            Map.entry("CONSUMER_UNIT.builtAreaM2", "UNIT_PROFILE_UPDATE"),
            Map.entry("CONSUMER_UNIT.physical", "UNIT_PROFILE_UPDATE"),
            Map.entry("CONSUMER_UNIT.waterStatusId", "UNIT_PROFILE_UPDATE"),
            Map.entry("CONSUMER_UNIT.sewerStatusId", "UNIT_PROFILE_UPDATE"),
            Map.entry("CONSUMER_UNIT.sewerDischargeId", "UNIT_PROFILE_UPDATE"),
            Map.entry("CONSUMER_UNIT.blockId", "UNIT_PROFILE_UPDATE"),
            Map.entry("CONSUMER_UNIT.blockFaceId", "UNIT_PROFILE_UPDATE"),
            Map.entry("CONSUMER_UNIT.lot", "UNIT_PROFILE_UPDATE"),
            Map.entry("CONSUMER_UNIT.sublot", "UNIT_PROFILE_UPDATE"),
            Map.entry("CONSUMER_UNIT.lotFrontageM", "UNIT_PROFILE_UPDATE"),
            Map.entry("CONSUMER_UNIT.readingRouteId", "UNIT_PROFILE_UPDATE"),
            Map.entry("CONSUMER_UNIT.readingSequence", "UNIT_PROFILE_UPDATE"),
            Map.entry("CONSUMER_UNIT.status", "UNIT_PROFILE_UPDATE"),
            Map.entry("CONSUMER_UNIT.parentUnitId", "UNIT_PROFILE_UPDATE"),
            Map.entry("CONSUMER_UNIT.externalIds", "UNIT_PROFILE_UPDATE"),
            Map.entry("CONSUMER_UNIT.parentRelationType", "UNIT_PROFILE_UPDATE"),
            Map.entry("CONSUMER_UNIT.iptuMunicipalityIbge", "UNIT_PROFILE_UPDATE"),
            Map.entry("CONSUMER_UNIT.cnaeCode", "UNIT_PROFILE_UPDATE"),
            Map.entry("CONSUMER_UNIT.seasonal", "UNIT_PROFILE_UPDATE"),
            Map.entry("CONSUMER_UNIT.dueDay", "ALTERNATIVE_DUE_DAY"),
            Map.entry("CONSUMER_UNIT.deliveryAddress", "DELIVERY_ADDRESS_CHANGE"),
            Map.entry("CONSUMER_UNIT.address", "DELIVERY_ADDRESS_CHANGE"));

    private final CadastralChangeRequestRepository changes;

    public CadastralChangeService(CadastralChangeRequestRepository changes) {
        this.changes = changes;
    }

    /** Tipo de OS de um campo, ou nulo se o campo não está no mapa. */
    public static String osTypeFor(String targetType, String field) {
        return FIELD_OS_TYPES.get(targetType + "." + field);
    }

    /**
     * Registra um pedido de alteração. Valores de documento entram mascarados
     * (o chamador é responsável por mascarar antes de chamar).
     */
    @Transactional
    public CadastralChangeRequest record(String targetType, UUID targetId, String field,
                                         String oldValue, String newValue, UUID serviceOrderId) {
        String type = osTypeFor(targetType, field);
        if (type == null) {
            throw new IllegalArgumentException("Campo fora do mapa campo → OS: " + targetType + "." + field);
        }
        CadastralChangeRequest row = RegistryContext.forCreate(new CadastralChangeRequest());
        row.setTargetType(targetType);
        row.setTargetId(targetId);
        row.setField(field);
        row.setOldValue(oldValue);
        row.setNewValue(newValue);
        row.setServiceOrderType(type);
        row.setServiceOrderId(serviceOrderId);
        row.setStatus("PENDING");
        return changes.saveAndFlush(row);
    }

    /** Pedidos de um alvo, em ordem de criação. */
    public List<CadastralChangeRequest> forTarget(String targetType, UUID targetId) {
        return changes.findByTenantIdAndTargetTypeAndTargetIdOrderByCreatedAtAsc(
                RegistryContext.tenantId(), targetType, targetId);
    }
}
