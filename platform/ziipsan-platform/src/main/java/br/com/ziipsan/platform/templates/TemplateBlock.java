package br.com.ziipsan.platform.templates;

/**
 * Um bloco de conteúdo de uma versão de modelo (P2.30). O corpo do modelo é
 * a concatenação dos textos dos blocos, na ordem; a renderização substitui
 * as variáveis {@code {{nome}}} do texto concatenado.
 *
 * <p>Blocos travados — o mecanismo do aceite, sem o conteúdo da P2.32:
 * <ul>
 *   <li>{@code kind = LOCKED}: o bloco não pode ser alterado nem removido
 *       por uma versão nova do mesmo modelo (o serviço confere contra a
 *       versão anterior e recusa); blocos FREE podem mudar livremente.</li>
 *   <li>{@code origin = SYSTEM}: reservado ao conteúdo legal obrigatório da
 *       P2.32 futura. Esta frente NÃO cria blocos SYSTEM (criar um seria
 *       fingir conteúdo legal) e o serviço recusa a criação; quando a P2.32
 *       existir, ela insere os blocos SYSTEM e este mecanismo já os trava.</li>
 * </ul>
 *
 * <p>Um bloco LOCKED de origem TENANT (criado pelo próprio tenant) trava
 * contra o próprio tenant nas versões seguintes: é o mesmo mecanismo, sem
 * esperar a P2.32 para ser testável.
 */
public record TemplateBlock(String id, Kind kind, Origin origin, String text) {

    public enum Kind {
        FREE,
        LOCKED
    }

    public enum Origin {
        TENANT,
        SYSTEM
    }

    public TemplateBlock {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Bloco sem id.");
        }
        if (kind == null || origin == null || text == null) {
            throw new IllegalArgumentException("Bloco incompleto: kind, origin e text são obrigatórios.");
        }
    }

    /** Verdadeiro quando o bloco não pode mudar nas versões seguintes. */
    public boolean locked() {
        return kind == Kind.LOCKED;
    }
}
