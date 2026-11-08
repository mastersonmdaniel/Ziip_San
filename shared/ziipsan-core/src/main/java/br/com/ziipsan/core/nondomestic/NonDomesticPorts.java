package br.com.ziipsan.core.nondomestic;

import java.util.UUID;

/**
 * Portas da ED-83 para os vizinhos — a ED-83 CONSUME por estas
 * interfaces, sem duplicar o que cada entrega ja faz:
 * <ul>
 *   <li>{@link CommercialRegistrationPort} — o cadastro comercial e o
 *       catalogo de unidades: diz se o gerador tem unidade comercial
 *       propria e, nao tendo, se a unidade entregue e a avulsa de tipo
 *       {@code CASUAL} da ED-22 (ADR-0039), pela qual o aceite manda
 *       atender o usuario sem cadastro comercial (CAD-70/OPE-13);</li>
 *   <li>{@link FieldInspectionOrderPort} — a OS de vistoria no ponto
 *       de lancamento, executada no app/motor de OS vigente (ED-20/
 *       P1.31): a ED-83 pede a OS e guarda o id; nao cria motor de OS;</li>
 *   <li>{@link UserNotificationPort} — a notificacao ao usuario por
 *       nao conformidade, pelo canal/motor vigente (ED-35/ED-41): a
 *       ED-83 entrega o caso e recebe a referencia do envio (prova);
 *       nenhum texto de notificacao e redigido aqui [SEM FONTE].</li>
 * </ul>
 * O laboratorio e a cadeia de custodia sao os da ED-50, consumidos
 * pela porta REAL da ED-105 ({@code EffluentPorts.EffluentLabPort}),
 * como a ED-105 ja faz — nenhuma porta de laboratorio nova.
 */
public final class NonDomesticPorts {

    private NonDomesticPorts() {}

    /** Cadastro comercial / catalogo de unidades (P1.3/registry e ED-22). */
    public interface CommercialRegistrationPort {
        /** O gerador tem unidade consumidora com cadastro comercial proprio? */
        boolean hasCommercialRegistration(UUID tenantId, UUID consumerUnitId);
        /** A unidade e a avulsa de tipo proprio CASUAL da ED-22? */
        boolean isCasualUnitOfEd22(UUID tenantId, UUID consumerUnitId);
    }

    /** OS de vistoria no ponto de lancamento (motor de OS vigente): devolve o id da OS criada. */
    public interface FieldInspectionOrderPort {
        UUID openInspectionOrder(UUID tenantId, UUID dischargerId, String dischargePointCode, String reason);
    }

    /** Notificacao ao usuario pelo canal vigente: devolve a referencia do envio (prova). */
    public interface UserNotificationPort {
        String notifyUser(UUID tenantId, UUID dischargerId, UUID nonConformityCaseId, String reason);
    }
}
