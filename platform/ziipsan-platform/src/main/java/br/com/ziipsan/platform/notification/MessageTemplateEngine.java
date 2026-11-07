package br.com.ziipsan.platform.notification;

import java.util.Map;
import java.util.UUID;

/**
 * Porta para o motor único de modelos de documento e mensagem da P2.30 (JOR-06):
 * a P3.5 NÃO cria leiaute próprio — pede a mensagem montada a esta porta, pelo
 * código do modelo vigente do tenant para o canal. Modelo inexistente, sem versão
 * vigente ou variável obrigatória ausente é erro explícito da implementação da
 * porta (a P2.30 recusa modelo com variável inexistente ou de tipo incompatível);
 * esta porta nunca devolve texto presumido.
 *
 * <p>As variáveis são códigos e identificadores (a carga da P1.38 não tem dado
 * pessoal); nome e endereço do destinatário NÃO são variáveis desta chamada —
 * quem os conhece é o cadastro da P1.3, consultado pela porta {@link ContactRegistry}.
 */
public interface MessageTemplateEngine {

    RenderedMessage render(UUID tenantId, String templateCode, NotificationChannel channel,
                           Map<String, String> variables);
}
