package br.com.ziipsan.intelligence.gis.ogc;

import java.util.List;
import java.util.UUID;

/**
 * Guarda do proxy WMS da {@code api} para o GeoServer (§7.2 item 1):
 * a api autentica, confere a permissão por camada e encaminha SOMENTE
 * ao workspace do tenant da credencial. Operações fora de
 * GetCapabilities/GetMap/GetFeatureInfo (inclusive qualquer escrita)
 * são recusadas; camada pessoal para credencial de terceiro só pela
 * camada agregada ({@code <camada>_agg} — convenção única de nomes
 * do {@link GisGeoServerProvisioning}, correção D15a).
 *
 * <p>Correção ED-67 (D14): a guarda recebe {@code canViewPersonal} —
 * camada pessoal para credencial NÃO terceiro sem
 * {@code gis.view-personal} é RECUSADA (antes devolvia o nome cru da
 * camada, deixando a "permissão por camada" parcial). ATENÇÃO: esta
 * entrega contém SOMENTE a guarda — NÃO há controller/proxy WMS que
 * a aplique e encaminhe ao GeoServer; a rota é trabalho de
 * integração com o GeoServer real (pendência nomeada).
 */
public final class GisWmsProxyGuard {

    public static final List<String> ALLOWED_OPERATIONS = List.of("GetCapabilities","GetMap","GetFeatureInfo");

    private GisWmsProxyGuard(){}

    /**
     * Nome da camada a encaminhar ao GeoServer, ou recusa.
     * {@code canViewPersonal} é a permissão {@code gis.view-personal}
     * da credencial (D14).
     */
    public static String forwardLayer(UUID credentialTenant, UUID requestedWorkspaceTenant, String operation,
                                      String layer, boolean personalLayer, boolean thirdPartyCredential,
                                      boolean canViewPersonal) {
        if (credentialTenant == null) throw new SecurityException("WMS sem credencial — acesso anônimo recusado (§7.2)");
        if (requestedWorkspaceTenant != null && !requestedWorkspaceTenant.equals(credentialTenant))
            throw new SecurityException("workspace de outro tenant recusado (§7.2 item 2)");
        if (!ALLOWED_OPERATIONS.contains(operation))
            throw new SecurityException("operação WMS não permitida (somente leitura): " + operation);
        if (personalLayer && thirdPartyCredential) return GisGeoServerProvisioning.aggregatedLayerKey(layer);
        if (personalLayer && !canViewPersonal)
            throw new SecurityException("camada pessoal exige gis.view-personal (§7.2 item 1; §12 item 2)");
        return layer;
    }
}
