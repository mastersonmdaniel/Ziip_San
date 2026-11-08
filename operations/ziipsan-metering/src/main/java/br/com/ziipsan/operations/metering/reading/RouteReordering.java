package br.com.ziipsan.operations.metering.reading;

import br.com.ziipsan.operations.metering.rules.MeteringErrorCode;
import br.com.ziipsan.operations.metering.rules.MeteringInputException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Roteiro de leitura com reordenação da sequência (ED-15).
 *
 * <p>O roteiro é a lista ordenada de paradas da rota. Reordenar é aplicar
 * uma <b>permutação exata</b> das paradas existentes: parada duplicada,
 * parada estranha ao roteiro ou parada faltante são recusadas — a
 * reordenação nunca cria, some ou repete uma leitura a fazer. A nova
 * sequência é renumerada de 1 a N, sem buracos.
 */
public final class RouteReordering {

    private RouteReordering() {
    }

    /** Uma parada do roteiro: a unidade e a posição atual (1..N). */
    public record RouteStop(UUID unitId, int sequence) {
        public RouteStop {
            if (unitId == null) {
                throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                        "parada do roteiro sem unidade");
            }
            if (sequence < 1) {
                throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                        "sequência da parada precisa ser >= 1: " + sequence);
            }
        }
    }

    /**
     * Aplica a nova ordem pedida e devolve o roteiro renumerado.
     *
     * @param current roteiro vigente (paradas com a sequência atual)
     * @param newOrder unidades na ordem desejada; precisa ser permutação
     *                 exata das unidades do roteiro
     */
    public static List<RouteStop> reorder(List<RouteStop> current, List<UUID> newOrder) {
        if (current == null || current.isEmpty()) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    "roteiro vazio não pode ser reordenado");
        }
        if (newOrder == null) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    "nova ordem do roteiro é obrigatória");
        }
        Set<UUID> currentUnits = new HashSet<>();
        for (RouteStop stop : current) {
            if (!currentUnits.add(stop.unitId())) {
                throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                        "roteiro já tem a unidade duplicada: " + stop.unitId());
            }
        }
        if (newOrder.size() != current.size()) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    "a nova ordem precisa ter exatamente as " + current.size()
                            + " paradas do roteiro; recebidas " + newOrder.size());
        }
        Set<UUID> seen = new HashSet<>();
        for (UUID unitId : newOrder) {
            if (unitId == null || !seen.add(unitId)) {
                throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                        "a nova ordem repete ou omite unidade: " + unitId);
            }
            if (!currentUnits.contains(unitId)) {
                throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                        "a nova ordem traz unidade fora do roteiro: " + unitId);
            }
        }
        List<RouteStop> result = new ArrayList<>();
        for (int i = 0; i < newOrder.size(); i++) {
            result.add(new RouteStop(newOrder.get(i), i + 1));
        }
        return List.copyOf(result);
    }

    /**
     * Move uma parada para a nova posição (1..N), deslocando as demais.
     * Atalho sobre {@link #reorder}: a ordem resultante continua sendo a
     * permutação exata do roteiro.
     */
    public static List<RouteStop> move(List<RouteStop> current, UUID unitId, int newPosition) {
        if (current == null || current.isEmpty()) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    "roteiro vazio não pode ser reordenado");
        }
        if (newPosition < 1 || newPosition > current.size()) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    "nova posição fora de 1.." + current.size() + ": " + newPosition);
        }
        List<RouteStop> ordered = new ArrayList<>(current);
        ordered.sort(java.util.Comparator.comparingInt(RouteStop::sequence));
        List<UUID> units = new ArrayList<>();
        for (RouteStop stop : ordered) {
            units.add(stop.unitId());
        }
        if (!units.remove(unitId)) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    "unidade fora do roteiro: " + unitId);
        }
        units.add(newPosition - 1, unitId);
        return reorder(current, units);
    }
}
