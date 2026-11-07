package br.com.ziipsan.commercial.registry.application;

import br.com.ziipsan.commercial.registry.domain.ConnectionStatus;
import br.com.ziipsan.commercial.registry.domain.ConsumerUnit;
import br.com.ziipsan.commercial.registry.domain.ServiceConnection;
import br.com.ziipsan.commercial.registry.repository.ConnectionStatusRepository;
import br.com.ziipsan.commercial.registry.repository.ConsumerUnitRepository;
import br.com.ziipsan.commercial.registry.repository.ServiceConnectionRepository;
import br.com.ziipsan.commercial.registry.repository.ServiceConnectionUnitRepository;
import br.com.ziipsan.core.api.ApiException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fatos da LIGAÇÃO para o ciclo de faturamento (ED-13; §3.11 do
 * GIS-CADASTRO-TECNICO): a partir da ED-13 o ciclo lê consumo mínimo e
 * situação DA LIGAÇÃO ({@code service_connections} +
 * {@code connection_statuses} da P2.23), única fonte — não mais da
 * projeção {@code consumer_units.water_status_id / sewer_status_id}.
 *
 * <p>Fonte alternativa de água (P1.3): o FATO é a presença de linha
 * vigente em {@code unit_alternative_sources} (V3_12, com tipo,
 * autorização e volume captado) na competência — é ela que alimenta a
 * cobrança de esgoto sem volume de água medido
 * ({@code sewer.no_water_volume_rule} da P2.5) e a situação efetiva da
 * ligação de esgoto (ver {@code ConnectionCycleRule} no billing).
 *
 * <p>SUPOSIÇÃO DECLARADA (correção M6 da revisão): o campo
 * {@code consumer_units.water_source_id} é um UUID {@code pendingRef}
 * SEM catálogo no backend (P1.3/P0.16: o catálogo water-sources não
 * existe) — não há como distinguir "fonte alternativa" de "rede
 * pública" por ele. Por isso {@code water_source_id} NÃO marca fonte
 * alternativa nesta entrega: ele é exposto à parte
 * ({@code waterSourceIdPresent}) e a marcação de alternativo vem SÓ
 * de {@code unit_alternative_sources}. Quando a P1.3 definir o
 * catálogo de fontes e o que nele é alternativo, a marcação passa a
 * consultá-lo — até lá, presumir seria inventar o fato.
 *
 * <p>Unidade sem ligação do tipo pedido é erro explícito: o ciclo não
 * cai para a projeção legada em silêncio. Toda leitura por id passa
 * por {@code RegistryContext.require} (tenant), como no restante do
 * cadastro.
 */
@Service
@Transactional(readOnly = true)
public class ConnectionCycleFactsService {

    /**
     * Fatos resolvidos de UMA ligação para uma competência.
     *
     * @param hasAlternativeWaterSource há fonte alternativa VIGENTE
     *        (linha em {@code unit_alternative_sources}) — ver a
     *        suposição declarada no cabeçalho: {@code water_source_id}
     *        sozinho NÃO marca alternativo
     * @param waterSourceIdPresent o campo {@code water_source_id} da
     *        P1.3 está preenchido em alguma unidade atendida (fato
     *        bruto, exposto para auditoria; não é a marca de fonte
     *        alternativa)
     */
    public record CycleFacts(
            UUID connectionId,
            ConnectionStatus.Kind kind,
            String statusCode,
            ConnectionStatus.SystemRole systemRole,
            boolean bills,
            boolean minimumConsumption,
            Integer minimumBillingConsumption,
            BigDecimal minimumBillingVolume,
            boolean hasAlternativeWaterSource,
            BigDecimal alternativeSourceVolumeM3,
            boolean waterSourceIdPresent,
            List<UUID> servedUnitIds) {
    }

    /**
     * Fatos COMBINADOS (água + esgoto) de um imóvel para o ciclo, com a
     * fonte entregue de {@code sewerOutOfUse} e as bifurcações reais
     * convertidas — é a entrada da ponte fatos→regra (adaptador no
     * billing, {@code ConnectionCycleFactsAdapter}).
     *
     * <p>Fonte de {@code sewerOutOfUse}: a bifurcação
     * {@code sewer.out_of_use_on_water_cut}. Com AUTOMATIC, o esgoto
     * está fora de uso quando a ÁGUA está cortada (papel CUT) ou
     * suprimida (papel DISCONNECTED) — a cascata é automática, então o
     * estado decorre dos dois papéis lidos das ligações. Com
     * SERVICE_ORDER_ONLY ou NONE, esta fonte devolve {@code false}: o
     * fora de uso, quando houver, nasce de OS própria do esgoto e se
     * reflete no PRÓPRIO papel/situação da ligação de esgoto (já lido
     * em {@code sewer.systemRole}/{@code sewer.statusCode}), não numa
     * derivação da água.
     */
    public record CombinedFacts(
            CycleFacts water,
            CycleFacts sewer,
            boolean sewerOutOfUse,
            ConnectionForkParameters.Values forks) {
    }

    private final ServiceConnectionRepository connections;
    private final ServiceConnectionUnitRepository links;
    private final ConnectionStatusRepository statuses;
    private final ConsumerUnitRepository units;

    @PersistenceContext
    private EntityManager em;

    public ConnectionCycleFactsService(ServiceConnectionRepository connections,
                                       ServiceConnectionUnitRepository links,
                                       ConnectionStatusRepository statuses,
                                       ConsumerUnitRepository units) {
        this.connections = connections;
        this.links = links;
        this.statuses = statuses;
        this.units = units;
    }

