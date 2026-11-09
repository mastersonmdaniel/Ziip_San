package br.com.ziipsan.core.chemicals;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Consistencia Java x SQL pendente (padrao ED-58): as chaves de
 * parametro aparecem no SQL, toda FK composta tem a UNIQUE (tenant_id,
 * id) no pai (licao C3), e NENHUM valor de fabrica/dosagem e semeado.
 * Caminho do SQL via -Ded89.sql (harness); sem a propriedade, o
 * teste localiza o SQL pendente nos caminhos do repo (padrao das
 * MigrationConsistencyTest vizinhas — ED-98/ED-37): a partir do
 * modulo (CWD do surefire) ou da raiz.
 */
class ChemMigrationConsistencyTest {

    private String sql() throws Exception {
        String prop = System.getProperty("ed89.sql");
        Path p = prop != null ? Path.of(prop)
                : Path.of("src/main/resources/db/pendente/ED-89__chemicals_reservoirs.sql");
        if (prop == null && !Files.exists(p)) {
            p = Path.of("shared/ziipsan-core/src/main/resources/db/pendente/ED-89__chemicals_reservoirs.sql");
        }
        return Files.readString(p);
    }

    @Test void parameterKeysPresentInSql() throws Exception {
        String sql = sql();
        for (String key : new String[]{ChemParameters.MIN_STOCK_DAYS, ChemParameters.CONTROLLED_PRODUCTS_LIST,
                ChemParameters.RESERVOIR_CLEANING_INTERVAL_MONTHS, ChemParameters.PF_CONTROL_ENABLED,
                ChemParameters.RECONCILIATION_TOLERANCE_PCT})
            assertTrue(sql.contains(key), "chave ausente no SQL pendente: " + key);
    }

    /**
     * Bloco CREATE TABLE de uma tabela (do seu CREATE ate o proximo
     * CREATE ou o fim do arquivo): e DENTRO dele que a UNIQUE do pai
     * tem de estar — presenca generica no arquivo nao basta (R5).
     */
    private static String createBlock(String sql, String table) {
        String marker = "CREATE TABLE IF NOT EXISTS " + table + " (";
        int start = sql.indexOf(marker);
        assertTrue(start >= 0, "tabela pai ausente no SQL: " + table);
        int next = sql.indexOf("CREATE TABLE IF NOT EXISTS", start + marker.length());
        return next < 0 ? sql.substring(start) : sql.substring(start, next);
    }

    @Test void everyCompositeFkParentHasTenantIdUnique() throws Exception {
        String sql = sql();
        // 1) Parse de TODAS as FKs compostas: nome da constraint,
        //    coluna filha e tabela PAI referenciada.
        var fkPattern = java.util.regex.Pattern.compile(
                "CONSTRAINT\\s+(fk_\\w+)\\s+FOREIGN KEY \\(tenant_id,\\s*(\\w+)\\)\\s*"
                        + "REFERENCES\\s+(\\w+)\\s*\\(tenant_id,\\s*id\\)");
        var matcher = fkPattern.matcher(sql);
        java.util.Map<String, String> parentByFk = new java.util.LinkedHashMap<>();
        while (matcher.find()) parentByFk.put(matcher.group(1), matcher.group(3));
        // 2) Mapa esperado EXATO das 8 FKs compostas da ED-89: se uma
        //    FK sumir, mudar de pai ou nascer FK nova sem entrar aqui,
        //    o teste quebra (amarra pai<->FK, licao C3).
        java.util.Map<String, String> expected = java.util.Map.of(
                "fk_chem_lot_product", "chem_products",
                "fk_chem_lot_receipt", "chem_receipts",
                "fk_chem_consumption_product", "chem_products",
                "fk_chem_pf_reg_product", "chem_products",
                "fk_chem_plans_reservoir", "chem_reservoirs",
                "fk_chem_orders_reservoir", "chem_reservoirs",
                "fk_chem_exec_reservoir", "chem_reservoirs",
                "fk_chem_exec_product", "chem_products");
        assertEquals(expected, parentByFk, "FKs compostas parseadas divergem do mapa esperado");
        // 3) Para CADA FK, o bloco CREATE da SUA tabela pai tem de
        //    declarar a UNIQUE (tenant_id, id) que a sustenta.
        for (var entry : parentByFk.entrySet()) {
            String block = createBlock(sql, entry.getValue());
            assertTrue(block.contains("UNIQUE (tenant_id, id)"),
                    "FK " + entry.getKey() + " referencia " + entry.getValue()
                            + " mas o CREATE de " + entry.getValue()
                            + " nao declara UNIQUE (tenant_id, id)");
        }
        long uniques = sql.lines().filter(l -> l.contains("UNIQUE (tenant_id, id)")).count();
        assertTrue(uniques >= parentByFk.size(),
                "cada FK composta exige UNIQUE (tenant_id, id) no pai: uniques=" + uniques
                        + " fks=" + parentByFk.size());
    }

    @Test void noSeededValuesNorFactoryDefaults() throws Exception {
        String sql = sql();
        assertFalse(sql.contains("INSERT INTO chem_"), "SQL nao semeia produtos/listas/licencas");
        assertFalse(sql.contains("INSERT INTO public.parameter_definitions"), "definitions vao como comentario conferido (padrao ED-50)");
        assertFalse(sql.toLowerCase().contains("factory_default"), "nenhum valor de fabrica");
    }
}
