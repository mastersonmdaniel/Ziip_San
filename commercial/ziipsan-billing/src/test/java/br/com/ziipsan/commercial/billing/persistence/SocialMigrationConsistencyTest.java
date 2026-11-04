package br.com.ziipsan.commercial.billing.persistence;

import br.com.ziipsan.commercial.billing.domain.BillAuditBatchEntity;
import br.com.ziipsan.commercial.billing.domain.SocialBaseImportEntity;
import br.com.ziipsan.commercial.billing.domain.SocialBaseRecordEntity;
import br.com.ziipsan.commercial.billing.domain.SocialTariffEndReasonEntity;
import br.com.ziipsan.commercial.billing.domain.SocialTariffEnrollmentEntity;
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
 * Guardas das migrations {@code V4_2} e {@code V4_6} que nao precisam de banco, no mesmo
 * modelo do {@code MigrationConsistencyTest} da V4_1: cada coluna mapeada existe no SQL
 * com tipo, tamanho, precisao e nulabilidade compativeis; toda tabela nova e registrada
 * para RLS; FKs compostas tem o UNIQUE no pai; nenhum dado e semeado; e os invariantes
 * da P1.5/P1.16 estao no banco (uma inscricao ativa por familia e por unidade, CHECKs de
 * situacao, CHECK do total com tributo retido travado, lote de auditoria).
 */
class SocialMigrationConsistencyTest {

    private static final List<Class<? extends TenantAwareEntity>> ENTITIES = List.of(
            SocialTariffEndReasonEntity.class, SocialTariffEnrollmentEntity.class,
            SocialBaseImportEntity.class, SocialBaseRecordEntity.class, BillAuditBatchEntity.class);

    private static final Set<String> INHERITED = Set.of("id", "tenant_id", "created_at", "updated_at", "created_by",
            "updated_by", "version", "tenant_schema");

    private static final Set<String> EXTERNAL_PARENTS = Set.of("consumer_units", "customers");

    record SqlColumn(String name, String type, Integer length, Integer precision, Integer scale, boolean notNull) {
    }

    private static String v42;
    private static String v46;
    private static String v91;
    private static String v413;
    private static Map<String, Map<String, SqlColumn>> tables;

    @BeforeAll
    static void load() throws IOException {
        v42 = read("/db/migration/tenant/V4_2__social_tariff_enrollments.sql");
        v46 = read("/db/migration/tenant/V4_6__bill_total_tax_check_and_batch_audit.sql");
        v91 = read("/db/migration/tenant/V9_1__service_order_engine.sql");
        v413 = read("/db/migration/tenant/V4_13__social_tariff_service_order_not_null.sql");
        tables = parseTables(v42);
        tables.putAll(parseTables(v46));
        applySetNotNull(v413);
    }

    /**
     * P1.3 (R1): a V4_13 aplica SET NOT NULL sobre a coluna criada NULL na V4_2. O modelo do
     * teste acompanha a evolução do esquema: sem este passo, a entidade (nullable=false desde
     * a P1.3) divergiria do SQL parseado da V4_2.
     */
    private static void applySetNotNull(String sql) {
        Matcher m = Pattern.compile("ALTER TABLE (\\w+) ALTER COLUMN (\\w+) SET NOT NULL").matcher(sql);
        while (m.find()) {
            Map<String, SqlColumn> columns = tables.get(m.group(1));
            assertThat(columns).as("tabela %s do SET NOT NULL", m.group(1)).isNotNull();
            SqlColumn col = columns.get(m.group(2));
            assertThat(col).as("coluna %s.%s do SET NOT NULL", m.group(1), m.group(2)).isNotNull();
            columns.put(m.group(2), new SqlColumn(col.name(), col.type(), col.length(), col.precision(),
                    col.scale(), true));
        }
    }

