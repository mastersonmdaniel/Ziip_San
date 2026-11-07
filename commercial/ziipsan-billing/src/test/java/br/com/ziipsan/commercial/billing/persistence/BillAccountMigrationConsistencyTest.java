package br.com.ziipsan.commercial.billing.persistence;

import br.com.ziipsan.commercial.billing.domain.BillCreditEntity;
import br.com.ziipsan.commercial.billing.domain.BillDebitEntity;
import br.com.ziipsan.commercial.billing.domain.BillTemplateEntity;
import br.com.ziipsan.commercial.billing.domain.BillTemplateVersionEntity;
import br.com.ziipsan.commercial.billing.domain.CreditOriginEntity;
import br.com.ziipsan.commercial.billing.domain.CreditReceivableEntity;
import br.com.ziipsan.commercial.billing.domain.DebitReceivableEntity;
import br.com.ziipsan.commercial.billing.domain.DebitTypeEntity;
import br.com.ziipsan.commercial.billing.domain.OptionalCategoryEnrollmentEntity;
import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guardas da migration {@code V4_4__bill_account_models_and_versioning.sql} (P2.5),
 * no mesmo modelo do {@code SocialMigrationConsistencyTest}: as tabelas novas batem
 * com as entidades, e os invariantes de DAD-15/DAD-20 estao no SQL -- snapshot so
 * cifrado e fora do audit_log, parametro do nome impresso com o padrao de fabrica
 * da decisao do dono.
 *
 * <p><b>V4_4 fundida (correcao da revisao cruzada, B3/B6):</b> a migration passou a
 * conter tambem os trechos da F3 (catalogos debit_types/credit_origins e a
 * proveniencia dos recebiveis/linhas), da F5 (fixed_part_value/TFDI, a tabela
 * optional_category_enrollments e as 19 chaves) e o bloco B da F4
 * (bill.partial_period_rule, meter.after_cap_rule). Esta guarda cobre o schema
 * fundido: as 5 tabelas novas contra as 5 entidades, e as entidades re-tipadas de
 * recebiveis/linhas contra o mapa da V4_1 depois de aplicadas, em ordem, as
 * alteracoes da V4_14 (reference_month das linhas) e as da secao 4 da propria
 * V4_4 (ADD COLUMN, SET NOT NULL e DROP COLUMN do trecho F3).
 *
 * <p><b>DAD-14 saiu daqui (correcao B1):</b> o versionamento de
 * {@code consumption_records} virou a migration V5_6 do metering; as assercoes
 * textuais que o guardavam migraram para o {@code MigrationConsistencyTest} do
 * metering. Aqui resta so a trava do ponteiro: a V4_4 nao pode voltar a alterar
 * {@code consumption_records} (num tenant novo ela roda antes da V5_1).
 *
 * <p>As colunas novas de {@code bills} tambem sao guardadas pelo
 * {@code MigrationConsistencyTest} (que aplica os ALTERs da V4_4 sobre o mapa da
 * V4_14).
 */
class BillAccountMigrationConsistencyTest {

    private static final List<Class<? extends TenantAwareEntity>> ENTITIES = List.of(
            BillTemplateEntity.class, BillTemplateVersionEntity.class,
            DebitTypeEntity.class, CreditOriginEntity.class,
            OptionalCategoryEnrollmentEntity.class);

    private static final List<Class<? extends TenantAwareEntity>> RECEIVABLE_ENTITIES = List.of(
            DebitReceivableEntity.class, CreditReceivableEntity.class,
            BillDebitEntity.class, BillCreditEntity.class);

    private static final Set<String> F3_TABLES = Set.of(
            "debit_receivables", "credit_receivables", "bill_debits", "bill_credits");

    private static final Set<String> INHERITED = Set.of("id", "tenant_id", "created_at", "updated_at", "created_by",
            "updated_by", "version", "tenant_schema");

    record SqlColumn(String name, String type, Integer length, Integer precision, Integer scale, boolean notNull) {
    }

    private static String sql;
    private static Map<String, Map<String, String>> tables;
    private static Map<String, Map<String, SqlColumn>> fusedReceivables;

