package br.com.ziipsan.intelligence.gis.asset;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Operacoes da GIS-19 expostas pela API. A implementacao de
 * producao (fiacao de integracao) carrega o retrato pela
 * {@link AssetPorts.InventoryPort} (P1.35) e delega aos motores
 * puros desta entrega; declarada como porta para o nucleo ficar
 * provado sem banco/Spring, como nas ondas GIS anteriores.
 */
public interface AssetOperations {

    List<AssetDtos.AssetView> inventory(UUID tenantId);

    List<AssetDtos.AssetView> confinedSpaces(UUID tenantId);

    List<AssetDtos.PmDueView> preventiveDue(UUID tenantId, LocalDate referenceDate);

    AssetDtos.ReconciliationView reconcile(UUID tenantId, int exercise);

    List<AssetDtos.RiskView> renewalPlan(UUID tenantId);

    /**
     * Programa de exercicio de valvulas/hidrantes (OPE-09) — leitura
     * real da superficie (correcao D8 da revisao da ED-56: o DTO
     * {@code ValveProgramView} era morto, nao era devolvido por
     * nenhum metodo nem endpoint). A fiacao monta cada visao com
     * {@code ValveHydrantProgram.programView} sobre o retrato do
     * tenant; o nucleo esta provado por teste.
     */
    List<AssetDtos.ValveProgramView> valveProgram(UUID tenantId, LocalDate referenceDate);
}
