package br.com.ziipsan.operations.metering;

import br.com.ziipsan.operations.metering.domain.ConsumptionAnomaly;
import br.com.ziipsan.operations.metering.domain.ConsumptionRecord;
import br.com.ziipsan.operations.metering.domain.Meter;
import br.com.ziipsan.operations.metering.domain.MeterInstallation;
import br.com.ziipsan.operations.metering.domain.MeterReading;
import br.com.ziipsan.operations.metering.domain.ReadingAnomaly;
import br.com.ziipsan.operations.metering.rules.AverageResult;
import br.com.ziipsan.operations.metering.rules.ConsumptionAnomalyKind;
import br.com.ziipsan.operations.metering.rules.ConsumptionEffect;
import br.com.ziipsan.operations.metering.rules.ConsumptionType;
import br.com.ziipsan.operations.metering.rules.MeasurementKind;
import br.com.ziipsan.operations.metering.rules.ReadingEffect;
import br.com.ziipsan.operations.metering.rules.ReadingStatus;
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
 * Mantém a migration {@code V5_1} e as entidades/enums de acordo, sem banco.
 *
 * <p>O teste de instalação do zero ({@code platform/ziipsan-app}) é quem prova o mapeamento contra
 * um PostgreSQL, mas só enxerga este módulo depois que o app depender dele. Este teste pega o erro
 * mais comum antes disso: um valor de enum que o {@code CHECK} do banco não aceita, uma coluna
 * mapeada que não existe (ou que sobrou na tabela) e um catálogo com semente.
 */
class MigrationConsistencyTest {

    private static final String SQL = readSql();

    private static String readSql() {
        return readSql("/db/migration/tenant/V5_1__create_metering_schema.sql");
    }

    private static String readSql(String path) {
        try (InputStream in = MigrationConsistencyTest.class.getResourceAsStream(path)) {
            assertThat(in).as("migration no classpath: " + path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static <E extends Enum<E>> Set<String> names(Class<E> type) {
        return EnumSet.allOf(type).stream().map(Enum::name).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Todos os {@code CHECK (coluna IN (...))} da migration, por nome de coluna (uma lista por ocorrência). */
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
    @DisplayName("os CHECKs de valor da migration listam exatamente os valores dos enums do código")
    void checkConstraintsMatchTheEnums() {
        Map<String, List<Set<String>>> checks = checkLists();

        assertThat(checks.get("consumption_type")).containsExactly(names(ConsumptionType.class));
        assertThat(checks.get("reading_status")).containsExactly(names(ReadingStatus.class));
        assertThat(checks.get("service_kind")).containsExactly(names(ConsumptionRecord.ServiceKind.class));
        assertThat(checks.get("average_source")).containsExactly(names(AverageResult.Source.class));
        assertThat(checks.get("source")).containsExactly(names(MeterReading.Source.class));
        assertThat(checks.get("system_role")).containsExactly(names(ReadingAnomaly.SystemRole.class));
        assertThat(checks.get("status")).containsExactly(names(Meter.Status.class));
        assertThat(checks.get("reading_effect_with_reading")).containsExactly(names(ReadingEffect.class));
        assertThat(checks.get("consumption_effect_with_reading")).containsExactly(names(ConsumptionEffect.class));
        assertThat(checks.get("reading_effect_without_reading"))
                .containsExactly(Arrays.stream(ReadingEffect.values()).filter(e -> e != ReadingEffect.INFORMED)
                        .map(Enum::name).collect(Collectors.toCollection(LinkedHashSet::new)));
        assertThat(checks.get("consumption_effect_without_reading"))
                .containsExactly(Set.of("AVERAGE", "MINIMUM", "NOT_METERED"));

        // 'kind' aparece em duas tabelas com água/poço e em consumption_anomalies com as anormalidades
        assertThat(checks.get("kind")).containsExactlyInAnyOrder(names(MeasurementKind.class),
                names(MeasurementKind.class), names(ConsumptionAnomalyKind.class));
    }

    @Test
    @DisplayName("nenhum catálogo ou tabela recebe semente: a migration não tem INSERT")
    void noSeedData() {
        String withoutComments = SQL.replaceAll("(?m)--.*$", "");
        assertThat(Pattern.compile("(?i)\\bINSERT\\s+INTO\\b").matcher(withoutComments).find())
                .as("a migration de metering não pode semear dados").isFalse();
        assertThat(withoutComments).doesNotContainIgnoringCase("COPY ");
    }

    @Test
    @DisplayName("toda tabela termina registrada em register_tenant_table (RLS, auditoria), e só ela")
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
        assertThat(tables).hasSize(6);
        assertThat(registered).isEqualTo(tables);
    }

    @Test
    @DisplayName("entidade e tabela têm exatamente as mesmas colunas (fora version e tenant_schema, que o helper acrescenta)")
    void entitiesMatchTableColumns() {
        // P2.3 (V5_4): meter_readings foi RECRIADA particionada e consumption_records
        // ganhou reading_reference. As colunas novas chegam pelos ALTERs da V5_4; as
        // demais guardas continuam lendo so a V5_1.
        String v54 = readSql("/db/migration/tenant/V5_4__meter_reading_partition_and_idempotency.sql");
        Map<String, Set<String>> v54Columns = new LinkedHashMap<>();
        Matcher alter = Pattern.compile("ALTER TABLE (\\w+) ADD COLUMN IF NOT EXISTS ([a-z_]+)").matcher(v54);
        while (alter.find()) {
            v54Columns.computeIfAbsent(alter.group(1), k -> new LinkedHashSet<>()).add(alter.group(2));
        }
        // idempotency_key nasce no CREATE TABLE particionado da V5_4, nao por ALTER.
        Matcher create = Pattern.compile("CREATE TABLE meter_readings \\((.*?)\\) PARTITION BY RANGE", Pattern.DOTALL).matcher(v54);
        assertThat(create.find()).as("CREATE TABLE meter_readings na V5_4").isTrue();
        Matcher col = Pattern.compile("(?m)^\\s+([a-z_]+) (?:UUID|VARCHAR|INT|NUMERIC|BOOLEAN|DATE|TIMESTAMPTZ|JSONB)\\b").matcher(create.group(1));
        while (col.find()) {
            v54Columns.computeIfAbsent("meter_readings", k -> new LinkedHashSet<>()).add(col.group(1));
        }
        for (Class<?> entity : List.of(Meter.class, MeterInstallation.class, ReadingAnomaly.class,
                ConsumptionAnomaly.class, MeterReading.class, ConsumptionRecord.class)) {
            String table = entity.getAnnotation(Table.class).name();
            Set<String> sqlColumns = sqlColumns(table);
            sqlColumns.addAll(Set.of("version", "tenant_schema"));
            sqlColumns.addAll(v54Columns.getOrDefault(table, Set.of()));

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
        Matcher column = Pattern.compile("(?m)^    ([a-z_]+) (?:UUID|VARCHAR|INT|NUMERIC|BOOLEAN|DATE|TIMESTAMPTZ|JSONB)\\b").matcher(body);
        while (column.find()) {
            columns.add(column.group(1));
        }
        return columns;
    }
}
