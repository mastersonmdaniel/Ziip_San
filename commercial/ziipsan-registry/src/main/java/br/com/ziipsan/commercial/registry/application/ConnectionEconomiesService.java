package br.com.ziipsan.commercial.registry.application;

import br.com.ziipsan.commercial.registry.application.ConnectionEconomies.UnitEconomies;
import br.com.ziipsan.core.api.ApiException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Montagem das economias de uma ligação A PARTIR DOS DADOS REAIS do
 * cadastro (ED-13; POC-10, alínea c): unidades atendidas
 * ({@code service_connection_units}) × economias VIGENTES na
 * competência ({@code unit_subcategories.valid_from/valid_to}, V3_12)
 * × categoria PELO CATÁLOGO (a subcategoria aponta para a categoria —
 * {@code subcategories.category_id -> categories.code}) — nunca texto
 * livre de quem chama.
 *
 * <p>Linha de economia cuja subcategoria/categoria não resolve no
 * catálogo do tenant é erro explícito: categoria presumida
 * demonstraria uma POC sobre dado inventado.
 */
@Service
@Transactional(readOnly = true)
public class ConnectionEconomiesService {

    @PersistenceContext
    private EntityManager em;

    /** Linha resolvida da consulta (unidade, código de categoria do catálogo, economias somadas). */
    record EconomyRow(UUID unitId, String categoryCode, long economies) {
    }

    /** Economias vigentes na competência das unidades atendidas pela ligação. */
    public List<UnitEconomies> economiesOfConnection(UUID connectionId, LocalDate referenceDate) {
        UUID tenantId = RegistryContext.tenantId();
        Long found = ((Number) em.createNativeQuery("""
                SELECT COUNT(*) FROM service_connections
                WHERE tenant_id = :t AND id = :c
                """)
                .setParameter("t", tenantId)
                .setParameter("c", connectionId)
                .getSingleResult()).longValue();
        if (found == 0) {
            throw new ApiException.NotFound("Ligacao nao encontrada: " + connectionId);
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT scu.unit_id, c.code, SUM(us.economies)
                FROM service_connection_units scu
                JOIN unit_subcategories us
                  ON us.tenant_id = scu.tenant_id AND us.unit_id = scu.unit_id
                 AND us.valid_from <= :d AND (us.valid_to IS NULL OR us.valid_to >= :d)
                JOIN subcategories sc
                  ON sc.tenant_id = us.tenant_id AND sc.id = us.subcategory_id
                JOIN categories c
                  ON c.tenant_id = sc.tenant_id AND c.id = sc.category_id
                WHERE scu.tenant_id = :t AND scu.connection_id = :conn
                GROUP BY scu.unit_id, c.code
                ORDER BY scu.unit_id, c.code
                """)
                .setParameter("t", tenantId)
                .setParameter("conn", connectionId)
                .setParameter("d", referenceDate)
                .getResultList();
        List<EconomyRow> resolved = new ArrayList<>();
        for (Object[] row : rows) {
            resolved.add(new EconomyRow(
                    (UUID) row[0], (String) row[1], ((Number) row[2]).longValue()));
        }
        return assemble(resolved);
    }

    /**
     * Linhas resolvidas -> economias por unidade. Categoria vazia ou
     * quantidade fora do intervalo de {@code int} é erro explícito
     * (a categoria vem do catálogo; linha sem código é cadastro
     * quebrado, não categoria nova).
     */
    static List<UnitEconomies> assemble(List<EconomyRow> rows) {
        Map<UUID, Map<String, Integer>> byUnit = new LinkedHashMap<>();
        for (EconomyRow row : rows == null ? List.<EconomyRow>of() : rows) {
            if (row.categoryCode() == null || row.categoryCode().isBlank()) {
                throw new IllegalStateException(
                        "Economia da unidade " + row.unitId() + " sem categoria no catalogo.");
            }
            if (row.economies() < 0 || row.economies() > Integer.MAX_VALUE) {
                throw new IllegalStateException(
                        "Economias fora do intervalo na unidade " + row.unitId()
                                + ": " + row.economies());
            }
            byUnit.computeIfAbsent(row.unitId(), id -> new LinkedHashMap<>())
                    .merge(row.categoryCode(), (int) row.economies(), Integer::sum);
        }
        List<UnitEconomies> out = new ArrayList<>();
        byUnit.forEach((unitId, byCategory) -> out.add(new UnitEconomies(unitId, byCategory)));
        return out;
    }
}
