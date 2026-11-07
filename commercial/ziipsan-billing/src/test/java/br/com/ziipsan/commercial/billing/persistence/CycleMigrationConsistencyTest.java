package br.com.ziipsan.commercial.billing.persistence;

import br.com.ziipsan.commercial.billing.cycle.BillingReferenceStatus;
import br.com.ziipsan.commercial.billing.cycle.ScheduleItemStatus;
import br.com.ziipsan.commercial.billing.domain.BillingGroupEntity;
import br.com.ziipsan.commercial.billing.domain.BillingReferenceEntity;
import br.com.ziipsan.commercial.billing.domain.CycleActivityEntity;
import br.com.ziipsan.commercial.billing.domain.CycleScheduleItemEntity;
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
import java.time.OffsetDateTime;
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
 * Guardas da migration {@code V4_3__billing_cycle_and_schedule.sql} (P2.5/F1) que nao
 * precisam de banco, no mesmo modelo do {@code MigrationConsistencyTest} da V4_1 e do
 * {@code SocialMigrationConsistencyTest} da V4_2/V4_6: cada coluna mapeada existe no
 * SQL com tipo, tamanho, precisao e nulabilidade compativeis; toda tabela nova e
 * registrada para RLS; FKs compostas tem o UNIQUE no pai criado antes; nenhum dado e
 * semeado (o catalogo de atividades do produto e carregado pelo servico); e as listas
 * de situacao do banco sao exatamente as dos enums do ciclo.
 */
class CycleMigrationConsistencyTest {

    private static final List<Class<? extends TenantAwareEntity>> ENTITIES = List.of(
            BillingGroupEntity.class, BillingReferenceEntity.class,
            CycleActivityEntity.class, CycleScheduleItemEntity.class);

    private static final Set<String> INHERITED = Set.of("id", "tenant_id", "created_at", "updated_at", "created_by",
            "updated_by", "version", "tenant_schema");

    record SqlColumn(String name, String type, Integer length, Integer precision, Integer scale, boolean notNull) {
    }

    private static String sql;
    private static Map<String, Map<String, SqlColumn>> tables;

