package br.com.ziipsan.commercial.billing.persistence;

import br.com.ziipsan.commercial.billing.bill.BillStatus;
import br.com.ziipsan.commercial.billing.domain.BillCreditEntity;
import br.com.ziipsan.commercial.billing.domain.BillDebitEntity;
import br.com.ziipsan.commercial.billing.domain.BillEntity;
import br.com.ziipsan.commercial.billing.domain.BillItemEntity;
import br.com.ziipsan.commercial.billing.domain.BillTaxEntity;
import br.com.ziipsan.commercial.billing.domain.CreditReceivableEntity;
import br.com.ziipsan.commercial.billing.domain.DebitReceivableEntity;
import br.com.ziipsan.commercial.billing.domain.TariffBlockEntity;
import br.com.ziipsan.commercial.billing.domain.TariffCategoryRuleEntity;
import br.com.ziipsan.commercial.billing.domain.TariffEntity;
import br.com.ziipsan.commercial.billing.domain.TariffSocialBandEntity;
import br.com.ziipsan.commercial.billing.domain.TariffVersionEntity;
import br.com.ziipsan.commercial.billing.domain.TaxWithholdingRateEntity;
import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guardas da migration {@code V4_1__create_billing_schema.sql} que NÃO precisam de banco.
 * A V4_6 entra aqui so pelos ALTERs em bills (a entidade evoluiu com ela); as tabelas
 * novas da V4_6 tem guarda propria em SocialMigrationConsistencyTest.
 *
 * <p>O projeto não permite rodar o banco de integração nesta fatia, e "build verde não prova nada"
 * (diário de bordo, seção 15): um nome de coluna errado numa entidade só quebraria no primeiro
 * deploy. Aqui o SQL é lido como texto e comparado com as entidades por reflexão: toda coluna mapeada
 * existe, com tipo, tamanho, precisão e nulabilidade compatíveis; toda coluna obrigatória do banco
 * está mapeada (senão o INSERT falharia); a lista de estados da conta e a do índice de idempotência
 * são iguais às do enum {@link BillStatus}; toda tabela é registrada para RLS; FKs compostas têm o
 * UNIQUE no pai criado antes; e a migration não semeia dado nenhum.
 */
class MigrationConsistencyTest {

    private static final List<Class<? extends TenantAwareEntity>> ENTITIES = List.of(
            TariffEntity.class, TariffVersionEntity.class, TariffCategoryRuleEntity.class, TariffBlockEntity.class,
            TariffSocialBandEntity.class, TaxWithholdingRateEntity.class, DebitReceivableEntity.class,
            CreditReceivableEntity.class, BillEntity.class, BillItemEntity.class, BillDebitEntity.class,
            BillCreditEntity.class, BillTaxEntity.class);

    /** Colunas que vêm de BaseEntity/TenantAwareEntity ou de register_tenant_table(). */
    private static final Set<String> INHERITED = Set.of("id", "tenant_id", "created_at", "updated_at", "created_by",
            "updated_by", "version", "tenant_schema");

    /** Pais de FK que pertencem a outros módulos (registry, V3_1), todos com UNIQUE (tenant_id, id). */
    private static final Set<String> EXTERNAL_PARENTS = Set.of("consumer_units", "categories", "subcategories");

    record SqlColumn(String name, String type, Integer length, Integer precision, Integer scale, boolean notNull) {
    }

    private static String sql;
    private static Map<String, Map<String, SqlColumn>> tables;

