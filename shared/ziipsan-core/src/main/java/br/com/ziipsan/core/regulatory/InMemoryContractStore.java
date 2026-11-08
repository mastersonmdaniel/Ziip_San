package br.com.ziipsan.core.regulatory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Store em memoria da ED-96 (mesmo papel do InMemoryGovernanceStore da P3.31: prova e referencia da porta). */
public final class InMemoryContractStore implements ContractPorts.ContractStore {

    private final List<RegulatoryContract.Contract> contracts = new ArrayList<>();
    private final List<RegulatoryContract.ContractGoal> goals = new ArrayList<>();
    private final List<RegulatoryContract.ContractAmendment> amendments = new ArrayList<>();
    private final List<RegulatoryContract.ContractDocument> documents = new ArrayList<>();
    private final List<ContractRiskMatrix.Entry> riskEntries = new ArrayList<>();
    private final List<String> assetLinks = new ArrayList<>();
    private final List<String> panels = new ArrayList<>();
    private final List<FinancialCapacity.Panel> panelValues = new ArrayList<>();
    private final List<UUID> panelContracts = new ArrayList<>();
    private final List<UUID> panelTenants = new ArrayList<>();

    @Override public void saveContract(RegulatoryContract.Contract c) { contracts.add(c); }

    @Override public Optional<RegulatoryContract.Contract> contract(UUID tenantId, UUID contractId) {
        return contracts.stream().filter(c -> c.tenantId().equals(tenantId) && c.id().equals(contractId)).findFirst();
    }

    @Override public List<RegulatoryContract.Contract> contracts(UUID tenantId) {
        return contracts.stream().filter(c -> c.tenantId().equals(tenantId)).toList();
    }

    @Override public void saveGoal(RegulatoryContract.ContractGoal g) { goals.add(g); }

    @Override public List<RegulatoryContract.ContractGoal> goals(UUID tenantId, UUID contractId) {
        return goals.stream().filter(g -> g.tenantId().equals(tenantId) && g.contractId().equals(contractId)).toList();
    }

    @Override public void saveAmendment(RegulatoryContract.ContractAmendment a) { amendments.add(a); }

    @Override public List<RegulatoryContract.ContractAmendment> amendments(UUID tenantId, UUID contractId) {
        return amendments.stream().filter(a -> a.tenantId().equals(tenantId) && a.contractId().equals(contractId)).toList();
    }

    @Override public void saveDocument(RegulatoryContract.ContractDocument d) { documents.add(d); }

    @Override public List<RegulatoryContract.ContractDocument> documents(UUID tenantId, UUID contractId) {
        return documents.stream().filter(d -> d.tenantId().equals(tenantId) && d.contractId().equals(contractId)).toList();
    }

    @Override public void saveRiskEntry(ContractRiskMatrix.Entry e) { riskEntries.add(e); }

    @Override public List<ContractRiskMatrix.Entry> riskEntries(UUID tenantId, UUID contractId) {
        return riskEntries.stream().filter(e -> e.tenantId().equals(tenantId) && e.contractId().equals(contractId)).toList();
    }

    @Override public void saveReversibleAssetLink(UUID tenantId, UUID contractId, UUID assetId) {
        assetLinks.add(tenantId + ":" + contractId + ":" + assetId);
    }

    @Override public List<UUID> reversibleAssetIds(UUID tenantId, UUID contractId) {
        String prefix = tenantId + ":" + contractId + ":";
        return assetLinks.stream().filter(s -> s.startsWith(prefix))
                .map(s -> UUID.fromString(s.substring(prefix.length()))).toList();
    }

    @Override public void saveFinancialPanel(UUID tenantId, UUID contractId, FinancialCapacity.Panel p) {
        panels.add(tenantId + ":" + contractId + ":" + p.exercise());
        panelValues.add(p);
        panelContracts.add(contractId);
        panelTenants.add(tenantId);
    }

    @Override public List<FinancialCapacity.Panel> financialPanels(UUID tenantId, UUID contractId) {
        List<FinancialCapacity.Panel> out = new ArrayList<>();
        for (int i = 0; i < panelValues.size(); i++) {
            if (panelTenants.get(i).equals(tenantId) && panelContracts.get(i).equals(contractId)) out.add(panelValues.get(i));
        }
        return List.copyOf(out);
    }
}
