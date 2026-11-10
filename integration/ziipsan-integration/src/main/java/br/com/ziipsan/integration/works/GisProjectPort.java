package br.com.ziipsan.integration.works;

import java.util.Optional;
import java.util.UUID;

/**
 * ED-102: porta de consumo da ED-53 (projetos e as-built no GIS,
 * modulo intelligence/ziipsan-gis). A ED-102 referencia projetos e
 * pacotes pelos identificadores estaveis do GIS e valida a existencia
 * e o tenant por esta porta — a fiacao ao servico real da ED-53 e
 * pendencia de integracao nomeada (P-ED102-04). Nenhum dado do GIS
 * (geometria, as-built, camadas) e copiado para o dominio de convenios.
 */
public interface GisProjectPort {

    /** Visao minima de um projeto do GIS, so para validar a ligacao. */
    record ProjectView(UUID tenantId, String projectRef, String name) {}

    /** Visao minima de um pacote de obra de um projeto do GIS. */
    record PackageView(UUID tenantId, String projectRef, String packageRef, String name) {}

    Optional<ProjectView> findProject(UUID tenantId, String projectRef);

    Optional<PackageView> findPackage(UUID tenantId, String projectRef, String packageRef);
}
