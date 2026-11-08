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
 * Desde a P2.5 guarda também a {@code V5_6} (versionamento de
 * {@code consumption_records}, DAD-14) e a {@code V5_7} (tipo de OS de
 * {@code reading_anomalies}, JOR-10), lidas do classpath deste módulo.
 *
 * <p>O teste de instalação do zero ({@code platform/ziipsan-app}) é quem prova o mapeamento contra
 * um PostgreSQL, mas só enxerga este módulo depois que o app depender dele. Este teste pega o erro
 * mais comum antes disso: um valor de enum que o {@code CHECK} do banco não aceita, uma coluna
 * mapeada que não existe (ou que sobrou na tabela) e um catálogo com semente.
 */
class MigrationConsistencyTest {

    private static final String SQL = readSql();

    // P2.5 (correcao da revisao cruzada, B1/B4): o versionamento de
    // consumption_records (DAD-14) saiu da V4_4 do billing para a V5_6 deste
    // modulo, e a coluna do tipo de OS de reading_anomalies (JOR-10) nasce na
    // V5_7. As duas sao lidas de verdade daqui -- a V4_4 nao esta (nem nunca
    // esteve) no classpath deste modulo.
    private static final String SQL_V56 = readSql("/db/migration/tenant/V5_6__consumption_record_versioning.sql");
    private static final String SQL_V57 = readSql("/db/migration/tenant/V5_7__reading_anomaly_service_order_type.sql");

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
        Matcher alter = Pattern.compile("ALTER TABLE (\\w+) ADD COLUMN IF NOT EXISTS ([a-z_][a-z0-9_]*)").matcher(v54);
        while (alter.find()) {
            v54Columns.computeIfAbsent(alter.group(1), k -> new LinkedHashSet<>()).add(alter.group(2));
        }
        // idempotency_key nasce no CREATE TABLE particionado da V5_4, nao por ALTER.
        Matcher create = Pattern.compile("CREATE TABLE meter_readings \\((.*?)\\) PARTITION BY RANGE", Pattern.DOTALL).matcher(v54);
        assertThat(create.find()).as("CREATE TABLE meter_readings na V5_4").isTrue();
        Matcher col = Pattern.compile("(?m)^\\s+([a-z_][a-z0-9_]*) (?:UUID|VARCHAR|INT|NUMERIC|BOOLEAN|DATE|TIMESTAMPTZ|JSONB)\\b").matcher(create.group(1));
        while (col.find()) {
            v54Columns.computeIfAbsent("meter_readings", k -> new LinkedHashSet<>()).add(col.group(1));
        }
        // P2.5 (V5_6/V5_7, deste modulo): consumption_records ganha as colunas do
        // versionamento DAD-14 (version_no, superseded_by_id, bill_id,
        // bill_reference_month, parameter_version_ids) pela V5_6, e
        // reading_anomalies ganha service_order_type_code (JOR-10) pela V5_7.
        // As colunas sao LIDAS das migrations (mesmo padrao da V5_4 acima), nao
        // mais um conjunto fixo de nomes: se a migration mudar, a guarda muda junto.
        Map<String, Set<String>> laterColumns = new LinkedHashMap<>();
        for (String laterSql : List.of(SQL_V56, SQL_V57)) {
            // Correcao (micro-lote correcoes-rg-v57): a extracao le STATEMENTS
            // completos, nao linhas. Na V5_7 (migration PUBLICADA, que nao pode
            // ser reescrita) o ALTER esta quebrado em duas linhas; SQL e
            // insensivel a espacos/quebras e o Flyway executa statements
            // terminados em ';', logo a quebra nao muda a semantica -- quem
            // nao enxergava o statement era o padrao de uma linha so. A
            // normalizacao de espacos vive SO nesta copia local, por statement:
            // o texto cru das migrations segue intacto para as demais
            // extracoes do arquivo, que dependem de ancoras multilinha
            // (^\s+, ^    ) sobre ele.
            for (String statement : laterSql.split(";")) {
                String normalizedStatement = statement.replaceAll("\\s+", " ").trim();
                Matcher laterAlter = Pattern.compile("ALTER\\s+TABLE\\s+(\\w+)\\s+ADD\\s+COLUMN\\s+IF\\s+NOT\\s+EXISTS\\s+([a-z_][a-z0-9_]*)").matcher(normalizedStatement);
                while (laterAlter.find()) {
                    laterColumns.computeIfAbsent(laterAlter.group(1), k -> new LinkedHashSet<>()).add(laterAlter.group(2));
                }
            }
        }
        assertThat(laterColumns.getOrDefault("consumption_records", Set.of()))
                .as("colunas do versionamento DAD-14 lidas da V5_6")
                .containsExactlyInAnyOrder("version_no", "superseded_by_id", "bill_id",
                        "bill_reference_month", "parameter_version_ids");
        assertThat(laterColumns.getOrDefault("reading_anomalies", Set.of()))
                .as("coluna do tipo de OS (JOR-10) lida da V5_7")
                .containsExactlyInAnyOrder("service_order_type_code");
        for (Class<?> entity : List.of(Meter.class, MeterInstallation.class, ReadingAnomaly.class,
                ConsumptionAnomaly.class, MeterReading.class, ConsumptionRecord.class)) {
            String table = entity.getAnnotation(Table.class).name();
            Set<String> sqlColumns = sqlColumns(table);
            sqlColumns.addAll(Set.of("version", "tenant_schema"));
            sqlColumns.addAll(v54Columns.getOrDefault(table, Set.of()));
            sqlColumns.addAll(laterColumns.getOrDefault(table, Set.of()));

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

    // As assercoes textuais de DAD-14 viviam no BillAccountMigrationConsistencyTest
    // do billing, lendo a V4_4; com a secao movida para a V5_6 (B1), elas moram
    // aqui, contra o SQL da V5_6 -- sem nenhuma assercao a menos.

    @Test
    @DisplayName("DAD-14 (V5_6): consumption_records ganha versao, sucessora, conta e versoes de parametro")
    void consumptionVersioningColumns() {
        assertThat(SQL_V56).contains("ALTER TABLE consumption_records ADD COLUMN IF NOT EXISTS version_no INT NOT NULL DEFAULT 1;");
        assertThat(SQL_V56).contains("ALTER TABLE consumption_records ADD COLUMN IF NOT EXISTS superseded_by_id UUID;");
        assertThat(SQL_V56).contains("ALTER TABLE consumption_records ADD COLUMN IF NOT EXISTS bill_id UUID;");
        assertThat(SQL_V56).contains("ALTER TABLE consumption_records ADD COLUMN IF NOT EXISTS bill_reference_month DATE;");
        assertThat(SQL_V56).contains("ALTER TABLE consumption_records ADD COLUMN IF NOT EXISTS parameter_version_ids JSONB;");
        assertThat(SQL_V56).contains("FOREIGN KEY (tenant_id, superseded_by_id) REFERENCES consumption_records(tenant_id, id)");
        assertThat(SQL_V56).contains("FOREIGN KEY (tenant_id, bill_reference_month, bill_id) REFERENCES bills(tenant_id, reference_month, id)");
        assertThat(SQL_V56).contains("CHECK (version_no >= 1)");
    }

    @Test
    @DisplayName("DAD-14 (V5_6): o indice unico antigo cai e o novo vale so para a versao vigente; a cadeia nao bifurca")
    void consumptionCurrentOnlyUniqueIndex() {
        assertThat(SQL_V56).contains("DROP INDEX IF EXISTS idx_consumption_unit_period;");
        assertThat(SQL_V56).contains("CREATE UNIQUE INDEX idx_consumption_unit_period_current "
                + "ON consumption_records(tenant_id, unit_id, service_kind, reference)");
        assertThat(SQL_V56).contains("WHERE superseded_by_id IS NULL");
        assertThat(SQL_V56).contains("CREATE UNIQUE INDEX idx_consumption_unit_period_version "
                + "ON consumption_records(tenant_id, unit_id, service_kind, reference, version_no)");
        assertThat(SQL_V56).contains("CREATE UNIQUE INDEX idx_consumption_superseded "
                + "ON consumption_records(tenant_id, superseded_by_id)");
    }

    @Test
    @DisplayName("JOR-10 (V5_7): reading_anomalies ganha o tipo de OS, com o CHECK de coerencia no padrao chk_")
    void readingAnomalyServiceOrderTypeColumn() {
        // Correcao (micro-lote correcoes-rg-v57): a afirmacao e sobre o
        // STATEMENT completo, nao sobre uma linha. O ALTER da V5_7 publicada
        // esta quebrado em duas linhas no texto cru; numa copia local com os
        // espacos normalizados, o statement inteiro -- ate o ';' inclusive,
        // que prova o termino do statement e nao um prefixo dele -- volta a
        // ser uma cadeia so, com o mesmo conteudo exigido antes (tabela,
        // coluna e tipo VARCHAR(60)). As outras duas assercoes deste teste
        // leem o texto cru e ficam intactas.
        String normalizedV57 = SQL_V57.replaceAll("\\s+", " ");
        assertThat(normalizedV57).contains("ALTER TABLE reading_anomalies ADD COLUMN IF NOT EXISTS service_order_type_code VARCHAR(60);");
        assertThat(SQL_V57).contains("chk_reading_anomalies_service_order_type");
        assertThat(SQL_V57).contains("generates_service_order = (service_order_type_code IS NOT NULL");
    }

    private static Set<String> sqlColumns(String table) {
        Matcher start = Pattern.compile("CREATE TABLE IF NOT EXISTS " + table + " \\(").matcher(SQL);
        assertThat(start.find()).as("tabela %s na migration", table).isTrue();
        int end = SQL.indexOf("\n);", start.end());
        String body = SQL.substring(start.end(), end);
        Set<String> columns = new LinkedHashSet<>();
        Matcher column = Pattern.compile("(?m)^    ([a-z_][a-z0-9_]*) (?:UUID|VARCHAR|INT|NUMERIC|BOOLEAN|DATE|TIMESTAMPTZ|JSONB)\\b").matcher(body);
        while (column.find()) {
            columns.add(column.group(1));
        }
        return columns;
    }
}
