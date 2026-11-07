package br.com.ziipsan.intelligence.gis.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Leitura dos problemas da validação contínua ({@code gis_validation_issue}, GIS-03).
 *
 * <p>A tabela é criada pela V13_8 e é escrita pelo motor de validação; aqui ela é só
 * consultada, por isso o repositório é uma classe fina sobre o {@link EntityManager}, sem
 * entidade mapeada: o que a exportação EPANET precisa saber é quais feições candidatas têm
 * problema de severidade {@code ERROR} ainda {@code OPEN}. Problema aceito
 * ({@code ACCEPTED}, com justificativa) ou resolvido ({@code RESOLVED}) não exclui.
 *
 * <p>A consulta é nativa sobre a tabela, filtrada por tenant e pelos ids das feições
 * candidatas (o {@code id} que a view {@code gis_network_element} expõe é o id da feição).
 * Os ids são únicos entre as tabelas de feição (UUID), então o {@code feature_table} não
 * entra no filtro.
 */
@Repository
public class GisValidationIssueRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Regras com erro grave aberto por feição: mapa id da feição → códigos de regra
     * ({@code rule_code}) distintos, na ordem devolvida pelo banco. Feição sem erro grave
     * aberto não aparece no mapa. Conjunto de ids vazio devolve mapa vazio, sem consulta.
     */
    public Map<UUID, List<String>> findOpenErrorRulesByFeatureId(UUID tenantId, Collection<UUID> featureIds) {
        if (featureIds == null || featureIds.isEmpty()) {
            return Map.of();
        }
        Query query = entityManager.createNativeQuery("""
                SELECT feature_id, rule_code
                FROM gis_validation_issue
                WHERE tenant_id = :tenantId
                  AND severity = 'ERROR'
                  AND status = 'OPEN'
                  AND feature_id IN :featureIds
                """);
        query.setParameter("tenantId", tenantId);
        query.setParameter("featureIds", new ArrayList<>(featureIds));

        Map<UUID, List<String>> rulesByFeature = new LinkedHashMap<>();
        for (Object row : query.getResultList()) {
            Object[] columns = (Object[]) row;
            UUID featureId = columns[0] instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(columns[0]));
            String ruleCode = columns[1] == null ? null : String.valueOf(columns[1]);
            List<String> rules = rulesByFeature.computeIfAbsent(featureId, k -> new ArrayList<>());
            if (ruleCode != null && !rules.contains(ruleCode)) {
                rules.add(ruleCode);
            }
        }
        return rulesByFeature;
    }
}
