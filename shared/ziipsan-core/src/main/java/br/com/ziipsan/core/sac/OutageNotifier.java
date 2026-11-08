package br.com.ziipsan.core.sac;

import java.util.List;
import java.util.UUID;

/**
 * Porta de comunicacao da interrupcao programada (ED-35): agencia e usuarios
 * afetados sao comunicados PELO canal da P3.5 (notificacoes com prova de
 * entrega) \u2014 pela ED-41 quando ela estiver disponivel. Esta frente nao envia
 * nada: declara o contrato que a fiacao liga a P3.5/ED-41.
 */
public interface OutageNotifier {
    void notifyAgency(PlannedOutage outage);
    void notifyAffectedUsers(PlannedOutage outage, List<UUID> consumerUnitIds);
    void requestEmergencySupply(PlannedOutage outage, List<PlannedOutage.EssentialUnit> essentialUnits);
}