    @BeforeAll
    static void load() throws IOException {
        try (InputStream in = BillAccountMigrationConsistencyTest.class
                .getResourceAsStream("/db/migration/tenant/V4_4__bill_account_models_and_versioning.sql")) {
            assertThat(in).as("migration V4_4 no classpath").isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
        tables = parseTables(sql);

        // Mapa dos recebiveis/linhas: V4_1 + ALTERs da V4_14 (reference_month das
        // linhas, com o SET NOT NULL posterior ao backfill) + secao 4 da V4_4 (F3).
        String v41;
        try (InputStream in = BillAccountMigrationConsistencyTest.class
                .getResourceAsStream("/db/migration/tenant/V4_1__create_billing_schema.sql")) {
            assertThat(in).as("migration V4_1 no classpath").isNotNull();
            v41 = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
        fusedReceivables = parseSqlTables(v41);
        String v414;
        try (InputStream in = BillAccountMigrationConsistencyTest.class
                .getResourceAsStream("/db/migration/tenant/V4_14__bill_partition_and_batch_audit.sql")) {
            assertThat(in).as("migration V4_14 no classpath").isNotNull();
            v414 = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
        applyAlters(fusedReceivables, v414, Set.of("bill_debits", "bill_credits"));
        applyAlters(fusedReceivables, sql, F3_TABLES);
    }

    private static Map<String, Map<String, String>> parseTables(String text) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        Matcher header = Pattern.compile("CREATE TABLE IF NOT EXISTS (\\w+) \\(\\n(.*?)\\n\\);", Pattern.DOTALL).matcher(text);
        Pattern column = Pattern.compile("^ {4}([a-z][a-z0-9_]*) +(UUID|VARCHAR\\(\\d+\\)|INT|DATE|BOOLEAN|TIMESTAMPTZ|JSONB|TEXT|NUMERIC\\(\\d+,\\d+\\))(?=[ ,]|$)");
        while (header.find()) {
            Map<String, String> columns = new LinkedHashMap<>();
            for (String line : header.group(2).split("\n")) {
                Matcher m = column.matcher(line);
                if (m.find()) {
                    columns.put(m.group(1), m.group(2).replaceAll("\\(.*\\)", ""));
                }
            }
            result.put(header.group(1), columns);
        }
        return result;
    }

    private static Map<String, Map<String, SqlColumn>> parseSqlTables(String text) {
        Map<String, Map<String, SqlColumn>> result = new LinkedHashMap<>();
        Matcher header = Pattern.compile("CREATE TABLE IF NOT EXISTS (\\w+) \\(\\n(.*?)\\n\\);", Pattern.DOTALL).matcher(text);
        Pattern column = Pattern.compile("^ {4}([a-z][a-z0-9_]*) +(UUID|VARCHAR\\((\\d+)\\)|INT|SMALLINT|DATE|BOOLEAN|NUMERIC\\((\\d+),(\\d+)\\)|TIMESTAMPTZ|JSONB)(?=[ ,]|$)(.*)$");
        while (header.find()) {
            Map<String, SqlColumn> columns = new LinkedHashMap<>();
            for (String line : header.group(2).split("\\n")) {
                Matcher m = column.matcher(line);
                if (m.matches()) {
                    String type = m.group(2).replaceAll("\\(.*\\)", "");
                    columns.put(m.group(1), new SqlColumn(m.group(1), type,
                            m.group(3) == null ? null : Integer.valueOf(m.group(3)),
                            m.group(4) == null ? null : Integer.valueOf(m.group(4)),
                            m.group(5) == null ? null : Integer.valueOf(m.group(5)),
                            m.group(6).contains("NOT NULL")));
                }
            }
            result.put(header.group(1), columns);
        }
        return result;
    }

    /**
     * Aplica, na ordem do texto, os ADD COLUMN / DROP COLUMN / SET NOT NULL de uma
     * migration sobre o mapa de tabelas -- o mesmo espirito com que o
     * {@code MigrationConsistencyTest} aplica a V4_14 sobre o mapa da V4_1. A regex
     * de tipos cobre UUID, VARCHAR(n), DATE, INT, BOOLEAN e NUMERIC(14,2).
     */
    private static void applyAlters(Map<String, Map<String, SqlColumn>> tables, String text, Set<String> targets) {
        List<Map.Entry<Integer, Runnable>> actions = new ArrayList<>();
        Matcher add = Pattern.compile("ALTER TABLE (\\w+) ADD COLUMN IF NOT EXISTS ([a-z][a-z0-9_]*) "
                + "+(UUID|VARCHAR\\((\\d+)\\)|INT|DATE|BOOLEAN|NUMERIC\\((\\d+),(\\d+)\\))([^;]*);").matcher(text);
        while (add.find()) {
            if (!targets.contains(add.group(1))) {
                continue;
            }
            String table = add.group(1);
            String name = add.group(2);
            String type = add.group(3).replaceAll("\\(.*\\)", "");
            Integer length = add.group(4) == null ? null : Integer.valueOf(add.group(4));
            Integer precision = add.group(5) == null ? null : Integer.valueOf(add.group(5));
            Integer scale = add.group(6) == null ? null : Integer.valueOf(add.group(6));
            boolean notNull = add.group(7).contains("NOT NULL");
            int at = add.start();
            actions.add(Map.entry(at, () -> tables.get(table)
                    .put(name, new SqlColumn(name, type, length, precision, scale, notNull))));
        }
        Matcher drop = Pattern.compile("ALTER TABLE (\\w+) DROP COLUMN IF EXISTS ([a-z][a-z0-9_]+);").matcher(text);
        while (drop.find()) {
            if (!targets.contains(drop.group(1))) {
                continue;
            }
            String table = drop.group(1);
            String name = drop.group(2);
            int at = drop.start();
            actions.add(Map.entry(at, () -> tables.get(table).remove(name)));
        }
        Matcher setNotNull = Pattern.compile("ALTER TABLE (\\w+) ALTER COLUMN ([a-z][a-z0-9_]+) SET NOT NULL;").matcher(text);
        while (setNotNull.find()) {
            if (!targets.contains(setNotNull.group(1))) {
                continue;
            }
            String table = setNotNull.group(1);
            String name = setNotNull.group(2);
            int at = setNotNull.start();
            actions.add(Map.entry(at, () -> {
                SqlColumn old = tables.get(table).get(name);
                tables.get(table).put(name, new SqlColumn(name, old.type(), old.length(),
                        old.precision(), old.scale(), true));
            }));
        }
        actions.sort(Map.Entry.comparingByKey());
        actions.forEach(e -> e.getValue().run());
    }

    static Stream<Class<? extends TenantAwareEntity>> entities() {
        return ENTITIES.stream();
    }

    static Stream<Class<? extends TenantAwareEntity>> receivableEntities() {
        return RECEIVABLE_ENTITIES.stream();
    }

    @Test
    @DisplayName("a V4_4 fundida cria exatamente as 5 tabelas novas (F2, F3 e F5), todas registradas para RLS")
    void fusedTablesExistAndAreRegistered() {
        assertThat(tables.keySet()).containsExactlyInAnyOrder(
                "bill_templates", "bill_template_versions",
                "debit_types", "credit_origins", "optional_category_enrollments");
        assertThat(sql).contains("SELECT register_tenant_table('bill_templates');");
        assertThat(sql).contains("SELECT register_tenant_table('bill_template_versions');");
        assertThat(sql).contains("SELECT register_tenant_table('debit_types');");
        assertThat(sql).contains("SELECT register_tenant_table('credit_origins');");
        assertThat(sql).contains("SELECT register_tenant_table('optional_category_enrollments');");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("entities")
    @DisplayName("cada coluna mapeada das tabelas novas existe na V4_4, e nenhuma coluna do SQL ficou sem mapeamento")
    void newTableColumnsMatch(Class<? extends TenantAwareEntity> entity) {
        String table = entity.getAnnotation(Table.class).name();
        Map<String, String> columns = tables.get(table);
        assertThat(columns).as("tabela %s", table).isNotNull();

        Set<String> mapped = new LinkedHashSet<>();
        for (Field f : entity.getDeclaredFields()) {
            Column c = f.getAnnotation(Column.class);
            if (c == null) {
                continue;
            }
            mapped.add(c.name());
            String sqlType = columns.get(c.name());
            assertThat(sqlType).as("%s.%s existe no SQL", table, c.name()).isNotNull();
            assertThat(typeMatches(f, c, sqlType)).as("%s.%s: Java %s x SQL %s", table, c.name(),
                    f.getType().getSimpleName(), sqlType).isTrue();
        }
        Set<String> sqlOnly = new LinkedHashSet<>(columns.keySet());
        sqlOnly.removeAll(mapped);
        sqlOnly.removeAll(INHERITED);
        assertThat(sqlOnly).as("colunas do SQL sem mapeamento em %s", entity.getSimpleName()).isEmpty();
    }

    private static boolean typeMatches(Field f, Column c, String sqlType) {
        Class<?> t = f.getType();
        if (t == String.class) {
            if (sqlType.equals("JSONB")) {
                return f.isAnnotationPresent(org.hibernate.annotations.JdbcTypeCode.class);
            }
            // TEXT (criterion/proof do enquadramento, B5/B8): texto livre sem o
            // length de um VARCHAR para conferir -- mesmo precedente do
            // MigrationConsistencyTest para o billed_customer_snapshot.
            return sqlType.equals("VARCHAR") || sqlType.equals("TEXT");
        }
        if (t == UUID.class) {
            return sqlType.equals("UUID");
        }
        if (t == int.class || t == Integer.class) {
            return sqlType.equals("INT");
        }
        if (t == LocalDate.class) {
            return sqlType.equals("DATE");
        }
        if (t == boolean.class || t == Boolean.class) {
            return sqlType.equals("BOOLEAN");
        }
        return false;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("receivableEntities")
    @DisplayName("recebiveis e linhas re-tipados (F3) batem com V4_1 + V4_14 + secao 4 da V4_4, com tipo e nulabilidade")
    void receivableColumnsMatchTheFusedSchema(Class<? extends TenantAwareEntity> entity) {
        String table = entity.getAnnotation(Table.class).name();
        Map<String, SqlColumn> columns = fusedReceivables.get(table);
        assertThat(columns).as("tabela %s", table).isNotNull();

        Set<String> mapped = new LinkedHashSet<>();
        for (Field f : entity.getDeclaredFields()) {
            Column c = f.getAnnotation(Column.class);
            if (c == null) {
                continue;
            }
            mapped.add(c.name());
            SqlColumn col = columns.get(c.name());
            assertThat(col).as("%s.%s existe no schema fundido", table, c.name()).isNotNull();
            assertThat(receivableTypeMatches(f, c, col)).as("%s.%s: tipo Java %s x SQL %s", table, c.name(),
                    f.getType().getSimpleName(), col).isTrue();
            assertThat(!c.nullable()).as("%s.%s: nullable=false deve coincidir com NOT NULL", table, c.name())
                    .isEqualTo(col.notNull());
        }
        Set<String> sqlOnly = new LinkedHashSet<>(columns.keySet());
        sqlOnly.removeAll(mapped);
        sqlOnly.removeAll(INHERITED);
        assertThat(sqlOnly).as("colunas do schema fundido sem mapeamento em %s", entity.getSimpleName()).isEmpty();
    }

    private static boolean receivableTypeMatches(Field f, Column c, SqlColumn col) {
        Class<?> t = f.getType();
        if (t == String.class) {
            if (col.type().equals("JSONB")) {
                return f.isAnnotationPresent(org.hibernate.annotations.JdbcTypeCode.class);
            }
            if (col.type().equals("TEXT")) {
                return true;
            }
            return col.type().equals("VARCHAR") && c.length() == col.length();
        }
        if (t == UUID.class) {
            return col.type().equals("UUID");
        }
        if (t == int.class || t == Integer.class) {
            return col.type().equals("INT");
        }
        if (t == LocalDate.class) {
            return col.type().equals("DATE");
        }
        if (t == boolean.class || t == Boolean.class) {
            return col.type().equals("BOOLEAN");
        }
        if (t == BigDecimal.class) {
            return col.type().equals("NUMERIC") && c.precision() == col.precision() && c.scale() == col.scale();
        }
        return false;
    }

    @Test
    @DisplayName("modelos de conta: sem sobreposicao de vigencia e uma so versao por numero (padrao das tarifas)")
    void templateVersionInvariants() {
        assertThat(sql).contains("EXCLUDE USING gist (tenant_id WITH =, template_id WITH =, daterange(valid_from, valid_to, '[]') WITH &&)");
        assertThat(sql).contains("CREATE UNIQUE INDEX idx_bill_template_versions_no ON bill_template_versions(tenant_id, template_id, version_no)");
        assertThat(sql).contains("FOREIGN KEY (tenant_id, template_id) REFERENCES bill_templates(tenant_id, id)");
    }

    @Test
    @DisplayName("DAD-15: bills ganha as tres colunas, o par cliente/snapshot anda junto e a FK aponta para customers")
    void billSnapshotColumns() {
        assertThat(sql).contains("ALTER TABLE bills ADD COLUMN IF NOT EXISTS parameter_version_ids JSONB;");
        assertThat(sql).contains("ALTER TABLE bills ADD COLUMN IF NOT EXISTS billed_customer_id UUID;");
        assertThat(sql).contains("ALTER TABLE bills ADD COLUMN IF NOT EXISTS billed_customer_snapshot TEXT;");
        assertThat(sql).contains("CHECK ((billed_customer_id IS NULL) = (billed_customer_snapshot IS NULL))");
        assertThat(sql).contains("FOREIGN KEY (tenant_id, billed_customer_id) REFERENCES customers(tenant_id, id)");
    }

    @Test
    @DisplayName("DAD-15: o snapshot cifrado e OMIT no audit_log e classificado como ID (nome/documento/endereco)")
    void snapshotNeverReachesTheAuditLog() {
        assertThat(sql).contains("('bills', 'billed_customer_snapshot', 'OMIT',");
        assertThat(sql).contains("INSERT INTO public.audit_redaction_rule (table_name, column_name, mode, reason)");
        assertThat(sql).contains("('bills', 'billed_customer_snapshot', 'ID',");
        assertThat(sql).contains("INSERT INTO public.data_classification (table_name, column_name, class, reason)");
    }

    @Test
    @DisplayName("B1: o versionamento de consumo saiu da V4_4 (ponteiro para a V5_6 do metering, nenhum ALTER na tabela)")
    void consumptionVersioningMovedToMeteringV56() {
        assertThat(sql).contains("V5_6__consumption_record_versioning.sql");
        assertThat(sql).doesNotContain("ALTER TABLE consumption_records");
        assertThat(sql).doesNotContain("CREATE UNIQUE INDEX idx_consumption_unit_period_current");
    }

    @Test
    @DisplayName("F3/DAD-20: CHECKs chk_drec_*/chk_crec_* e FKs para os catalogos estao na V4_4 fundida")
    void f3ChecksAndCatalogForeignKeys() {
        assertThat(sql).contains("chk_drec_source_type");
        assertThat(sql).contains("chk_drec_service_order");
        assertThat(sql).contains("chk_drec_legacy_reference");
        assertThat(sql).contains("chk_drec_financing_term");
        assertThat(sql).contains("chk_crec_source_type");
        assertThat(sql).contains("chk_crec_service_order");
        assertThat(sql).contains("chk_crec_legacy_reference");
        assertThat(sql).contains("chk_crec_financing_term");
        assertThat(sql).contains("FOREIGN KEY (tenant_id, debit_type_id) REFERENCES debit_types(tenant_id, id)");
        assertThat(sql).contains("FOREIGN KEY (tenant_id, credit_origin_id) REFERENCES credit_origins(tenant_id, id)");
        assertThat(sql).contains("ALTER TABLE debit_receivables DROP COLUMN IF EXISTS type_code;");
        assertThat(sql).contains("ALTER TABLE debit_receivables DROP COLUMN IF EXISTS generates_bill;");
        assertThat(sql).contains("ALTER TABLE credit_receivables DROP COLUMN IF EXISTS origin;");
        assertThat(sql).contains("ALTER TABLE bill_debits DROP COLUMN IF EXISTS type_code;");
        assertThat(sql).contains("ALTER TABLE bill_credits DROP COLUMN IF EXISTS origin;");
    }

    @Test
    @DisplayName("B6: cada tabela da F3 tem a guarda de linha legada nao mapeada antes da obrigatoriedade")
    void f3LegacyGuards() {
        assertThat(sql).contains("RAISE EXCEPTION 'V4_4 secao 4 (F3/B6): debit_receivables tem linhas legadas nao mapeadas");
        assertThat(sql).contains("RAISE EXCEPTION 'V4_4 secao 4 (F3/B6): credit_receivables tem linhas legadas nao mapeadas");
        assertThat(sql).contains("RAISE EXCEPTION 'V4_4 secao 4 (F3/B6): bill_debits tem linhas legadas nao mapeadas");
        assertThat(sql).contains("RAISE EXCEPTION 'V4_4 secao 4 (F3/B6): bill_credits tem linhas legadas nao mapeadas");
    }

    @Test
    @DisplayName("B2-DDL: fixed_part_value entra em bills e os CHECKs vigentes do total sao substituidos com a TFDI na formula")
    void fixedPartAndTotalChecks() {
        assertThat(sql).contains("ALTER TABLE bills ADD COLUMN IF NOT EXISTS fixed_part_value NUMERIC(14,2) NOT NULL DEFAULT 0;");
        assertThat(sql).contains("ALTER TABLE bills DROP CONSTRAINT IF EXISTS chk_bills_total_with_withholding_tax;");
        assertThat(sql).contains("pg_get_constraintdef(oid) LIKE '%credits_value <=%'");
        assertThat(sql).contains("ADD CONSTRAINT chk_bills_total_with_withholding_tax");
        assertThat(sql).contains("CHECK (total_value = water_value + sewer_value + fixed_part_value + debits_value - credits_value - taxes_value)");
        assertThat(sql).contains("CHECK (credits_value <= water_value + sewer_value + fixed_part_value + debits_value)");
    }

    @Test
    @DisplayName("B8: criterion e proof do enquadramento tem classificacao (SOC/SENS) e redacao REDACT")
    void optionalEnrollmentClassificationAndRedaction() {
        assertThat(sql).contains("('optional_category_enrollments', 'criterion', 'SOC',");
        assertThat(sql).contains("('optional_category_enrollments', 'proof', 'SENS',");
        assertThat(sql).contains("('optional_category_enrollments', 'criterion', 'REDACT',");
        assertThat(sql).contains("('optional_category_enrollments', 'proof', 'REDACT',");
    }

    @Test
    @DisplayName("parametros fundidos: holder (F2), partial_period_rule/after_cap_rule (F4 bloco B) e chaves da F5 no catalogo")
    void fusedParameterRegistrations() {
        assertThat(sql).contains("('bill.partial_period_rule', 'ENUM', 'b', '\"PROPORTIONAL\"'");
        assertThat(sql).contains("('meter.after_cap_rule', 'ENUM', NULL, NULL,");
        assertThat(sql).contains("('tariff.optional_categories', 'TABLE', 'd', NULL,");
        assertThat(sql).contains("('benefit.expiry_notice_days', 'INT', 'd', NULL,");
        assertThat(sql).contains("('bill.hold_on_anomaly', 'BOOLEAN', NULL, NULL,");
        assertThat(sql).contains("('fee.fine_mode', 'ENUM', NULL, NULL,");
        assertThat(sql).contains("('tariff.fixed_part_model', 'ENUM', NULL, NULL,");
    }

    @Test
    @DisplayName("bill.holder_name_display entra no catalogo: ENUM, classe b, padrao USER_HOLDER_ONLY, so os dois valores")
    void holderNameDisplayParameter() {
        assertThat(sql).contains("('bill.holder_name_display', 'ENUM', 'b', '\"USER_HOLDER_ONLY\"'");
        assertThat(sql).contains("'[\"USER_HOLDER_ONLY\", \"USER_HOLDER_AND_OWNER\"]'");
    }

    @Test
    @DisplayName("nenhum dado de negocio semeado: os unicos INSERTs sao catalogo, classificacao e redacao")
    void noBusinessSeedData() {
        assertThat(sql).doesNotContainPattern("(?i)INSERT\\s+INTO\\s+(bills|bill_templates|bill_template_versions|consumption_records)\\b");
        assertThat(sql).doesNotContainPattern("(?i)\\bUPDATE\\s+\\w+\\s+SET\\b");
        assertThat(sql).doesNotContainPattern("(?i)\\bDELETE\\s+FROM\\b");
    }

    @Test
    @DisplayName("higiene do SQL: so ASCII e nenhum CHAR(n)")
    void sqlHygiene() {
        assertThat(sql.chars().allMatch(ch -> ch < 128)).as("so ASCII").isTrue();
        assertThat(sql).doesNotContainPattern("(?<!VAR)CHAR\\(");
    }
}
