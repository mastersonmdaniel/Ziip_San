package br.com.ziipsan.intelligence.gis.validation;

import br.com.ziipsan.intelligence.gis.validation.GisValidationModel.ConnectionFeature;
import br.com.ziipsan.intelligence.gis.validation.GisValidationModel.ServiceConnectionFeature;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * KPI de orfaos da cadeia da GIS-05 (P2.23) — feicao (ramal) &lt;-&gt;
 * ligacao &lt;-&gt; imovel &lt;-&gt; unidades consumidoras — com a meta
 * declarada ZERO e o caminho de correccao de cada categoria
 * (Ciclo 4 do programa 5x PDCA GIS, criterio "cadastro utilizavel"
 * da decisao 73).
 *
 * <p>Logica pura, sem banco, no mesmo desenho do
 * {@link GisValidationEngine}: quem le o banco monta o retrato
 * ({@link Chain}); quem decide e esta classe; os testes exercitam-na
 * com fixtures em memoria. A fiacao de leitura em BD (carregar o
 * retrato de {@code gis_connection}, {@code service_connections},
 * {@code service_connection_units} e das unidades ancora) e a
 * medicao sobre uma base real de tenant NAO fazem parte desta
 * entrega — pendencia P-PDCAGIS4-01. Sem essa medicao, nenhum zero
 * real de tenant e declarado: o zero aqui e o do retrato medido.
 *
 * <p>Semantica alinhada com as fontes existentes, sem duplicar
 * decisoes:
 * <ul>
 *   <li>{@code RAMAL_SEM_LIGACAO} e a regra
 *   {@code CONNECTION_WITHOUT_LINK} do motor ({@code GisValidationEngine}):
 *   ramal activo (ciclo de vida diferente de {@code ABANDONED} e de
 *   {@code PLANNED}, o {@code isActive} do motor) com
 *   {@code serviceConnectionId} nulo;</li>
 *   <li>{@code LIGACAO_SEM_RAMAL} e a regra
 *   {@code ACTIVE_CONNECTION_WITHOUT_SERVICE_LINE} do motor: ligacao
 *   aberta (nao encerrada) com papel {@code ACTIVE} ou {@code CUT} e
 *   nenhum ramal — a contagem de ramais deriva dos ramais do retrato
 *   com ciclo de vida diferente de {@code ABANDONED}, a contagem da
 *   funcao SQL da V13_10
 *   ({@code gis_active_connections_without_service_line});</li>
 *   <li>as restantes categorias sao os elos da cadeia que o motor
 *   nao confere: vinculo pendurado, ancora de imovel, unidades
 *   vinculadas e imovel sem ligacao aberta.</li>
 * </ul>
 *
 * <p>Cada orfao traz o caminho de correccao sobre mecanismos que ja
 * existem — esta classe nao executa mutacao nenhuma: detectar, contar
 * e apontar a correccao; corrigir corre pelos fluxos revistos
 * (pacote GIS-04 com revisao por outra pessoa, ramal automatico
 * GIS-05 em pacote revisto, cadastro comercial). O caminho ate zero
 * e re-medir: {@link Report#zero()} so e verdade com o retrato
 * corrigido.
 */
public final class Gis05OrphanKpi {

    /** Categoria de orfao: o elo da cadeia que esta partido. */
    public enum Category {
        /** Ramal activo sem ligacao comercial (serviceConnectionId nulo). */
        RAMAL_SEM_LIGACAO,
        /** Ramal activo cujo vinculo aponta para ligacao fora do retrato. */
        RAMAL_COM_VINCULO_PENDURADO,
        /** Ligacao aberta ACTIVE/CUT sem nenhum ramal nao abandonado. */
        LIGACAO_SEM_RAMAL,
        /** Ligacao aberta cuja ancora (imovel) e nula ou esta fora do retrato de imoveis. */
        LIGACAO_SEM_IMOVEL,
        /** Ligacao aberta sem nenhuma unidade consumidora em service_connection_units. */
        LIGACAO_SEM_UNIDADE,
        /** Imovel (unidade ancora) sem nenhuma ligacao aberta ancorada nele. */
        IMOVEL_SEM_LIGACAO
    }

    /** Caminho de correccao nomeado, sobre fluxos que ja existem (ver javadoc da classe). */
    public enum Correction {
        /** Vincular/corrigir o vinculo do ramal em pacote de correccao GIS-04 (CORRECTION/WEB), revisto por outra pessoa. */
        VINCULAR_EM_PACOTE_DE_CORRECAO_GIS04,
        /** Gerar o ramal automatico GIS-05 (AutoServiceLineService; pacote AUTO_SERVICE_LINE revisto) ou desenhar o ramal e gravar via GIS-04. */
        GERAR_RAMAL_AUTOMATICO_GIS05,
        /** Criar a ligacao no cadastro comercial (ServiceConnectionService do registry, regra 1:1 por imovel/tipo enquanto activa). */
        CRIAR_LIGACAO_NO_CADASTRO,
        /** Corrigir a ancora da ligacao no cadastro comercial (ServiceConnectionService do registry). */
        REANCORAR_LIGACAO_NO_CADASTRO,
        /** Vincular as unidades consumidoras a ligacao no cadastro (service_connection_units, via ServiceConnectionService). */
        VINCULAR_UNIDADES_NO_CADASTRO
    }

    /**
     * Um orfao: a categoria, a tabela e o id da feicao orfa, o id
     * relacionado (o vinculo pendurado, a ancora ou a ligacao em
     * falta; nulo quando nao ha relacionado) e a correccao apontada.
     */
    public record Orphan(Category category, String featureTable, UUID featureId,
                         UUID relatedId, Correction correction) {
    }

    /**
     * Retrato da cadeia de um tenant (ou de uma area completa — o
     * contrato e o mesmo do {@code Snapshot} do motor: retrato
     * parcial mede orfaos da parcialidade, nao do cadastro).
     *
     * @param serviceLines            ramais ({@code gis_connection}) do retrato
     * @param serviceConnections      ligacoes ({@code service_connections}) do retrato
     * @param propertyUnitIds         imoveis (unidades ancora) conhecidos do retrato
     * @param consumerUnitsByConnection unidades vinculadas por ligacao
     *                                ({@code service_connection_units}); ligacao ausente
     *                                do mapa = nenhuma unidade vinculada
     */
    public record Chain(List<ConnectionFeature> serviceLines,
                        List<ServiceConnectionFeature> serviceConnections,
                        Set<UUID> propertyUnitIds,
                        Map<UUID, List<UUID>> consumerUnitsByConnection) {
        public Chain {
            serviceLines = serviceLines == null ? List.of() : List.copyOf(serviceLines);
            serviceConnections = serviceConnections == null ? List.of() : List.copyOf(serviceConnections);
            propertyUnitIds = propertyUnitIds == null ? Set.of() : Set.copyOf(propertyUnitIds);
            consumerUnitsByConnection = consumerUnitsByConnection == null
                    ? Map.of() : Map.copyOf(consumerUnitsByConnection);
        }
    }

    /** Relatorio do KPI: a lista de orfaos, o total e a contagem por categoria (todas as categorias, com zero). */
    public record Report(List<Orphan> orphans) {
        public Report {
            orphans = List.copyOf(orphans);
        }

        public long total() {
            return orphans.size();
        }

        /** Verdadeiro somente quando o retrato medido nao tem nenhum orfao — a meta do KPI. */
        public boolean zero() {
            return orphans.isEmpty();
        }

        public Map<Category, Long> countByCategory() {
            Map<Category, Long> counts = new EnumMap<>(Category.class);
            for (Category category : Category.values()) {
                counts.put(category, 0L);
            }
            orphans.forEach(o -> counts.merge(o.category(), 1L, Long::sum));
            return Map.copyOf(counts);
        }

        public List<Orphan> byCategory(Category category) {
            return orphans.stream().filter(o -> o.category() == category).toList();
        }
    }

    private Gis05OrphanKpi() {
    }

    /** Mede o retrato e devolve o relatorio de orfaos, em ordem deterministica (categoria, depois id da feicao). */
    public static Report measure(Chain chain) {
        Objects.requireNonNull(chain, "retrato da cadeia obrigatorio");
        Set<Orphan> orphans = new LinkedHashSet<>();

        Set<UUID> connectionIds = new HashSet<>();
        chain.serviceConnections().forEach(c -> connectionIds.add(c.id()));

        // Ramais: vinculo em falta ou pendurado (so ramais activos, como o motor).
        for (ConnectionFeature line : chain.serviceLines()) {
            if (!isActive(line.lifecycleStatus())) {
                continue;
            }
            if (line.serviceConnectionId() == null) {
                orphans.add(new Orphan(Category.RAMAL_SEM_LIGACAO, "gis_connection", line.id(), null,
                        Correction.VINCULAR_EM_PACOTE_DE_CORRECAO_GIS04));
            } else if (!connectionIds.contains(line.serviceConnectionId())) {
                orphans.add(new Orphan(Category.RAMAL_COM_VINCULO_PENDURADO, "gis_connection", line.id(),
                        line.serviceConnectionId(), Correction.VINCULAR_EM_PACOTE_DE_CORRECAO_GIS04));
            }
        }

        // Ramais por ligacao (contagem da V13_10: ciclo de vida diferente de ABANDONED).
        Map<UUID, Long> linesByConnection = new java.util.HashMap<>();
        for (ConnectionFeature line : chain.serviceLines()) {
            if (line.serviceConnectionId() != null && !"ABANDONED".equals(line.lifecycleStatus())) {
                linesByConnection.merge(line.serviceConnectionId(), 1L, Long::sum);
            }
        }

        Set<UUID> propertiesWithOpenConnection = new HashSet<>();
        for (ServiceConnectionFeature connection : chain.serviceConnections()) {
            if (!connection.open()) {
                continue; // ligacao encerrada e historico: nao gera orfaos
            }
            if (connection.propertyUnitId() != null) {
                propertiesWithOpenConnection.add(connection.propertyUnitId());
            }
            boolean activeForLineRule = "ACTIVE".equals(connection.systemRole())
                    || "CUT".equals(connection.systemRole());
            if (activeForLineRule && linesByConnection.getOrDefault(connection.id(), 0L) == 0) {
                orphans.add(new Orphan(Category.LIGACAO_SEM_RAMAL, "service_connections", connection.id(), null,
                        Correction.GERAR_RAMAL_AUTOMATICO_GIS05));
            }
            if (connection.propertyUnitId() == null
                    || !chain.propertyUnitIds().contains(connection.propertyUnitId())) {
                orphans.add(new Orphan(Category.LIGACAO_SEM_IMOVEL, "service_connections", connection.id(),
                        connection.propertyUnitId(), Correction.REANCORAR_LIGACAO_NO_CADASTRO));
            }
            if (chain.consumerUnitsByConnection().getOrDefault(connection.id(), List.of()).isEmpty()) {
                orphans.add(new Orphan(Category.LIGACAO_SEM_UNIDADE, "service_connections", connection.id(), null,
                        Correction.VINCULAR_UNIDADES_NO_CADASTRO));
            }
        }

        for (UUID propertyId : chain.propertyUnitIds()) {
            if (!propertiesWithOpenConnection.contains(propertyId)) {
                orphans.add(new Orphan(Category.IMOVEL_SEM_LIGACAO, "consumer_units", propertyId, null,
                        Correction.CRIAR_LIGACAO_NO_CADASTRO));
            }
        }

        List<Orphan> sorted = new ArrayList<>(orphans);
        sorted.sort(Comparator.comparing(Orphan::category)
                .thenComparing(o -> o.featureId().toString()));
        return new Report(sorted);
    }

    /** O {@code isActive} do motor de validacao: tudo menos ABANDONED e PLANNED conta como activo. */
    private static boolean isActive(String lifecycleStatus) {
        return !"ABANDONED".equals(lifecycleStatus) && !"PLANNED".equals(lifecycleStatus);
    }
}
