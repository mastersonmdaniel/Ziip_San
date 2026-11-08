package br.com.ziipsan.operations.metering;

import br.com.ziipsan.operations.metering.domain.ReadingImportBatch;
import br.com.ziipsan.operations.metering.domain.ReadingImportRecord;
import br.com.ziipsan.operations.metering.domain.ReadingTask;
import br.com.ziipsan.operations.metering.domain.ReadingVendor;
import br.com.ziipsan.operations.metering.rules.MeasurementKind;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mantem a migration {@code V5_3} (P2.7, leitura terceirizada) e as entidades
 * novas de acordo, sem banco -- o mesmo papel do {@code MigrationConsistencyTest}
 * da V5_1: enum fora do CHECK, coluna mapeada que nao existe (ou que sobrou) e
 * catalogo com semente aparecem aqui antes do PostgreSQL.
 */
class OutsourcedReadingMigrationConsistencyTest {

    private static final String SQL = readSql("/db/migration/tenant/V5_3__outsourced_reading_tasks_and_files.sql");

    private static String readSql(String path) {
        try (InputStream in = OutsourcedReadingMigrationConsistencyTest.class.getResourceAsStream(path)) {
            assertThat(in).as("migration no classpath: " + path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static <E extends Enum<E>> Set<String> names(Class<E> type) {
        return EnumSet.allOf(type).stream().map(Enum::name).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Map<String, List<Set<String>>> checkLists() {
        Map<String, List<Set<String>>> result = new LinkedHashMap<>();
        Matcher m = Pattern.compile("CHECK\\s*\\(\\s*(\\w+)\\s+IN\\s*\\(([^)]*)\\)\\s*\\)").matcher(SQL);
        while (m.find()) {
            Set<String> values = Arrays.stream(m.group(2).split(","))
                    .map(v -> v.trim().replace("'", ""))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            result.computeIfAbsent(m.group(1), k -> new ArrayList<>()).add(values);
        }
        return result;
    }

    @Test
    @DisplayName("os CHECKs de status e de tipo listam exatamente os valores dos enums do codigo")
    void checkConstraintsMatchTheEnums() {
        Map<String, List<Set<String>>> checks = checkLists();

        assertThat(checks.get("status")).containsExactlyInAnyOrder(
                names(ReadingTask.Status.class),
                names(ReadingImportBatch.Status.class),
                names(ReadingImportRecord.Status.class));
        assertThat(checks.get("kind")).containsExactly(names(MeasurementKind.class));
    }

    @Test
    @DisplayName("os carimbos da tarefa sao amarrados ao status: EXPORTED exige exported_at; READ/NOT_READ exigem reading_reference")
    void taskTimestampsAreBoundToStatus() {
        assertThat(SQL).contains("CHECK (status <> 'EXPORTED' OR exported_at IS NOT NULL)");
        assertThat(SQL).contains(
                "CHECK (status NOT IN ('READ', 'NOT_READ') OR reading_reference IS NOT NULL)");
    }

    @Test
    @DisplayName("nenhuma tabela da V5_3 recebe semente: fornecedor e tarefa sao dados do tenant")
    void noSeedData() {
        String withoutComments = SQL.replaceAll("(?m)--.*$", "");
        assertThat(Pattern.compile("(?i)\\bINSERT\\s+INTO\\b").matcher(withoutComments).find())
                .as("a migration da leitura terceirizada nao pode semear dados").isFalse();
    }

    @Test
    @DisplayName("as quatro tabelas sao criadas e registradas (RLS, auditoria), e so elas")
    void everyTableIsRegistered() {
        Set<String> tables = new LinkedHashSet<>();
        Matcher create = Pattern.compile("CREATE TABLE IF NOT EXISTS (\\w+)").matcher(SQL);
        while (create.find()) {
            tables.add(create.group(1));
        }
        Set<String> registered = new LinkedHashSet<>();
        Matcher register = Pattern.compile("register_tenant_table\\('(\\w+)'\\)").matcher(SQL);
        while (register.find()) {
            registered.add(register.group(1));
        }
        assertThat(tables).containsExactlyInAnyOrder(
                "reading_vendors", "reading_tasks", "reading_import_batches", "reading_import_records");
        assertThat(registered).isEqualTo(tables);
    }

    @Test
    @DisplayName("a idempotencia do aceite esta no banco: hash do arquivo unico, chave do registro unica entre IMPORTED, tarefa unica por rota e referencia")
    void idempotencyConstraintsExist() {
        assertThat(SQL).contains("CREATE UNIQUE INDEX idx_reading_import_batches_sha "
                + "ON reading_import_batches(tenant_id, file_sha256)");
        assertThat(SQL).contains("CREATE UNIQUE INDEX idx_reading_import_records_key "
                + "ON reading_import_records(tenant_id, record_key)");
        assertThat(SQL).contains("WHERE status = 'IMPORTED'");
        assertThat(SQL).contains("CREATE UNIQUE INDEX idx_reading_tasks_unique "
                + "ON reading_tasks(tenant_id, reference_month, route_code, unit_id, kind)");
    }

    @Test
    @DisplayName("entidade e tabela tem exatamente as mesmas colunas (fora version e tenant_schema, que o helper acrescenta)")
    void entitiesMatchTableColumns() {
        for (Class<?> entity : List.of(ReadingVendor.class, ReadingTask.class,
                ReadingImportBatch.class, ReadingImportRecord.class)) {
            String table = entity.getAnnotation(Table.class).name();
            Set<String> sqlColumns = sqlColumns(table);
            sqlColumns.addAll(Set.of("version", "tenant_schema"));

            Set<String> mapped = new LinkedHashSet<>();
            for (Class<?> c = entity; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    Column column = f.getAnnotation(Column.class);
                    if (column != null) {
                        mapped.add(column.name());
                    } else if (f.isAnnotationPresent(jakarta.persistence.Id.class)) {
                        mapped.add("id");
                    }
                }
            }
            assertThat(mapped).as("colunas da entidade %s x tabela %s", entity.getSimpleName(), table)
                    .containsExactlyInAnyOrderElementsOf(sqlColumns);
        }
    }

    private static Set<String> sqlColumns(String table) {
        Matcher start = Pattern.compile("CREATE TABLE IF NOT EXISTS " + table + " \\(").matcher(SQL);
        assertThat(start.find()).as("tabela %s na migration", table).isTrue();
        int end = SQL.indexOf("\n);", start.end());
        String body = SQL.substring(start.end(), end);
        Set<String> columns = new LinkedHashSet<>();
        Matcher column = Pattern.compile("(?m)^    ([a-z_][a-z0-9_]*) (?:UUID|VARCHAR|INT|BOOLEAN|DATE|TIMESTAMPTZ|JSONB)\\b").matcher(body);
        while (column.find()) {
            columns.add(column.group(1));
        }
        return columns;
    }
}
