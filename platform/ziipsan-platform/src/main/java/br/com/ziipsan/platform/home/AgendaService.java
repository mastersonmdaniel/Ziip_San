package br.com.ziipsan.platform.home;

import br.com.ziipsan.core.agenda.AgendaEvent;
import br.com.ziipsan.core.agenda.AgendaEventSource;
import br.com.ziipsan.core.agenda.AgendaEventType;
import br.com.ziipsan.core.agenda.AgendaEventTypeRegistry;
import br.com.ziipsan.core.api.ApiException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * A agenda (P3.39): leitura agregada das fontes dos tipos REGISTRADOS.
 *
 * <p>SO LEITURA, por desenho e por teste: esta classe nao tem metodo de
 * criacao, alteracao ou remocao — nem poderia ter, porque ela nao e dona de
 * nenhum evento; quem cria/altera e o modulo dono, na tela dele. O teste
 * {@code AgendaReadOnlyTest} confere por reflexao que nenhum metodo publico
 * de escrita existe aqui nem no controller da agenda.
 *
 * <p>Regras do aceite:
 * <ul>
 *   <li>so exibe tipos registrados no {@link AgendaEventTypeRegistry}:
 *       fonte de tipo nao registrado e ignorada (a fonte posterior so entra
 *       quando a tarefa dela registrar o tipo);</li>
 *   <li>fonte AUSENTE para um tipo registrado nao e erro: o tipo aparece na
 *       lista de tipos com {@code sourceAvailable = false} e a janela
 *       simplesmente nao traz eventos dele;</li>
 *   <li>evento devolvido pela fonte fora da janela pedida ou de OUTRO tenant
 *       e descartado na agregacao (a fonte errou; a agenda nao exibe);</li>
 *   <li>ordenacao deterministica: inicio, tipo, id.</li>
 * </ul>
 */
public final class AgendaService {

    private final AgendaEventTypeRegistry registry;
    private final List<AgendaEventSource> sources;

    public AgendaService(AgendaEventTypeRegistry registry, List<AgendaEventSource> sources) {
        if (registry == null) {
            throw new IllegalArgumentException("registro de tipos de agenda e obrigatorio");
        }
        this.registry = registry;
        this.sources = sources == null ? List.of() : List.copyOf(sources);
    }

    /** Um tipo registrado e se alguma fonte o alimenta nesta instalacao. */
    public record TypeAvailability(AgendaEventType type, boolean sourceAvailable) {
    }

    /** Tipos registrados, com a disponibilidade honesta de cada fonte. */
    public List<TypeAvailability> types() {
        return registry.registeredTypes().stream()
                .map(t -> new TypeAvailability(t, sources.stream()
                        .anyMatch(s -> t.code().equals(s.typeCode()))))
                .toList();
    }

    /**
     * Eventos do tenant em {@code [from, to)}, so de tipos registrados, na
     * ordem deterministica. Janela invalida (fim nao posterior ao inicio)
     * e recusa 422 nomeada — janela vazia por engano nao vira "sem eventos"
     * silencioso.
     */
    public List<AgendaEvent> eventsInWindow(UUID tenantId, Instant from, Instant to) {
        if (tenantId == null || from == null || to == null) {
            throw new IllegalArgumentException("tenant e janela (inicio e fim) sao obrigatorios");
        }
        if (!to.isAfter(from)) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "to", "invalid-window", "A janela da agenda exige fim posterior ao inicio."));
        }
        return sources.stream()
                .filter(s -> registry.isRegistered(s.typeCode()))
                .flatMap(s -> s.eventsInWindow(tenantId, from, to).stream())
                .filter(e -> tenantId.equals(e.tenantId()))
                .filter(e -> registry.isRegistered(e.typeCode()))
                .filter(e -> !e.startsAt().isBefore(from) && e.startsAt().isBefore(to))
                .sorted(Comparator.comparing(AgendaEvent::startsAt)
                        .thenComparing(AgendaEvent::typeCode)
                        .thenComparing(e -> e.id().toString()))
                .toList();
    }
}
