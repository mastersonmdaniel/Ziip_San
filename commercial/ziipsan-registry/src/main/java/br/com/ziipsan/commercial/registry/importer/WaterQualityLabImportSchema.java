package br.com.ziipsan.commercial.registry.importer;

import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static br.com.ziipsan.commercial.registry.importer.ImportColumn.ColumnType.DATE;
import static br.com.ziipsan.commercial.registry.importer.ImportColumn.ColumnType.TEXT;

/**
 * P2.24: esquema {@code billing.water_quality_lab} — a planilha MENSAL do
 * laboratorio que alimenta o bloco de qualidade da agua da conta
 * (Decreto 5.440/2005), carregada pelo importador generico da P1.40
 * (JOR-26) enquanto o modulo de qualidade da ED-50 nao estiver ativo,
 * conforme o parametro {@code bill.water_quality_source} do tenant
 * (padrao de fabrica: esta planilha). Este esquema NAO e um importador
 * novo: e a declaracao que o {@link ImportSchemaRegistry} pede, no mesmo
 * desenho do esquema de beneficiarios da ED-57 — mora no modulo registry,
 * dono do framework da P1.40, e grava por JDBC na tabela do billing, sem
 * dependencia de compilacao entre os modulos.
 *
 * <p>Destino: {@code water_quality_results} (billing), com
 * {@code source = 'LAB_SPREADSHEET'} gravado pelo aplicador — a linha nao
 * declara a propria fonte na planilha. PENDENCIA: o plano v3.0 SS3 nao
 * reserva versao Flyway para a P2.24; a tabela esta pronta como DDL em
 * {@code db/migration-pendente/} do modulo billing e o aplicador so tem
 * onde gravar depois de a versao ser reservada e a migration promovida
 * (ver o relatorio da P2.24).
 *
 * <p>NENHUM parametro de qualidade e fixado aqui: o plano nao traz
 * catalogo de parametros, limites nem valores de referencia do
 * Decreto 5.440/2005, e nenhum e inventado. A planilha carrega, por linha,
 * o parametro (chave e nome de exibicao), a unidade, o resultado e a
 * referencia — todos texto do laboratorio, gravados como vieram
 * ({@code result_text} nunca e convertido para numero: resultado
 * qualitativo do laboratorio nao quebra a importacao nem e reinterpretado).
 *
 * <p>Chave da linha (duplicidade no arquivo e no banco): a composicao
 * (mes de referencia, sistema, parametro) — a mesma do indice unico da
 * tabela para a fonte LAB_SPREADSHEET. Reenviar a mesma planilha nao
 * duplica: o validador generico recusa as linhas como
 * DUPLICATE_IN_DATABASE e o reenvio inteiro consta do relatorio de
 * recusas (comportamento da P1.40).
 *
 * <p>Guarda propria do esquema (o validador generico nao a expressa):
 * {@code reference_month} tem de ser o PRIMEIRO DIA do mes — a tabela tem
 * o mesmo CHECK e o bloco da conta e mensal. A guarda roda na validacao
 * completa deste esquema ({@link #validate}) e de novo no aplicador,
 * antes de qualquer INSERT, como na ED-57: sem ela, a linha passaria na
 * preparacao e quebraria no INSERT, derrubando o lote inteiro em vez de
 * constar do relatorio de recusas. Limite conhecido, o mesmo registrado
 * na ED-57: o {@code ImportService} da P1.40 chama apenas o validador
 * generico; ate o framework ganhar gancho de validacao cruzada por
 * esquema, a guarda do aplicador e a barreira efetiva na aplicacao.
 *
 * <p>Inverso (OS de correcao do lote): apaga o resultado inserido por
 * esta linha. Se o mes ja foi impresso em conta emitida, a conta nao
 * muda (conta emitida e imutavel, P2.30/P2.32): a correcao vale para as
 * emissoes seguintes, e uma nova planilha do mesmo mes com valores
 * corrigidos exige antes a reversao do lote anterior, porque a chave ja
 * existe no banco (recusa DUPLICATE_IN_DATABASE, nao atualizacao
 * silenciosa).
 */
@Component
public class WaterQualityLabImportSchema implements ImportSchema {

    public static final String CODE = "billing.water_quality_lab";

    /** Fonte gravada pelo aplicador em toda linha deste esquema. */
    static final String SOURCE = "LAB_SPREADSHEET";

