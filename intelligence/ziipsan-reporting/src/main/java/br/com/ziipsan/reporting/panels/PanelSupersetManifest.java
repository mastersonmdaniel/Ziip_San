package br.com.ziipsan.reporting.panels;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.reporting.bi.ReplicaOnlyGuard;
import br.com.ziipsan.reporting.bi.SupersetDataSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ED-49: a ligacao declarativa dos sete paineis com o Superset da ED-47.
 * O manifesto (JSON so pela classe {@code Json} do core) e o insumo da
 * fiacao que cria os dashboards: cada painel le da REPLICA, com a role
 * do tenant, e exibe o registro da P3.41 (JOR-19). A criacao efetiva dos
 * dashboards via API do Superset e o provisionamento sao integracao
 * declarada — este pacote nao finge um Superset no ar.
 *
 * <p>A guarda de replica e a REAL da ED-47: painel nenhum monta sobre
 * fonte que a {@link ReplicaOnlyGuard} reprova (tipo nao-replica ou
 * endpoint do primario) — {@link #requireReplicaOnly} delega sem
 * reimplementar a regra.
 */
public final class PanelSupersetManifest {

    private PanelSupersetManifest() {
    }

    public static String manifestJson() {
        List<Map<String, String>> out = new ArrayList<>();
        for (PanelDefinition definition : StandardPanelCatalog.all()) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("painel", definition.kind().name());
            item.put("titulo", definition.title());
            item.put("slug_proposto", definition.supersetSlug());
            item.put("poc", definition.pocRef());
            item.put("fonte_dados", "READ_REPLICA");
            item.put("registro_indicadores", "P3.41 (JOR-19)");
            item.put("console_tempo_quase_real", "FORA_DO_BI_ED-93 (OPE-22)");
            out.add(item);
        }
        return Json.write(out);
    }

    /** Delega a guarda da ED-47: qualquer violacao impede montar os paineis. */
    public static void requireReplicaOnly(ReplicaOnlyGuard guard,
                                          List<SupersetDataSource> dataSources) {
        guard.requireReplicaOnly(dataSources);
    }
}
