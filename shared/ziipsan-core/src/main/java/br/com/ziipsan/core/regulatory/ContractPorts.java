package br.com.ziipsan.core.regulatory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Portas da ED-96 para as dependencias EXECUTADAS — esta entrega nao
 * duplica nenhuma delas:
 * <ul>
 *   <li><b>ED-56</b> (inventario regulatorio/GIS-19): {@link ReversibleAssetPort}
 *       le o retrato do ativo da P1.35/ED-56 — o vinculo so aceita
 *       ativo existente E reversivel; o codigo patrimonial continua
 *       so no ativo (DAD-26), aqui viaja apenas o id;</li>
 *   <li><b>P3.31</b> (registro de riscos, {@code cert_risks}):
 *       {@link RiskRegistryPort} confere a existencia do risco
 *       referenciado pela matriz — primeiro uso da matriz sobre
 *       esse registro, sem segundo registro de riscos;</li>
 *   <li><b>Registro de parametros do core</b>: {@link TenantParameters}
 *       resolve os fatos declarados {@code reg.contract_type} e
 *       {@code reg.risk_matrix_required} (classe d, REG) como texto
 *       cru; o parse estrito e de {@link ContractParameters}.</li>
 * </ul>
 */
public final class ContractPorts {

    private ContractPorts() {
    }

    /** Retrato minimo do ativo para o vinculo: existe? e reversivel? (ED-56/P1.35). */
    public record AssetSnapshot(UUID assetId, boolean reversible) {
    }

    public interface ReversibleAssetPort {
        Optional<AssetSnapshot> findAsset(UUID tenantId, UUID assetId);
    }

    /** Registro de riscos da P3.31: a matriz referencia, nao duplica. */
    public interface RiskRegistryPort {
        boolean riskExists(UUID tenantId, UUID riskId);
    }

    /** Parametros declarados do tenant, como texto cru (parse em ContractParameters). */
    public interface TenantParameters {
        Optional<String> resolve(UUID tenantId, String parameterName);
    }

    /** Persistencia do cadastro (a fiacao JDBC e da integracao; aqui o contrato da porta). */
    public interface ContractStore {
        void saveContract(RegulatoryContract.Contract c);

        Optional<RegulatoryContract.Contract> contract(UUID tenantId, UUID contractId);

        List<RegulatoryContract.Contract> contracts(UUID tenantId);

        void saveGoal(RegulatoryContract.ContractGoal g);

        List<RegulatoryContract.ContractGoal> goals(UUID tenantId, UUID contractId);

        void saveAmendment(RegulatoryContract.ContractAmendment a);

        List<RegulatoryContract.ContractAmendment> amendments(UUID tenantId, UUID contractId);

        void saveDocument(RegulatoryContract.ContractDocument d);

        List<RegulatoryContract.ContractDocument> documents(UUID tenantId, UUID contractId);

        void saveRiskEntry(ContractRiskMatrix.Entry e);

        List<ContractRiskMatrix.Entry> riskEntries(UUID tenantId, UUID contractId);

        void saveReversibleAssetLink(UUID tenantId, UUID contractId, UUID assetId);

        List<UUID> reversibleAssetIds(UUID tenantId, UUID contractId);

        void saveFinancialPanel(UUID tenantId, UUID contractId, FinancialCapacity.Panel p);

        List<FinancialCapacity.Panel> financialPanels(UUID tenantId, UUID contractId);
    }
}