    private static final List<ImportColumn> COLUMNS = List.of(
            new ImportColumn("reference_month", DATE, true, List.of(), "2026-05-01", null, true),
            new ImportColumn("supply_system", TEXT, true, List.of(), "ETA_CENTRAL", null, true),
            new ImportColumn("parameter_key", TEXT, true, List.of(), "parametro_exemplo", null, true),
            ImportColumn.text("parameter_name", true, "Parametro de exemplo"),
            ImportColumn.text("unit", false, ""),
            ImportColumn.text("result_text", true, "0.0"),
            ImportColumn.text("reference_text", false, ""));

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "Qualidade da agua - planilha mensal do laboratorio";
    }

    @Override
    public List<ImportColumn> columns() {
        return COLUMNS;
    }

    /**
     * A validacao completa deste esquema: a validacao generica da P1.40
     * MAIS a recusa linha a linha de {@code reference_month} fora do
     * primeiro dia do mes, que o validador generico nao expressa (ver o
     * Javadoc da classe). Linha recusada aqui sai das aceitas e entra no
     * relatorio de recusas.
     */
    public ImportValidator.ValidationResult validate(ImportCsvParser.ParsedCsv csv,
                                                     ImportValidator.DbChecks db) {
        ImportValidator.ValidationResult generic = ImportValidator.validate(this, csv, db);
        java.util.List<ImportCsvParser.ParsedRow> accepted = new java.util.ArrayList<>();
        java.util.List<ImportValidator.Rejection> rejections =
                new java.util.ArrayList<>(generic.rejections());
        for (ImportCsvParser.ParsedRow row : generic.accepted()) {
            java.util.Optional<ImportValidator.Rejection> rejection = monthStartRejection(row);
            if (rejection.isPresent()) {
                rejections.add(rejection.get());
            } else {
                accepted.add(row);
            }
        }
        rejections.sort(java.util.Comparator.comparingInt(ImportValidator.Rejection::rowNumber));
        return new ImportValidator.ValidationResult(List.copyOf(accepted), List.copyOf(rejections));
    }

    /** A linha tem {@code reference_month} fora do primeiro dia do mes? */
    static java.util.Optional<ImportValidator.Rejection> monthStartRejection(ImportCsvParser.ParsedRow row) {
        String value = row.values().getOrDefault("reference_month", "");
        // Linha sem a data ou com data invalida ja foi recusada pelo validador generico.
        if (value.isBlank()) {
            return java.util.Optional.empty();
        }
        LocalDate month;
        try {
            month = LocalDate.parse(value);
        } catch (RuntimeException e) {
            return java.util.Optional.empty();
        }
        if (month.getDayOfMonth() != 1) {
            return java.util.Optional.of(new ImportValidator.Rejection(row.number(), "reference_month",
                    "INVALID_DOMAIN", "Mes de referencia deve ser o primeiro dia do mes:"
                    + " o bloco de qualidade da agua e mensal (Decreto 5.440/2005)."));
        }
        return java.util.Optional.empty();
    }

    /** Guarda do aplicador: a mesma regra de {@link #monthStartRejection}, antes de qualquer SQL. */
    static void requireMonthStart(Map<String, String> values) throws SQLException {
        String value = values.get("reference_month");
        LocalDate month;
        try {
            month = LocalDate.parse(value);
        } catch (RuntimeException e) {
            throw new SQLException("reference_month invalido na linha da planilha do laboratorio:"
                    + " a linha devia ter sido recusada na validacao e nao pode ser aplicada.");
        }
        if (month.getDayOfMonth() != 1) {
            throw new SQLException("reference_month fora do primeiro dia do mes: a linha devia ter"
                    + " sido recusada na validacao do esquema (o bloco e mensal) e nao pode ser aplicada.");
        }
    }

    @Override
    public ImportApplier applier() {
        return new ImportApplier() {
            @Override
            public boolean existsByKey(Connection connection, String schema, UUID tenantId,
                                       Map<String, String> values) throws SQLException {
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT 1 FROM " + schema + ".water_quality_results"
                                + " WHERE tenant_id = ? AND reference_month = ? AND supply_system = ?"
                                + " AND parameter_key = ? AND source = ?")) {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, LocalDate.parse(values.get("reference_month")));
                    ps.setString(3, values.get("supply_system"));
                    ps.setString(4, values.get("parameter_key"));
                    ps.setString(5, SOURCE);
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next();
                    }
                }
            }

            @Override
            public Applied apply(Connection connection, String schema, UUID tenantId, UUID actorId,
                                 Map<String, String> values) throws SQLException {
                requireMonthStart(values);
                UUID id = UUID.randomUUID();
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO " + schema + ".water_quality_results"
                                + " (id, tenant_id, reference_month, supply_system, parameter_key,"
                                + " parameter_name, unit, result_text, reference_text, source,"
                                + " created_by, updated_by)"
                                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    int i = 0;
                    ps.setObject(++i, id);
                    ps.setObject(++i, tenantId);
                    ps.setObject(++i, LocalDate.parse(values.get("reference_month")));
                    ps.setString(++i, values.get("supply_system"));
                    ps.setString(++i, values.get("parameter_key"));
                    ps.setString(++i, values.get("parameter_name"));
                    setTextOrNull(ps, ++i, values.get("unit"));
                    ps.setString(++i, values.get("result_text"));
                    setTextOrNull(ps, ++i, values.get("reference_text"));
                    ps.setString(++i, SOURCE);
                    setUuid(ps, ++i, actorId);
                    setUuid(ps, ++i, actorId);
                    ps.executeUpdate();
                }
                return new Applied("water_quality_results", id,
                        values.get("reference_month") + "/" + values.get("supply_system")
                                + "/" + values.get("parameter_key"));
            }

            @Override
            public void reverse(Connection connection, String schema, UUID tenantId, UUID actorId,
                                Applied applied) throws SQLException {
                // Apaga o resultado inserido por esta linha; idempotente
                // (linha ja revertida ou inexistente nao e erro).
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM " + schema + ".water_quality_results"
                                + " WHERE tenant_id = ? AND id = ? AND source = ?")) {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, applied.targetId());
                    ps.setString(3, SOURCE);
                    ps.executeUpdate();
                }
            }
        };
    }

    static void setTextOrNull(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    static void setUuid(PreparedStatement ps, int index, UUID value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.OTHER);
        } else {
            ps.setObject(index, value);
        }
    }
}