    @BeforeAll
    static void load() throws IOException {
        try (InputStream in = MigrationConsistencyTest.class.getResourceAsStream("/db/migration/tenant/V4_1__create_billing_schema.sql")) {
            assertThat(in).as("migration no classpath").isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
        tables = parseTables(sql);
        // P1.16 (V4_6): a entidade bills ganhou tax_treatment e audit_batch_id por ALTER
        // TABLE. O mapa da bills passa a refletir o schema final, nao so o da V4_1, para a
        // guarda continuar valendo (coluna mapeada sem coluna no banco continua falhando).
        try (InputStream in = MigrationConsistencyTest.class.getResourceAsStream("/db/migration/tenant/V4_6__bill_total_tax_check_and_batch_audit.sql")) {
            assertThat(in).as("migration V4_6 no classpath").isNotNull();
            String v46 = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
            Matcher alter = Pattern.compile("ALTER TABLE bills ADD COLUMN IF NOT EXISTS ([a-z][a-z0-9_]*) +(UUID|VARCHAR\\((\\d+)\\))([^;]*);").matcher(v46);
            while (alter.find()) {
                String type = alter.group(2).replaceAll("\\(.*\\)", "");
                tables.get("bills").put(alter.group(1), new SqlColumn(alter.group(1), type,
                        alter.group(3) == null ? null : Integer.valueOf(alter.group(3)),
                        null, null, alter.group(4).contains("NOT NULL")));
            }
        }
        // P2.3 (V4_14): bills e bill_items foram RECRIADAS particionadas (a V4_6 nao pode
        // ser editada: Flyway recusa checksum alterado). O mapa das duas tabelas passa a
        // refletir o CREATE TABLE da V4_14, e as demais filhas ganham reference_month
        // pelos ALTERs da V4_14 (o SET NOT NULL posterior e aplicado em seguida, pois a
        // coluna nasce NULL para o backfill). As demais guardas continuam lendo a V4_1.
        try (InputStream in = MigrationConsistencyTest.class.getResourceAsStream("/db/migration/tenant/V4_14__bill_partition_and_batch_audit.sql")) {
            assertThat(in).as("migration V4_14 no classpath").isNotNull();
            String v414 = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            tables.put("bills", parseCreateTable(v414, "bills"));
            tables.put("bill_items", parseCreateTable(v414, "bill_items"));
            Matcher alter14 = Pattern.compile("ALTER TABLE (bill_debits|bill_credits|bill_taxes) ADD COLUMN IF NOT EXISTS ([a-z][a-z0-9_]*) +(DATE)([^;]*);").matcher(v414);
            while (alter14.find()) {
                tables.get(alter14.group(1)).put(alter14.group(2), new SqlColumn(alter14.group(2),
                        alter14.group(3), null, null, null, alter14.group(4).contains("NOT NULL")));
            }
            Matcher notNull = Pattern.compile("ALTER TABLE (bill_debits|bill_credits|bill_taxes) ALTER COLUMN reference_month SET NOT NULL;").matcher(v414);
            while (notNull.find()) {
                SqlColumn old = tables.get(notNull.group(1)).get("reference_month");
                tables.get(notNull.group(1)).put("reference_month",
                        new SqlColumn("reference_month", old.type(), old.length(), old.precision(), old.scale(), true));
            }
        }
        // P2.5 (V4_4): bills ganha parameter_version_ids (JSONB), billed_customer_id (UUID)
        // e billed_customer_snapshot (TEXT, o ciphertext do snapshot DAD-15) por ALTER
        // TABLE. As tabelas novas da V4_4 (modelos de conta) tem guarda propria em
        // BillAccountMigrationConsistencyTest, como as da V4_6 na Social.
        // Correcao B2-DDL (revisao cruzada da P2.5): a V4_4 fundida tambem acrescenta
        // fixed_part_value NUMERIC(14,2) NOT NULL (TFDI, secao 5), que BillEntity mapeia
        // como BigDecimal(14,2) -- a regex de ALTER passa a aceitar NUMERIC(p,s) e a
        // guardar precisao/escala, senao a coluna passaria invisivel por esta guarda.
        try (InputStream in = MigrationConsistencyTest.class.getResourceAsStream("/db/migration/tenant/V4_4__bill_account_models_and_versioning.sql")) {
            assertThat(in).as("migration V4_4 no classpath").isNotNull();
            String v44 = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
            Matcher alter44 = Pattern.compile("ALTER TABLE bills ADD COLUMN IF NOT EXISTS ([a-z][a-z0-9_]*) +(UUID|TEXT|JSONB|NUMERIC\\((\\d+),(\\d+)\\))([^;]*);").matcher(v44);
            while (alter44.find()) {
                String type = alter44.group(2).replaceAll("\\(.*\\)", "");
                tables.get("bills").put(alter44.group(1), new SqlColumn(alter44.group(1), type,
                        null,
                        alter44.group(3) == null ? null : Integer.valueOf(alter44.group(3)),
                        alter44.group(4) == null ? null : Integer.valueOf(alter44.group(4)),
                        alter44.group(5).contains("NOT NULL")));
            }
        }
    }

    /** Colunas do CREATE TABLE particionado da V4_14 (dentro do DO, indentacao maior). */
    private static Map<String, SqlColumn> parseCreateTable(String text, String table) {
        Matcher header = Pattern.compile("CREATE TABLE " + table + " \\((.*?)\n\\s*\\) PARTITION BY RANGE", Pattern.DOTALL).matcher(text);
        assertThat(header.find()).as("CREATE TABLE %s na V4_14", table).isTrue();
        Map<String, SqlColumn> columns = new LinkedHashMap<>();
        Pattern column = Pattern.compile("^\\s+([a-z][a-z0-9_]*) +(UUID|VARCHAR\\((\\d+)\\)|INT|SMALLINT|DATE|BOOLEAN|NUMERIC\\((\\d+),(\\d+)\\)|TIMESTAMPTZ|JSONB)(?=[ ,]|$)(.*)$", Pattern.MULTILINE);
        for (String line : header.group(1).split("\n")) {
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
        assertThat(columns).as("colunas de %s na V4_14", table).isNotEmpty();
        return columns;
    }

    private static Map<String, Map<String, SqlColumn>> parseTables(String text) {
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

    static Stream<Class<? extends TenantAwareEntity>> entities() {
        return ENTITIES.stream();
    }

    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("o parser enxerga as 13 tabelas e as 13 entidades mapeiam 13 tabelas distintas")
    void thirteenTables() {
        assertThat(tables).hasSize(13);
        assertThat(ENTITIES.stream().map(c -> c.getAnnotation(Table.class).name()).collect(Collectors.toSet()))
                .isEqualTo(tables.keySet());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("entities")
    @DisplayName("cada coluna mapeada existe no banco com tipo, tamanho, precisão e nulabilidade compatíveis")
    void mappedColumnsMatchTheSchema(Class<? extends TenantAwareEntity> entity) {
        assertThat(entity.getAnnotation(Entity.class)).isNotNull();
        String table = entity.getAnnotation(Table.class).name();
        Map<String, SqlColumn> columns = tables.get(table);
        assertThat(columns).as("tabela %s", table).isNotNull();

        Set<String> mapped = new LinkedHashSet<>();
        for (Field f : entity.getDeclaredFields()) {
            Column c = f.getAnnotation(Column.class);
            if (c == null) {
                continue;
            }
            mapped.add(c.name());
            SqlColumn col = columns.get(c.name());
            assertThat(col).as("%s.%s existe no SQL", table, c.name()).isNotNull();
            assertThat(typeMatches(f, c, col)).as("%s.%s: tipo Java %s × SQL %s", table, c.name(), f.getType().getSimpleName(), col).isTrue();
            assertThat(!c.nullable()).as("%s.%s: nullable=false deve coincidir com NOT NULL", table, c.name()).isEqualTo(col.notNull());
        }

        Set<String> sqlOnly = new LinkedHashSet<>(columns.keySet());
        sqlOnly.removeAll(mapped);
        sqlOnly.removeAll(INHERITED);
        assertThat(sqlOnly).as("colunas do SQL sem mapeamento em %s (o INSERT ignoraria ou falharia)", entity.getSimpleName()).isEmpty();
    }

    private static boolean typeMatches(Field f, Column c, SqlColumn col) {
        Class<?> t = f.getType();
        if (t == String.class) {
            if (col.type().equals("JSONB")) {
                return f.isAnnotationPresent(org.hibernate.annotations.JdbcTypeCode.class);
            }
            if (col.type().equals("TEXT")) {
                // V4_4: billed_customer_snapshot guarda o ciphertext (base64 de tamanho
                // variavel); TEXT nao tem o length de um VARCHAR para conferir.
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
        if (t == short.class || t == Short.class) {
            return col.type().equals("SMALLINT");
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
    @DisplayName("jsonb mapeado como String leva @JdbcTypeCode(JSON); nenhuma entidade usa @Builder")
    void conventions() {
        for (Class<? extends TenantAwareEntity> e : ENTITIES) {
            for (Field f : e.getDeclaredFields()) {
                Column c = f.getAnnotation(Column.class);
                if (c != null && c.columnDefinition().equalsIgnoreCase("jsonb")) {
                    assertThat(f.isAnnotationPresent(org.hibernate.annotations.JdbcTypeCode.class)).as("%s.%s", e.getSimpleName(), f.getName()).isTrue();
                }
            }
            assertThat(e.getSuperclass()).isEqualTo(TenantAwareEntity.class);
            assertThat(Stream.of(e.getAnnotations()).map(a -> a.annotationType().getSimpleName()))
                    .as("sem @Builder em %s", e.getSimpleName()).doesNotContain("Builder");
        }
    }

    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("os estados da conta (CHECK de status e previous_status) são exatamente os do enum BillStatus")
    void statusCheckMatchesTheEnum() {
        Set<String> enumNames = Stream.of(BillStatus.values()).map(Enum::name).collect(Collectors.toSet());

        Matcher status = Pattern.compile("status VARCHAR\\(30\\) NOT NULL CHECK \\(status IN \\(([^)]*)\\)\\)").matcher(sql);
        assertThat(status.find()).isTrue();
        assertThat(quoted(status.group(1))).isEqualTo(enumNames);

        Matcher previous = Pattern.compile("previous_status VARCHAR\\(30\\) CHECK \\(previous_status IS NULL OR previous_status IN \\(([^)]*)\\)\\)").matcher(sql);
        assertThat(previous.find()).isTrue();
        assertThat(quoted(previous.group(1))).isEqualTo(enumNames);
    }

    @Test
    @DisplayName("o índice de idempotência exclui exatamente os estados que BillStatus.live() libera")
    void idempotencyIndexMatchesLiveStatuses() {
        Matcher index = Pattern.compile("CREATE UNIQUE INDEX idx_bills_unit_reference_live ON bills\\(tenant_id, unit_id, reference_month\\)\\s+WHERE status NOT IN \\(([^)]*)\\)").matcher(sql);
        assertThat(index.find()).as("índice único parcial de idempotência").isTrue();

        Set<String> notLive = Stream.of(BillStatus.values()).filter(s -> !s.live()).map(Enum::name).collect(Collectors.toSet());
        assertThat(quoted(index.group(1))).isEqualTo(notLive);
    }

    private static Set<String> quoted(String list) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = Pattern.compile("'([A-Z_]+)'").matcher(list);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("toda tabela criada é registrada com register_tenant_table (RLS forçada, policy, auditoria), e só elas")
    void everyTableIsRegistered() {
        Set<String> registered = new LinkedHashSet<>();
        Matcher m = Pattern.compile("SELECT register_tenant_table\\('(\\w+)'\\);").matcher(sql);
        while (m.find()) {
            registered.add(m.group(1));
        }
        assertThat(registered).isEqualTo(tables.keySet());
    }

    @Test
    @DisplayName("FK composta (tenant_id, id): o pai tem UNIQUE (tenant_id, id) criado antes da FK; pais de outros módulos são os esperados")
    void compositeForeignKeysHaveUniqueParents() {
        Matcher fk = Pattern.compile("ALTER TABLE (\\w+) ADD CONSTRAINT \\w+\\s+FOREIGN KEY \\(tenant_id, \\w+\\) REFERENCES (\\w+)\\(tenant_id, id\\)").matcher(sql);
        int count = 0;
        while (fk.find()) {
            count++;
            String child = fk.group(1);
            String parent = fk.group(2);
            if (EXTERNAL_PARENTS.contains(parent)) {
                continue;
            }
            assertThat(tables).as("pai %s de %s", parent, child).containsKey(parent);
            int parentCreated = sql.indexOf("CREATE TABLE IF NOT EXISTS " + parent + " (");
            Matcher unique = Pattern.compile("ALTER TABLE " + parent + " ADD CONSTRAINT \\w+ UNIQUE \\(tenant_id, id\\);").matcher(sql);
            assertThat(unique.find()).as("UNIQUE (tenant_id, id) em %s", parent).isTrue();
            assertThat(unique.start()).as("UNIQUE de %s antes da FK de %s", parent, child).isLessThan(fk.start());
            assertThat(parentCreated).as("%s criado antes de %s", parent, child).isLessThan(fk.start());
        }
        assertThat(count).as("FKs compostas encontradas").isGreaterThan(15);
        assertThat(sql).as("nenhuma FK simples que esqueça o tenant")
                .doesNotContainPattern("REFERENCES \\w+\\(id\\)");
    }

    @Test
    @DisplayName("nenhum dado semeado: a migration não tem INSERT, UPDATE nem DELETE (tarifa é política do tenant)")
    void noSeedData() {
        assertThat(sql).doesNotContainPattern("(?i)\\bINSERT\\s+INTO\\b");
        assertThat(sql).doesNotContainPattern("(?i)\\bUPDATE\\s+\\w+\\s+SET\\b");
        assertThat(sql).doesNotContainPattern("(?i)\\bDELETE\\s+FROM\\b");
    }

    @Test
    @DisplayName("higiene do SQL: só ASCII, sem CHAR(n) (só VARCHAR), sem tabela fora do padrão")
    void sqlHygiene() {
        assertThat(sql.chars().allMatch(ch -> ch < 128)).as("só ASCII").isTrue();
        assertThat(sql).doesNotContainPattern("(?<!VAR)CHAR\\(");
        assertThat(sql).doesNotContainPattern("(?i)\\bCREATE TABLE (?!IF NOT EXISTS)");
    }

    @Test
    @DisplayName("a idempotência (unidade, referência) e a não sobreposição de vigências estão no banco, não só no código")
    void databaseEnforcesTheKeyInvariants() {
        assertThat(sql).contains("idx_bills_unit_reference_live");
        assertThat(sql).contains("EXCLUDE USING gist (tenant_id WITH =, tariff_id WITH =, daterange(valid_from, valid_to, '[]') WITH &&)");
        assertThat(sql).contains("CHECK (total_value = water_value + sewer_value + debits_value - credits_value - taxes_value)");
        assertThat(sql).contains("CHECK (credits_value <= water_value + sewer_value + debits_value)");
    }
}
