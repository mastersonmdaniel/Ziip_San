package br.com.ziipsan.platform.notification.multichannel;

import br.com.ziipsan.platform.notification.NotificationChannel;

import java.util.UUID;

/**
 * Registros de governança que o disparo multicanal consulta antes de enviar
 * (portas; as implementações leem as fontes donas na integração):
 *
 * <ul>
 *   <li>{@link DpaProviderRegistry} — "provedores no DPA" (aceite da ED-41):
 *       o provedor de um canal só pode enviar se constar da lista de
 *       suboperadores aprovada do tenant (DPA da P3.9). NENHUM nome de
 *       provedor vive neste código — a lista nominal de suboperadores é um
 *       dos valores abertos da P3.9 (item #18 do mapa dela) e fecha na
 *       contratação; enquanto um canal não tem provedor aprovado, o disparo
 *       por ele é recusado com motivo nomeado;</li>
 *   <li>{@link TemplateApprovalRegistry} — a aprovação registrada de um
 *       modelo cuja triagem apontou risco de classificação como marketing
 *       (aceite: "alerta na aprovação"). Sem aprovação registrada para
 *       (modelo, versão), o envio com risco não sai.</li>
 * </ul>
 */
public final class MultichannelRegistries {

    private MultichannelRegistries() {
    }

    public interface DpaProviderRegistry {
        /** Verdade se o tenant tem provedor aprovado no DPA para este canal. */
        boolean providerApproved(UUID tenantId, NotificationChannel channel);
    }

    public interface TemplateApprovalRegistry {
        /** Verdade se o risco de marketing deste modelo/versão já foi aprovado por quem aprova modelos. */
        boolean marketingRiskApproved(UUID tenantId, String templateCode, int templateVersion);
    }
}
