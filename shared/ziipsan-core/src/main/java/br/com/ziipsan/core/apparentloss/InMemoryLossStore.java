package br.com.ziipsan.core.apparentloss;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Store em memoria da ED-95 (prova do nucleo, mesmo padrao de
 * {@code InMemoryContractStore} da ED-96): a persistencia real e o
 * SQL pendente desta entrega, fiado na integracao.
 */
public final class InMemoryLossStore implements LossPorts.LossStore {

    private final ConcurrentHashMap<UUID, List<PropertyRegistryRecord>> properties = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, List<CrossMatch.Finding>> findings = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, List<FraudRules.Suspicion>> suspicions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, List<InspectionAccuracy.RuleOutcome>> outcomes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, List<SelfReport.Case>> selfReports = new ConcurrentHashMap<>();

    private static <T> List<T> bucket(ConcurrentHashMap<UUID, List<T>> map, UUID tenantId) {
        return map.computeIfAbsent(tenantId, k -> new ArrayList<>());
    }

    @Override
    public void saveProperty(PropertyRegistryRecord r) {
        List<PropertyRegistryRecord> list = bucket(properties, r.tenantId());
        synchronized (list) {
            list.removeIf(x -> x.id().equals(r.id()));
            list.add(r);
        }
    }

    @Override
    public List<PropertyRegistryRecord> properties(UUID tenantId) {
        return List.copyOf(bucket(properties, tenantId));
    }

    @Override
    public void saveFinding(CrossMatch.Finding f) {
        List<CrossMatch.Finding> list = bucket(findings, f.tenantId());
        synchronized (list) {
            list.removeIf(x -> x.id().equals(f.id()));
            list.add(f);
        }
    }

    @Override
    public Optional<CrossMatch.Finding> finding(UUID tenantId, UUID findingId) {
        return bucket(findings, tenantId).stream().filter(f -> f.id().equals(findingId)).findFirst();
    }

    @Override
    public List<CrossMatch.Finding> findings(UUID tenantId) {
        return List.copyOf(bucket(findings, tenantId));
    }

    @Override
    public void saveSuspicion(FraudRules.Suspicion s) {
        // Replace por id (mesmo padrao de saveFinding): a transicao de
        // decisao da correcao D1 grava a MESMA suspeita com a decisao,
        // sem duplicar a pendente.
        List<FraudRules.Suspicion> list = bucket(suspicions, s.tenantId());
        synchronized (list) {
            list.removeIf(x -> x.id().equals(s.id()));
            list.add(s);
        }
    }

    @Override
    public Optional<FraudRules.Suspicion> suspicion(UUID tenantId, UUID suspicionId) {
        return bucket(suspicions, tenantId).stream().filter(s -> s.id().equals(suspicionId)).findFirst();
    }

    @Override
    public List<FraudRules.Suspicion> suspicions(UUID tenantId) {
        return List.copyOf(bucket(suspicions, tenantId));
    }

    @Override
    public void saveOutcome(UUID tenantId, InspectionAccuracy.RuleOutcome o) {
        // RuleOutcome e agregado por regra (nao carrega tenant): o tenant vem explicito do chamador.
        List<InspectionAccuracy.RuleOutcome> list = bucket(outcomes, tenantId);
        synchronized (list) {
            list.removeIf(x -> x.rule() == o.rule());
            list.add(o);
        }
    }

    @Override
    public List<InspectionAccuracy.RuleOutcome> outcomes(UUID tenantId) {
        return List.copyOf(bucket(outcomes, tenantId));
    }

    @Override
    public void saveSelfReport(SelfReport.Case c) {
        bucket(selfReports, c.tenantId()).add(c);
    }

    @Override
    public List<SelfReport.Case> selfReports(UUID tenantId) {
        return List.copyOf(bucket(selfReports, tenantId));
    }
}