    @BeforeAll
    static void load() throws IOException {
        try (InputStream in = CycleMigrationConsistencyTest.class
                .getResourceAsStream("/db/migration/tenant/V4_3__billing_cycle_and_schedule.sql")) {
            assertThat(in).as("migration V4_3 no classpath").isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
        tables = parseTables(sql);
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

    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("a V4_3 cria 4 tabelas e as 4 entidades mapeiam 4 tabelas distintas")
    void fourTables() {
        assertThat(tables).hasSize(4);
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
            assertThat(!c.nullable()).as("%s.%s: nullable=false deve coincidir com NOT NULL", table, c.name()).isEqualTo(col.notNull());
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
        if (t == short.class || t == Short.class) {
            return col.type().equals("SMALLINT");
        }
        if (t == LocalDate.class) {
            return col.type().equals("DATE");
        }
        if (t == OffsetDateTime.class) {
            return col.type().equals("TIMESTAMPTZ");
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
    @DisplayName("convencoes: toda entidade herda TenantAwareEntity e nenhuma usa @Builder")
    void conventions() {
        for (Class<? extends TenantAwareEntity> e : ENTITIES) {
            assertThat(e.getSuperclass()).isEqualTo(TenantAwareEntity.class);
            assertThat(Stream.of(e.getAnnotations()).map(a -> a.annotationType().getSimpleName()))
                    .as("sem @Builder em %s", e.getSimpleName()).doesNotContain("Builder");
        }
    }

    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("as situacoes da referencia e do item do cronograma sao exatamente as dos enums")
    void statusChecksMatchTheEnums() {
        Matcher reference = Pattern.compile("status VARCHAR\\(20\\) NOT NULL DEFAULT 'OPEN' CHECK \\(status IN \\(([^)]*)\\)\\)").matcher(sql);
        assertThat(reference.find()).isTrue();
        assertThat(quoted(reference.group(1)))
                .isEqualTo(Stream.of(BillingReferenceStatus.values()).map(Enum::name).collect(Collectors.toSet()));

        Matcher item = Pattern.compile("status VARCHAR\\(20\\) NOT NULL DEFAULT 'PENDING' CHECK \\(status IN \\(([^)]*)\\)\\)").matcher(sql);
        assertThat(item.find()).isTrue();
        assertThat(quoted(item.group(1)))
                .isEqualTo(Stream.of(ScheduleItemStatus.values()).map(Enum::name).collect(Collectors.toSet()));
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
    @DisplayName("toda tabela criada e registrada com register_tenant_table (RLS forcada, policy, auditoria), e so elas")
    void everyTableIsRegistered() {
        Set<String> registered = new LinkedHashSet<>();
        Matcher m = Pattern.compile("SELECT register_tenant_table\\('(\\w+)'\\);").matcher(sql);
        while (m.find()) {
            registered.add(m.group(1));
        }
        assertThat(registered).isEqualTo(tables.keySet());
    }

    @Test
    @DisplayName("FK composta (tenant_id, id): o pai tem UNIQUE (tenant_id, id) criado antes da FK")
    void compositeForeignKeysHaveUniqueParents() {
        Matcher fk = Pattern.compile("ALTER TABLE (\\w+) ADD CONSTRAINT \\w+\\s+FOREIGN KEY \\(tenant_id, \\w+\\) REFERENCES (\\w+)\\(tenant_id, id\\)").matcher(sql);
        int count = 0;
        while (fk.find()) {
            count++;
            String child = fk.group(1);
            String parent = fk.group(2);
            assertThat(tables).as("pai %s de %s", parent, child).containsKey(parent);
            int parentCreated = sql.indexOf("CREATE TABLE IF NOT EXISTS " + parent + " (");
            Matcher unique = Pattern.compile("ALTER TABLE " + parent + " ADD CONSTRAINT \\w+ UNIQUE \\(tenant_id, id\\);").matcher(sql);
            assertThat(unique.find()).as("UNIQUE (tenant_id, id) em %s", parent).isTrue();
            assertThat(unique.start()).as("UNIQUE de %s antes da FK de %s", parent, child).isLessThan(fk.start());
            // A auto-referencia do catalogo (precedente) nasce depois da propria tabela:
            // para ela, so o UNIQUE precisa vir antes da FK.
            if (!parent.equals(child)) {
                assertThat(parentCreated).as("%s criado antes de %s", parent, child).isLessThan(fk.start());
            }
        }
        // precedente (auto), referencia, grupo e atividade dos itens do cronograma.
        assertThat(count).as("FKs compostas encontradas").isEqualTo(4);
        assertThat(sql).as("nenhuma FK simples que esqueca o tenant")
                .doesNotContainPattern("REFERENCES \\w+\\(id\\)");
    }

    @Test
    @DisplayName("nenhum dado semeado: sem INSERT, UPDATE nem DELETE (o catalogo do produto e carregado pelo servico)")
    void noSeedData() {
        assertThat(sql).doesNotContainPattern("(?i)\\bINSERT\\s+INTO\\b");
        assertThat(sql).doesNotContainPattern("(?i)\\bUPDATE\\s+\\w+\\s+SET\\b");
        assertThat(sql).doesNotContainPattern("(?i)\\bDELETE\\s+FROM\\b");
    }

    @Test
    @DisplayName("higiene do SQL: so ASCII, sem CHAR(n) (so VARCHAR), sem tabela fora do padrao")
    void sqlHygiene() {
        assertThat(sql.chars().allMatch(ch -> ch < 128)).as("so ASCII").isTrue();
        assertThat(sql).doesNotContainPattern("(?<!VAR)CHAR\\(");
        assertThat(sql).doesNotContainPattern("(?i)\\bCREATE TABLE (?!IF NOT EXISTS)");
    }

    @Test
    @DisplayName("os invariantes do ciclo estao no banco: uma ocorrencia por (referencia, grupo, atividade), fechamento com instante, datas amarradas a situacao")
    void databaseEnforcesTheKeyInvariants() {
        assertThat(sql).contains("CREATE UNIQUE INDEX idx_cycle_schedule_unique "
                + "ON cycle_schedule_items(tenant_id, reference_id, group_id, activity_id, occurrence_no)");
        assertThat(sql).contains("CREATE UNIQUE INDEX idx_billing_references_month ON billing_references(tenant_id, reference_month)");
        assertThat(sql).contains("CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL))");
        assertThat(sql).contains("CHECK (EXTRACT(DAY FROM reference_month) = 1)");
        assertThat(sql).contains("(status = 'DONE' AND commanded_at IS NOT NULL AND done_at IS NOT NULL)");
        assertThat(sql).contains("CHECK (commanded_at IS NULL OR commanded_by IS NOT NULL)");
    }
}
