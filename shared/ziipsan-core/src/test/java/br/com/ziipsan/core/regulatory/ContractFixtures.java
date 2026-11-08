package br.com.ziipsan.core.regulatory;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Massa SINTETICA declarada da ED-96 (nenhum dado real): municipio ficticio de codigo IBGE valido em formato. */
final class ContractFixtures {

    static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID OTHER_TENANT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final String MUNICIPALITY = "4202008"; // formato IBGE 7 digitos (massa sintetica)
    static final UUID REVERSIBLE_ASSET = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    static final UUID NON_REVERSIBLE_ASSET = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    static final UUID KNOWN_RISK = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    static final UUID UNKNOWN_RISK = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    private ContractFixtures() {
    }

    static RegulatoryContract.Contract contract(RegulatoryContract.ContractType type,
                                                  RegulatoryContract.ServiceKind service,
                                                  LocalDate from, LocalDate to) {
        return new RegulatoryContract.Contract(UUID.randomUUID(), TENANT, MUNICIPALITY, "Municipio Sintetico",
                service, type, "CONTRATO-SINTETICO-001",
                new RegulatoryContract.Parties("Municipio Sintetico", "Prestador Sintetico SA", "Agencia Sintetica"),
                "Lei municipal sintetica 001/2020",
                new RegulatoryContract.Validity(from, to),
                new RegulatoryContract.RebalancingClause("Cl. 10", "Reequilibrio por fato imprevisivel, como declarado"),
                new RegulatoryContract.UnamortizedIndemnity("Cl. 22", "Metodo declarado no instrumento sintetico"));
    }

    static RegulatoryContract.Contract concession() {
        return contract(RegulatoryContract.ContractType.CONCESSION, RegulatoryContract.ServiceKind.WATER_SUPPLY,
                LocalDate.of(2020, 1, 1), LocalDate.of(2050, 1, 1));
    }

    static ContractPorts.ReversibleAssetPort assetPort() {
        return (tenantId, assetId) -> {
            if (!TENANT.equals(tenantId)) return Optional.empty();
            if (REVERSIBLE_ASSET.equals(assetId))
                return Optional.of(new ContractPorts.AssetSnapshot(assetId, true));
            if (NON_REVERSIBLE_ASSET.equals(assetId))
                return Optional.of(new ContractPorts.AssetSnapshot(assetId, false));
            return Optional.empty();
        };
    }

    static ContractPorts.RiskRegistryPort riskPort() {
        return (tenantId, riskId) -> TENANT.equals(tenantId) && KNOWN_RISK.equals(riskId);
    }

    static ContractPorts.TenantParameters parameters(Map<String, String> values) {
        Map<String, String> copy = new HashMap<>(values);
        return (tenantId, name) -> TENANT.equals(tenantId) ? Optional.ofNullable(copy.get(name)) : Optional.empty();
    }

    static RegulatoryContractService service(Map<String, String> params) {
        return new RegulatoryContractService(new InMemoryContractStore(), assetPort(), riskPort(), parameters(params));
    }

    static RegulatoryContractService service() {
        return service(Map.of(
                ContractParameters.CONTRACT_TYPE, "CONCESSION",
                ContractParameters.RISK_MATRIX_REQUIRED, "true"));
    }

    static Set<String> unused() {
        return Set.of();
    }
}
