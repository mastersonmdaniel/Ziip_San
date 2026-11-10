package br.com.ziipsan.intelligence.gis.clickos;

import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.FunctionalConnection;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.Target;

import java.util.List;
import java.util.UUID;

/**
 * Modelo da II-1 (OS por clique no elemento GIS): dado o elemento
 * clicado no mapa, propagar na rede da GIS-03 (retrato
 * {@code ManeuverModel.NetworkSnapshot}), resolver as ligações/UCs
 * afectadas e montar o payload da OS sobre o núcleo existente.
 *
 * <p>Este pacote é ADITIVO sobre a GIS-11 (ED-54): o traçado
 * dirigido reutiliza {@code ManeuverGraph} e o isolamento reutiliza
 * {@code IsolationEngine} — nenhum dos dois é duplicado aqui. O que
 * a II-1 acrescenta é a SEMÂNTICA DO CLIQUE: modos de propagação por
 * tipo de intervenção, limites configuráveis com truncagem
 * DECLARADA (nunca silenciosa), desfecho tipificado e o payload de
 * OS por porta (ver {@link ClickOsPorts}).
 *
 * <p>Nenhum número de negócio nasce aqui: raio/limite de UCs/prazo
 * são parâmetros do tenant. As omissões de
 * {@link ClickPropagationConfig#proofDefaults()} são construção de
 * prova declarada — {@code [A DEFINIR]} pelo tenant em produção.
 */
public final class ClickOsModel {

    private ClickOsModel() {
    }

    /** Modo de propagação pedido pelo tipo de intervenção do clique. */
    public enum PropagationMode {
        /** Quem está a jusante do elemento (perda de abastecimento). */
        DOWNSTREAM,
        /** Quem alimenta o elemento (origem/rastreio a montante). */
        UPSTREAM,
        /** Jusante + montante (investigação). */
        BOTH,
        /** Área isolável por fecho de válvulas — delega na IsolationEngine da GIS-11. */
        ISOLATION
    }

    /**
     * Desfecho tipificado da propagação — o clique nunca falha em
     * silêncio: elemento sem UCs e elemento isolado são resultados
     * nomeados, não listas vazias ambíguas.
     */
    public enum ClickOutcome {
        /** Propagação encontrou UCs afectadas. */
        WITH_AFFECTED_UNITS,
        /** Propagação expandiu mas nenhum elemento afectado tem ligação/UC. */
        WITHOUT_AFFECTED_UNITS,
        /** O elemento não alcança nenhum outro (sem arestas dirigidas de saída/entrada). */
        ISOLATED_ELEMENT,
        /** Modo ISOLATION: a IsolationEngine declarou o isolamento impossível (motivo em {@code isolationFailure}). */
        NOT_ISOLABLE
    }

    /**
     * Parâmetros configuráveis do mecanismo. {@code null} = sem
     * limite naquele eixo. Valores concretos são do tenant
     * {@code [A DEFINIR]}; o mecanismo só os aplica e declara a
     * truncagem quando um limite corta a propagação.
     *
     * @param maxHops          profundidade máxima da BFS dirigida (0 = só o elemento clicado)
     * @param maxAffectedUnits teto de UCs afectadas para a OS nascer pronta (acima dele, exige subdivisão)
     */
    public record ClickPropagationConfig(Integer maxHops, Integer maxAffectedUnits) {
        public ClickPropagationConfig {
            if (maxHops != null && maxHops < 0)
                throw new IllegalArgumentException("maxHops negativo");
            if (maxAffectedUnits != null && maxAffectedUnits <= 0)
                throw new IllegalArgumentException("maxAffectedUnits tem de ser positivo");
        }

        /**
         * Omissões de PROVA (declaradas): sem limite de profundidade
         * nem de UCs — a propagação é a do grafo inteiro, igual à da
         * GIS-11. NÃO são valores de negócio; em produção o tenant
         * fixa os seus {@code [A DEFINIR]}.
         */
        public static ClickPropagationConfig proofDefaults() {
            return new ClickPropagationConfig(null, null);
        }
    }

    /**
     * Resultado completo de uma propagação por clique.
     *
     * <p>Ordem determinística: nos modos dirigidos sem limite, a
     * ordem dos nós é a ordem de descoberta da BFS do
     * {@code ManeuverGraph} (jusante primeiro em {@code BOTH}); com
     * {@code maxHops}, é a BFS multi-fonte por profundidade a partir
     * das pontas do elemento clicado. Arcos afectados são os arcos
     * em operação com as DUAS pontas no conjunto de nós afectados
     * (a mesma regra da área da IsolationEngine), ordenados por
     * código. Conexões/UCs seguem a ordem do aviso da GIS-11
     * ({@code IsolationEngine.NOTICE_ORDER}: sensíveis primeiro,
     * depois essenciais, depois código), UCs sem duplicados.
     *
     * @param truncated         um limite (maxHops/maxAffectedUnits) cortou a propagação — declarado, nunca silencioso
     * @param unitLimitExceeded o corte foi pelo teto de UCs
     * @param isolationFailure  motivo da IsolationEngine quando {@code outcome == NOT_ISOLABLE}; nulo nos demais casos
     * @param valvesToCloseCodes válvulas a fechar — só no modo ISOLATION isolável; vazio nos demais modos
     */
    public record PropagationResult(
            Target clicked, PropagationMode mode,
            List<UUID> affectedNodeIds, List<UUID> affectedArcIds,
            List<FunctionalConnection> affectedConnections,
            List<UUID> affectedUnitIds, List<UUID> serviceConnectionIds,
            int totalEconomies, List<UUID> essentialUnitIds, List<UUID> sensitiveUnitIds,
            List<String> valvesToCloseCodes, String isolationFailure,
            boolean truncated, boolean unitLimitExceeded,
            ClickOutcome outcome) {
        public PropagationResult {
            affectedNodeIds = List.copyOf(affectedNodeIds);
            affectedArcIds = List.copyOf(affectedArcIds);
            affectedConnections = List.copyOf(affectedConnections);
            affectedUnitIds = List.copyOf(affectedUnitIds);
            serviceConnectionIds = List.copyOf(serviceConnectionIds);
            essentialUnitIds = List.copyOf(essentialUnitIds);
            sensitiveUnitIds = List.copyOf(sensitiveUnitIds);
            valvesToCloseCodes = List.copyOf(valvesToCloseCodes);
        }
    }
}
