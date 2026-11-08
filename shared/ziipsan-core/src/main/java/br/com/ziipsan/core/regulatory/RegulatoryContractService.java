package br.com.ziipsan.core.regulatory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Cadastro do instrumento contratual regulatorio (ED-96, GES-02).
 *
 * <p>Regras executaveis do aceite:
 * <ul>
 *   <li>registro por municipio e por servico: dois instrumentos do
 *       mesmo tenant nao podem cobrir o mesmo municipio+servico com
 *       vigencias sobrepostas (a troca de instrumento e feita com
 *       nova vigencia, nunca com sobreposicao silenciosa);</li>
 *   <li>matriz de riscos (NR 5/ANA) como primeiro uso sobre o
 *       registro da P3.31: linha so entra referenciando risco
 *       EXISTENTE naquele registro e naquele tenant;</li>
 *   <li>vinculo com bens reversiveis so sobre ativo existente e
 *       reversivel do inventario da ED-56;</li>
 *   <li>painel de capacidade (Decreto 11.598/2023) so para
 *       instrumento SOB CONTRATO — prestacao direta e recusada;</li>
 *   <li>completude cadastral declarada: lacunas sao NOMEADAS
 *       (matriz exigida e ausente, clausulas nao declaradas,
 *       documento ausente, divergencia com {@code reg.contract_type}),
 *       nunca preenchidas com texto ou valor presumido.</li>
 * </ul>
 * Item 32 das decisoes do dono (consumidores de conta): esta
 * entrega nao toca conta, tarifa nem billing — nenhum consumidor
 * de conta e afetado.
 */
public final class RegulatoryContractService {

    private final ContractPorts.ContractStore store;
    private final ContractPorts.ReversibleAssetPort assets;
    private final ContractPorts.RiskRegistryPort risks;
    private final ContractPorts.TenantParameters parameters;

    public RegulatoryContractService(ContractPorts.ContractStore store,
                                     ContractPorts.ReversibleAssetPort assets,
                                     ContractPorts.RiskRegistryPort risks,
                                     ContractPorts.TenantParameters parameters) {
        this.store = Objects.requireNonNull(store, "servico sem store");
        this.assets = assets;
        this.risks = risks;
        this.parameters = parameters;
    }

    /** Lacunas nomeadas da completude cadastral (nenhuma e corrigida em silencio). */
    public enum CompletenessGap {
        RISK_MATRIX_REQUIRED_BUT_EMPTY, RISK_MATRIX_REQUIREMENT_UNDECLARED,
        REBALANCING_CLAUSE_NOT_DECLARED, INDEMNITY_CLAUSE_NOT_DECLARED,
        NO_DOCUMENT_REGISTERED, CONTRACT_TYPE_PARAMETER_UNDECLARED,
        CONTRACT_TYPE_DIVERGES_FROM_PARAMETER
    }

    public record Completeness(UUID contractId, List<CompletenessGap> gaps) {
        public boolean complete() {
            return gaps.isEmpty();
        }
    }

    private RegulatoryContract.Contract requireContract(UUID tenantId, UUID contractId) {
        return store.contract(tenantId, contractId).orElseThrow(() ->
                new IllegalArgumentException("instrumento inexistente neste tenant: " + contractId));
    }

    /**
     * Registra o instrumento. Recusa sobreposicao de vigencia para o
     * mesmo municipio+servico no mesmo tenant (dois instrumentos
     * vigentes ao mesmo tempo para o mesmo recorte e contradicao
     * cadastral, nao escolha silenciosa).
     */
    public RegulatoryContract.Contract registerContract(RegulatoryContract.Contract c) {
        for (RegulatoryContract.Contract other : store.contracts(c.tenantId())) {
            if (other.municipalityCode().equals(c.municipalityCode())
                    && other.service() == c.service()
                    && other.validity().overlaps(c.validity())) {
                throw new IllegalArgumentException("ja existe instrumento para o municipio " + c.municipalityCode()
                        + " e servico " + c.service() + " com vigencia sobreposta: " + other.contractRef());
            }
        }
        store.saveContract(c);
        return c;
    }

    public void addGoal(RegulatoryContract.ContractGoal g) {
        requireContract(g.tenantId(), g.contractId());
        if (g.amendmentId() != null) {
            boolean amendmentExists = store.amendments(g.tenantId(), g.contractId()).stream()
                    .anyMatch(a -> a.id().equals(g.amendmentId()));
            if (!amendmentExists)
                throw new IllegalArgumentException("meta ligada a aditivo inexistente neste instrumento: " + g.amendmentId());
        }
        store.saveGoal(g);
    }

    public void addAmendment(RegulatoryContract.ContractAmendment a) {
        RegulatoryContract.Contract c = requireContract(a.tenantId(), a.contractId());
        if (a.signedOn().isBefore(c.validity().validFrom()))
            throw new IllegalArgumentException("aditivo assinado antes do inicio da vigencia do instrumento");
        boolean duplicate = store.amendments(a.tenantId(), a.contractId()).stream()
                .anyMatch(x -> x.amendmentRef().equals(a.amendmentRef()));
        if (duplicate)
            throw new IllegalArgumentException("aditivo ja registrado com esta referencia: " + a.amendmentRef());
        store.saveAmendment(a);
    }

    public void addDocument(RegulatoryContract.ContractDocument d) {
        requireContract(d.tenantId(), d.contractId());
        store.saveDocument(d);
    }