    public CycleFacts factsForUnit(UUID unitId, ConnectionStatus.Kind kind, LocalDate referenceDate) {
        UUID tenantId = RegistryContext.tenantId();
        ConsumerUnit unit = RegistryContext.require(units.findById(unitId),
                () -> "Unidade consumidora nao encontrada.");
        UUID anchorId = ServiceConnectionRules.propertyAnchorId(unit);
        ServiceConnection connection = connections
                .findByTenantIdAndPropertyUnitIdAndKindAndEndedOnIsNull(tenantId, anchorId, kind)
                .orElseThrow(() -> new ApiException.NotFound(
                        "Unidade " + unitId + " sem ligacao vigente de " + kind
                                + ": o ciclo le a ligacao como unica fonte (ED-13)."));
        ConnectionStatus status = RegistryContext.require(statuses.findById(connection.getStatusId()),
                () -> "Situacao da ligacao nao encontrada.");

        List<UUID> served = links.findByTenantIdAndConnectionIdOrderByUnitId(tenantId, connection.getId())
                .stream().map(link -> link.getUnitId()).toList();

        boolean hasAlternative = false;
        boolean waterSourcePresent = false;
        BigDecimal alternativeVolume = null;
        if (kind == ConnectionStatus.Kind.SEWER) {
            // Fonte alternativa e fato do IMOVEL (unidade-ancora e unidades
            // atendidas): qualquer fonte vigente na competencia conta.
            for (UUID servedId : served) {
                ConsumerUnit servedUnit = RegistryContext.require(units.findById(servedId),
                        () -> "Unidade atendida pela ligacao nao encontrada: " + servedId);
                if (servedUnit.getWaterSourceId() != null) {
                    waterSourcePresent = true;
                }
                AlternativeVolume volume = alternativeVolume(tenantId, servedId, referenceDate);
                if (volume.present()) {
                    hasAlternative = true;
                    alternativeVolume = alternativeVolume == null
                            ? volume.volume() : alternativeVolume.add(volume.volume());
                }
            }
        }
        return new CycleFacts(connection.getId(), kind, status.getCode(), status.getSystemRole(),
                status.isBills(), status.isMinimumConsumption(),
                status.getMinimumBillingConsumption(), status.getMinimumBillingVolume(),
                hasAlternative, alternativeVolume, waterSourcePresent, served);
    }

    /** Água + esgoto combinados, com bifurcações e a fonte de {@code sewerOutOfUse}. */
    public CombinedFacts combinedFactsForUnit(UUID unitId, LocalDate referenceDate) {
        CycleFacts water = factsForUnit(unitId, ConnectionStatus.Kind.WATER, referenceDate);
        CycleFacts sewer = factsForUnit(unitId, ConnectionStatus.Kind.SEWER, referenceDate);
        ConnectionForkParameters.Values forks = ConnectionForkParameters.parse(
                readAcceptedForks(RegistryContext.tenantId()));
        boolean sewerOutOfUse = forks.sewerOutOfUseOnWaterCut()
                == ConnectionForkParameters.SewerOutOfUseOnWaterCut.AUTOMATIC
                && (water.systemRole() == ConnectionStatus.SystemRole.CUT
                || water.systemRole() == ConnectionStatus.SystemRole.DISCONNECTED);
        return new CombinedFacts(water, sewer, sewerOutOfUse, forks);
    }

    private record AlternativeVolume(boolean present, BigDecimal volume) {
    }

    /**
     * Fontes alternativas vigentes da unidade na data, numa consulta
     * ATÔMICA (contagem + soma na mesma leitura): presença de linha
     * vigente é o fato; a soma é o volume captado (0 quando as linhas
     * não declaram volume).
     */
    private AlternativeVolume alternativeVolume(UUID tenantId, UUID unitId, LocalDate referenceDate) {
        Object[] row = (Object[]) em.createNativeQuery("""
                SELECT COUNT(*), COALESCE(SUM(captured_volume_m3), 0) FROM unit_alternative_sources
                WHERE tenant_id = :t AND unit_id = :u AND valid_from <= :d
                  AND (valid_to IS NULL OR valid_to >= :d)
                """)
                .setParameter("t", tenantId)
                .setParameter("u", unitId)
                .setParameter("d", referenceDate)
                .getSingleResult();
        long count = ((Number) row[0]).longValue();
        if (count == 0) {
            return new AlternativeVolume(false, null);
        }
        return new AlternativeVolume(true, new BigDecimal(row[1].toString()));
    }

    /** Valores aceitos e vigentes das bifurcações reais (mesma leitura do motor de OS, P0.19). */
    private Map<String, String> readAcceptedForks(UUID tenantId) {
        Map<String, String> raw = new HashMap<>();
        for (String key : List.of(ConnectionForkParameters.VOLUNTARY_SUPPRESSION_WITH_DEBT,
                ConnectionForkParameters.SEWER_OUT_OF_USE_ON_WATER_CUT,
                ConnectionForkParameters.SEWER_ALTERNATIVE_PCT_ENABLED)) {
            List<?> rows = em.createNativeQuery("""
                    SELECT CAST(v.value AS text) FROM parameter_versions v
                    WHERE v.tenant_id = :t AND v.definition_key = :k AND v.status = 'APPROVED'
                      AND v.valid_from <= CURRENT_DATE
                      AND (v.valid_to IS NULL OR v.valid_to >= CURRENT_DATE)
                      AND EXISTS (SELECT 1 FROM parameter_acceptances a
                                  WHERE a.tenant_id = v.tenant_id AND a.version_id = v.id
                                    AND a.content_hash = v.content_hash)
                    ORDER BY v.valid_from DESC
                    """)
                    .setParameter("t", tenantId)
                    .setParameter("k", key)
                    .setMaxResults(1)
                    .getResultList();
            if (!rows.isEmpty() && rows.get(0) != null) {
                raw.put(key, rows.get(0).toString());
            }
        }
        return raw;
    }
}
