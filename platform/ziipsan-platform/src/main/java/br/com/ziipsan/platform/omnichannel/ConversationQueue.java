package br.com.ziipsan.platform.omnichannel;

import java.util.Comparator;
import java.util.List;

/**
 * Fila da tela de atendimento (ED-42): a ordem é a da ED-35
 * ({@code ServiceQueue}) adaptada ao que a conversa TEM — perfil
 * prioritário/acessibilidade da P1.3 ANTES de tudo, depois a conversa
 * aberta há mais tempo. A ED-35 ordena também por PRAZO (dueDate do
 * protocolo); a conversa omnichannel não carrega prazo próprio porque
 * SLA de atendimento não tem fonte ({@code [A DEFINIR]} — pendência
 * nomeada): prometer ordenação por um prazo inventado seria dado
 * fabricado. Quando o prazo existir por parâmetro do tenant, ele entra
 * como segundo critério na integração, sem mudar esta assinatura de
 * ordenação por prioridade/antiguidade.
 */
public final class ConversationQueue {

    private ConversationQueue() {
    }

    /** Conversas abertas, na ordem em que a tela deve oferecê-las ao atendente. */
    public static List<Conversation> order(List<Conversation> conversations) {
        if (conversations == null) {
            throw new IllegalArgumentException("a fila exige a lista de conversas (pode ser vazia, não nula)");
        }
        return conversations.stream()
                .filter(Conversation::isOpen)
                .sorted(Comparator.comparing(Conversation::priorityProfile).reversed()
                        .thenComparing(Conversation::openedAt)
                        .thenComparing(c -> c.id().toString()))
                .toList();
    }
}
