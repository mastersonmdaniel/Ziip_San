package br.com.ziipsan.platform.notification;

/**
 * Mensagem montada pelo motor de modelos da P2.30: o texto final e a versão do modelo
 * que o gerou. A versão é gravada na tentativa de entrega — mudar o modelo depois
 * não muda o que foi enviado (mesma regra da P2.30 para documentos emitidos).
 */
public record RenderedMessage(String subject, String body, String templateCode, int templateVersion) {

    public RenderedMessage {
        if (subject == null || subject.isBlank() || body == null || body.isBlank()
                || templateCode == null || templateCode.isBlank() || templateVersion < 1) {
            throw new IllegalArgumentException("mensagem montada exige assunto, corpo, modelo e versão >= 1");
        }
    }
}
