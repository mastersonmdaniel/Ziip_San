package br.com.ziipsan.core.sac;

import java.util.Objects;

/**
 * Plano de comunicacao da interrupcao (ED-35; NR 11, arts. 39 a 41): a
 * interrupcao programada so esta pronta para executar quando a agencia foi
 * comunicada, os usuarios afetados foram comunicados e — havendo unidade
 * essencial na area — o fornecimento de emergencia foi acionado, tudo pela
 * porta {@link OutageNotifier} (P3.5/ED-41).
 */
public final class OutageCommunication {

    private OutageCommunication() {}

    public static void communicate(PlannedOutage outage, OutageNotifier notifier) {
        Objects.requireNonNull(outage, "interrupcao obrigatoria");
        Objects.requireNonNull(notifier, "canal de comunicacao obrigatorio");
        notifier.notifyAgency(outage);
        notifier.notifyAffectedUsers(outage, outage.affectedUnitIds());
        if (outage.emergencySupplyRequired())
            notifier.requestEmergencySupply(outage, outage.essentialUnits());
    }
}