    /**
     * Linha da matriz de riscos (NR 5/ANA) sobre o registro da
     * P3.31: sem a porta fiada ou sem o risco la, a linha e recusada
     * — a matriz nunca vira segundo registro de riscos.
     */
    public void addRiskEntry(ContractRiskMatrix.Entry e) {
        requireContract(e.tenantId(), e.contractId());
        if (risks == null)
            throw new IllegalStateException("registro de riscos da P3.31 nao fiado: matriz recusada");
        if (!risks.riskExists(e.tenantId(), e.riskId()))
            throw new IllegalArgumentException("risco inexistente no registro da P3.31 para este tenant: " + e.riskId());
        boolean duplicate = store.riskEntries(e.tenantId(), e.contractId()).stream()
                .anyMatch(x -> x.riskId().equals(e.riskId()));
        if (duplicate)
            throw new IllegalArgumentException("risco ja esta na matriz deste instrumento: " + e.riskId());
        store.saveRiskEntry(e);
    }

    public ContractRiskMatrix.Matrix riskMatrix(UUID tenantId, UUID contractId) {
        requireContract(tenantId, contractId);
        return new ContractRiskMatrix.Matrix(contractId, store.riskEntries(tenantId, contractId));
    }

    /**
     * Vinculo com bem reversivel do inventario da ED-56: o ativo tem
     * de existir no retrato e ser reversivel; bem nao reversivel ou
     * inexistente e recusado (o vinculo e com os REVERSIVEIS).
     */
    public void linkReversibleAsset(UUID tenantId, UUID contractId, UUID assetId) {
        requireContract(tenantId, contractId);
        if (assets == null)
            throw new IllegalStateException("inventario de ativos da ED-56 nao fiado: vinculo recusado");
        ContractPorts.AssetSnapshot snap = assets.findAsset(tenantId, assetId).orElseThrow(() ->
                new IllegalArgumentException("ativo inexistente no inventario da ED-56: " + assetId));
        if (!snap.reversible())
            throw new IllegalArgumentException("ativo nao e bem reversivel no inventario da ED-56: " + assetId);
        if (store.reversibleAssetIds(tenantId, contractId).contains(assetId))
            throw new IllegalArgumentException("ativo ja vinculado a este instrumento: " + assetId);
        store.saveReversibleAssetLink(tenantId, contractId, assetId);
    }

    /**
     * Painel de capacidade economico-financeira (Decreto
     * 11.598/2023): OFERECIDO SO A TENANT SOB CONTRATO — instrumento
     * de prestacao direta e recusado, como manda o aceite (GES-02).
     * O calculo e o de {@link FinancialCapacity} sobre a exportacao
     * do ERP entregue pelo chamador.
     */
    public FinancialCapacity.Panel financialPanel(UUID tenantId, UUID contractId, FinancialCapacity.ErpExport export) {
        RegulatoryContract.Contract c = requireContract(tenantId, contractId);
        Objects.requireNonNull(export, "painel sem exportacao do ERP");
        if (!c.underContract())
            throw new IllegalArgumentException("painel de capacidade so e oferecido a tenant sob contrato "
                    + "(Decreto 11.598/2023); instrumento de prestacao direta nao e contrato");
        FinancialCapacity.Panel panel = FinancialCapacity.panel(export);
        store.saveFinancialPanel(tenantId, contractId, panel);
        return panel;
    }

    /** O painel se aplica a algum instrumento do tenant? (existe instrumento sob contrato). */
    public boolean panelOfferedTo(UUID tenantId) {
        return store.contracts(tenantId).stream().anyMatch(RegulatoryContract.Contract::underContract);
    }

    private Optional<String> parameter(UUID tenantId, String name) {
        return parameters == null ? Optional.empty() : parameters.resolve(tenantId, name);
    }

    /**
     * Completude cadastral: compara o registro com os fatos
     * declarados nos parametros e com os campos do aceite. Lacuna
     * nomeada nao bloqueia o cadastro (dado cadastral), mas impede
     * declarar o instrumento completo — {@link #assertComplete}.
     */
    public Completeness completeness(UUID tenantId, UUID contractId) {
        RegulatoryContract.Contract c = requireContract(tenantId, contractId);
        List<CompletenessGap> gaps = new ArrayList<>();

        Boolean matrixRequired = ContractParameters.declaredRiskMatrixRequired(
                parameter(tenantId, ContractParameters.RISK_MATRIX_REQUIRED).orElse(null));
        if (matrixRequired == null) {
            gaps.add(CompletenessGap.RISK_MATRIX_REQUIREMENT_UNDECLARED);
        } else if (matrixRequired && store.riskEntries(tenantId, contractId).isEmpty()) {
            gaps.add(CompletenessGap.RISK_MATRIX_REQUIRED_BUT_EMPTY);
        }
        if (c.rebalancingClause() == null) gaps.add(CompletenessGap.REBALANCING_CLAUSE_NOT_DECLARED);
        if (c.indemnity() == null) gaps.add(CompletenessGap.INDEMNITY_CLAUSE_NOT_DECLARED);
        if (store.documents(tenantId, contractId).isEmpty()) gaps.add(CompletenessGap.NO_DOCUMENT_REGISTERED);

        RegulatoryContract.ContractType declared = ContractParameters.declaredContractType(
                parameter(tenantId, ContractParameters.CONTRACT_TYPE).orElse(null));
        if (declared == null) gaps.add(CompletenessGap.CONTRACT_TYPE_PARAMETER_UNDECLARED);
        else if (declared != c.contractType()) gaps.add(CompletenessGap.CONTRACT_TYPE_DIVERGES_FROM_PARAMETER);

        return new Completeness(contractId, List.copyOf(gaps));
    }

    public void assertComplete(UUID tenantId, UUID contractId) {
        Completeness completeness = completeness(tenantId, contractId);
        if (!completeness.complete())
            throw new IllegalStateException("instrumento com lacunas de completude: " + completeness.gaps());
    }
}