    private static String read(String resource) throws IOException {
        try (InputStream in = SocialMigrationConsistencyTest.class.getResourceAsStream(resource)) {
            assertThat(in).as("migration no classpath: " + resource).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    private static Map<String, Map<String, SqlColumn>> parseTables(String text) {
        Map<String, Map<String, SqlColumn>> result = new LinkedHashMap<>();
        Matcher header = Pattern.compile("CREATE TABLE IF NOT EXISTS (\\w+) \\(\\n(.*?)\\n\\);", Pattern.DOTALL).matcher(text);
        Pattern column = Pattern.compile("^ {4}([a-z][a-z0-9_]*) +(UUID|VARCHAR\\((\\d+)\\)|INT|SMALLINT|DATE|BOOLEAN|NUMERIC\\((\\d+),(\\d+)\\)|TIMESTAMPTZ|JSONB)(?=[ ,]|$)(.*)$");
        while (header.find()) {
            Map<String, SqlColumn> columns = new LinkedHashMap<>();
            for (String line : header.group(2).split("\n")) {
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

    @Test
    @DisplayName("a V4_2 cria 4 tabelas e a V4_6 cria 1, mapeadas por 5 entidades distintas")
    void fiveTables() {
        assertThat(tables).hasSize(5);
        assertThat(ENTITIES.stream().map(c -> c.getAnnotation(Table.class).name()).collect(Collectors.toSet()))
                .isEqualTo(tables.keySet());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("entities")
    @DisplayName("cada coluna mapeada existe no banco com tipo, tamanho, precisao e nulabilidade compativeis")
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
            assertThat(typeMatches(f, c, col)).as("%s.%s: tipo Java %s x SQL %s", table, c.name(), f.getType().getSimpleName(), col).isTrue();
            // Integracao P1-B: a V4_2 criou service_order_id sem NOT NULL e a
            // V9_1 acrescenta so o FK. O NOT NULL obrigatorio chegou na
            // P1.3/V4_13 (a V3_6 reservada no plano já existia, P0.16), junto
            // com o roteamento do registro direto por OS interna — o modelo
            // do teste aplica o SET NOT NULL da V4_13 em load().
            // Conferido pelos testes serviceOrderIdFkFromP131 e serviceOrderIdNotNullFromP13.
            assertThat(!c.nullable()).as("%s.%s: nullable=false deve coincidir com NOT NULL", table, c.name())
                    .isEqualTo(col.notNull());
        }

        Set<String> sqlOnly = new LinkedHashSet<>(columns.keySet());
        sqlOnly.removeAll(mapped);
        sqlOnly.removeAll(INHERITED);
        assertThat(sqlOnly).as("colunas do SQL sem mapeamento em %s", entity.getSimpleName()).isEmpty();
    }

    private static boolean typeMatches(Field f, Column c, SqlColumn col) {
        Class<?> t = f.getType();
        if (t == String.class) {
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
        if (t == BigDecimal.class) {
            return col.type().equals("NUMERIC") && c.precision() == col.precision() && c.scale() == col.scale();
        }
        return false;
    }

    @Test
    @DisplayName("toda tabela nova e registrada com register_tenant_table (RLS forcada, policy, auditoria), e so elas")
    void everyTableIsRegistered() {
        Set<String> registered = new LinkedHashSet<>();
        String all = v42 + "\n" + v46;
        Matcher m = Pattern.compile("SELECT register_tenant_table\\('(\\w+)'\\);").matcher(all);
        while (m.find()) {
            registered.add(m.group(1));
        }
        assertThat(registered).isEqualTo(tables.keySet());
    }

    @Test
    @DisplayName("FK composta (tenant_id, id): o pai tem UNIQUE (tenant_id, id) criado antes da FK")
    void compositeForeignKeysHaveUniqueParents() {
        String all = v42 + "\n" + v46;
        Matcher fk = Pattern.compile("ALTER TABLE (\\w+) ADD CONSTRAINT \\w+\\s+FOREIGN KEY \\(tenant_id, \\w+\\) REFERENCES (\\w+)\\(tenant_id, id\\)").matcher(all);
        int count = 0;
        while (fk.find()) {
            count++;
            String child = fk.group(1);
            String parent = fk.group(2);
            if (EXTERNAL_PARENTS.contains(parent)) {
                continue;
            }
            assertThat(tables).as("pai %s de %s", parent, child).containsKey(parent);
            Matcher unique = Pattern.compile("ALTER TABLE " + parent + " ADD CONSTRAINT \\w+ UNIQUE \\(tenant_id, id\\);").matcher(all);
            assertThat(unique.find()).as("UNIQUE (tenant_id, id) em %s", parent).isTrue();
            assertThat(unique.start()).as("UNIQUE de %s antes da FK de %s", parent, child).isLessThan(fk.start());
        }
        // import -> imports, record -> enrollment, bill -> batch (as demais FKs apontam
        // para consumer_units/customers, externos, ou para (tenant_id, code) do catalogo).
        assertThat(count).as("FKs compostas (tenant_id, id) encontradas").isGreaterThanOrEqualTo(3);
        assertThat(all).contains("FOREIGN KEY (tenant_id, end_reason_code) REFERENCES social_tariff_end_reasons(tenant_id, code)");
    }

    @Test
    @DisplayName("uma inscricao ativa por familia e por unidade (indices unicos parciais)")
    void oneActiveEnrollmentPerFamilyAndUnit() {
        assertThat(v42).contains("CREATE UNIQUE INDEX idx_social_enroll_family_active ON social_tariff_enrollments(tenant_id, family_code_hash)");
        assertThat(v42).contains("CREATE UNIQUE INDEX idx_social_enroll_unit_active ON social_tariff_enrollments(tenant_id, unit_id)");
        assertThat(v42).contains("WHERE status IN ('ACTIVE', 'LOSS_NOTICE', 'GRACE')");
    }

    @Test
    @DisplayName("situacoes, bases e origens da inscricao sao exatamente as do plano")
    void enrollmentDomainsMatchThePlan() {
        assertThat(v42).contains("CHECK (status IN ('ACTIVE', 'LOSS_NOTICE', 'GRACE', 'ENDED'))");
        assertThat(v42).contains("CHECK (eligibility_basis IN ('CADUNICO', 'BPC', 'LOCAL_PROGRAM'))");
        assertThat(v42).contains("CHECK (enrollment_source IN ('OFFICIAL_LIST', 'MANUAL_DOCUMENTS'))");
        assertThat(v42).contains("CHECK (list_source IN ('REGULATOR_LIST', 'MUNICIPAL_BASE', 'CONECTA_API'))");
        assertThat(v42).contains("CHECK ((status = 'ENDED') = (end_reason_code IS NOT NULL))");
        assertThat(v42).contains("CHECK ((eligibility_basis = 'CADUNICO') = (cadunico_updated_on IS NOT NULL))");
    }

    @Test
    @DisplayName("nenhuma coluna de CPF, NIS, renda ou adimplencia nas tabelas sociais")
    void noSensitiveOrPaymentColumns() {
        for (String table : tables.keySet()) {
            if (!table.startsWith("social_")) {
                continue;
            }
            for (String column : tables.get(table).keySet()) {
                assertThat(column).doesNotContain("cpf").doesNotContain("nis").doesNotContain("income")
                        .doesNotContain("payment").doesNotContain("adimpl");
            }
        }
        assertThat(v42).contains("family_code_hash VARCHAR(64)");
        assertThat(v42).contains("person_document_hash VARCHAR(64)");
    }

    @Test
    @DisplayName("service_order_id existe sem FK e sem NOT NULL na V4_2 (o FK chega na P1.31; o NOT NULL, na P1.3/V4_13)")
    void serviceOrderIdDeferredToP131() {
        assertThat(v42).contains("service_order_id UUID,");
        assertThat(v42).doesNotContain("REFERENCES service_orders");
        assertThat(v42).doesNotContain("service_order_id UUID NOT NULL");
    }

    @Test
    @DisplayName("P1.3 (V4_13, R1): service_order_id da inscricao vira NOT NULL (a V3_6 reservada já existia, P0.16)")
    void serviceOrderIdNotNullFromP13() {
        assertThat(v413).contains(
                "ALTER TABLE social_tariff_enrollments ALTER COLUMN service_order_id SET NOT NULL");
        assertThat(tables.get("social_tariff_enrollments").get("service_order_id").notNull())
                .as("modelo do teste após aplicar a V4_13").isTrue();
    }

    @Test
    @DisplayName("P1.31 (V9_1): service_order_id da inscricao ganha FK (o NOT NULL so chega na P1.3/V4_13)")
    void serviceOrderIdFkFromP131() {
        assertThat(v91).contains("ALTER TABLE social_tariff_enrollments ADD CONSTRAINT fk_social_enroll_order");
        assertThat(v91).contains("FOREIGN KEY (tenant_id, service_order_id) REFERENCES service_orders(tenant_id, id)");
        assertThat(v91).doesNotContain("ALTER TABLE social_tariff_enrollments ALTER COLUMN service_order_id SET NOT NULL");
        assertThat(v91).contains("ALTER TABLE service_orders ADD CONSTRAINT uq_service_orders_tenant_id UNIQUE (tenant_id, id)");
    }

    @Test
    @DisplayName("V4_6: CHECK nomeado do total com tributo retido e tratamento travado em WITHHOLDING_ONLY")
    void billTotalTaxCheck() {
        assertThat(v46).contains("tax_treatment VARCHAR(20) NOT NULL DEFAULT 'WITHHOLDING_ONLY'");
        assertThat(v46).contains("CONSTRAINT chk_bills_tax_treatment");
        assertThat(v46).contains("CHECK (tax_treatment IN ('WITHHOLDING_ONLY'))");
        assertThat(v46).contains("CONSTRAINT chk_bills_total_with_withholding_tax");
        assertThat(v46).contains("CHECK (total_value = water_value + sewer_value + debits_value - credits_value - taxes_value)");
        assertThat(v46).contains("audit_batch_id UUID");
        assertThat(v46).contains("FOREIGN KEY (tenant_id, audit_batch_id) REFERENCES bill_audit_batches(tenant_id, id)");
    }

    @Test
    @DisplayName("nenhum dado semeado: o unico INSERT e a redacao de auditoria dos indices cegos")
    void noSeedData() {
        assertThat(v42).doesNotContainPattern("(?i)\\bINSERT\\s+INTO\\s+social_\\w+\\b");
        assertThat(v42).doesNotContainPattern("(?i)\\bUPDATE\\s+\\w+\\s+SET\\b");
        assertThat(v42).doesNotContainPattern("(?i)\\bDELETE\\s+FROM\\b");
        assertThat(v46).doesNotContainPattern("(?i)\\bINSERT\\s+INTO\\b");
        assertThat(v42).contains("INSERT INTO public.audit_redaction_rule");
    }

    @Test
    @DisplayName("higiene do SQL: so ASCII, sem CHAR(n) (so VARCHAR)")
    void sqlHygiene() {
        assertThat(v42.chars().allMatch(ch -> ch < 128)).as("V4_2 so ASCII").isTrue();
        assertThat(v46.chars().allMatch(ch -> ch < 128)).as("V4_6 so ASCII").isTrue();
        assertThat(v42).doesNotContainPattern("(?<!VAR)CHAR\\(");
        assertThat(v46).doesNotContainPattern("(?<!VAR)CHAR\\(");
    }
}
