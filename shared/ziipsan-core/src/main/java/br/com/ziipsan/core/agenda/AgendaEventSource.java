package br.com.ziipsan.core.agenda;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Fonte de eventos de agenda: a porta que o modulo dono de um tipo registrado
 * implementa (P3.39). SOMENTE LEITURA por contrato — nao ha metodo de escrita
 * nesta porta, e a agenda nao oferece nenhum: criar/alterar evento e na tela
 * e no servico do modulo dono, nunca na agenda.
 *
 * <p>Contrato da janela: devolver os eventos do tenant cujo inicio cai em
 * {@code [from, to)}; a agenda ainda refiltra e ordena (defesa em
 * profundidade: fonte que devolve fora da janela nao polui a exibicao).
 * Fonte ausente para um tipo registrado NAO e erro: a agenda exibe os tipos
 * que tem fonte e segue (aceite da P3.39).
 */
public interface AgendaEventSource {

    /** Codigo do tipo registrado que esta fonte alimenta ({@link AgendaEventType#code()}). */
    String typeCode();

    /** Eventos do tenant com inicio em {@code [from, to)}. Nunca nulo; vazio e valido. */
    List<AgendaEvent> eventsInWindow(UUID tenantId, Instant from, Instant to);
}
