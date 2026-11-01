package br.com.sigsan.app;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Toda coluna com nome de dado pessoal, numa tabela auditada, tem regra de redação
 * (public.audit_redaction_rule) ou uma justificativa explícita de por que não é dado pessoal.
 *
 * <p>Sem isto, uma coluna nova com CPF ou e-mail entraria em claro na trilha de auditoria sem que
 * ninguém percebesse: foi assim que nome, nascimento, CPF do motorista e IP acabaram em audit_log.
 * Uma coluna nova que case com o padrão abaixo QUEBRA o build até ser classificada.
 */
class AuditRedactionCoverageTest {

    private static final Pattern PERSONAL = Pattern.compile(
            "(?i).*(cpf|cnpj|document|email|phone|birth|ip_address|user_agent|geoloc|driver|contact|street"
                    + "|full_name|username|signature|address|coordinates|photos|notes|technician)"
                    + ".*|^cep$|^name$|.*_name$");

    /** "tabela.coluna" que casa com o padrão mas não é dado pessoal, com o motivo. */
    // P0.25: roles.* e service_orders.* sairam da V1_1 (voltam em P0.13/P1.31); as
    // entradas correspondentes sairam daqui junto, para a allowlist nao ficar obsoleta.
    private static final Map<String, String> NOT_PERSONAL = Map.ofEntries(
            Map.entry("localities.name", "nome de localidade (território)"),
            Map.entry("commercial_sectors.name", "nome de setor comercial"),
            Map.entry("reading_routes.name", "nome de rota de leitura"),
            Map.entry("categories.name", "nome de categoria tarifária"),
            Map.entry("subcategories.name", "nome de subcategoria tarifária"),
            Map.entry("water_statuses.name", "nome de situação de água"),
            Map.entry("sewer_statuses.name", "nome de situação de esgoto"),
            Map.entry("tariffs.name", "nome de tarifa"),
            Map.entry("collection_agreements.name", "nome de convênio bancário"),
            Map.entry("collection_agreements.document_kind", "tipo de documento de cobrança do convênio"),
            Map.entry("bank_return_files.file_name", "nome do arquivo de retorno do banco"),
            Map.entry("payments.document_type", "tipo do documento pago (conta, guia)"),
            Map.entry("payments.document_id", "id interno do documento pago"),
            Map.entry("dma_district.name", "nome de distrito de medição"),
            Map.entry("gis_network_element.name", "nome de elemento de rede"),
            Map.entry("epanet_simulation.name", "nome de simulação"),
            Map.entry("scenario_analysis.name", "nome de cenário"),
            Map.entry("quality_parameter.name", "nome de parâmetro de qualidade"),
            Map.entry("sampling_point.name", "nome de ponto de amostragem"),
            Map.entry("sampling_point.address", "endereço de ponto público de amostragem"),
            Map.entry("scada_tag.tag_name", "nome de tag de telemetria"),
            Map.entry("lab_instrument.name", "nome de instrumento"),
            Map.entry("lab_analysis_method.name", "nome de método de análise"),
            Map.entry("waste_transporter.cnpj", "CNPJ de transportadora (pessoa jurídica)"),
            Map.entry("waste_transporter.corporate_name", "razão social de transportadora"),
            Map.entry("waste_transporter.trade_name", "nome fantasia de transportadora"),
            Map.entry("waste_transporter.address", "endereço de transportadora"),
            Map.entry("waste_destination.cnpj", "CNPJ de destinador (pessoa jurídica)"),
            Map.entry("waste_destination.corporate_name", "razão social de destinador"),
            Map.entry("waste_destination.trade_name", "nome fantasia de destinador"),
            Map.entry("waste_destination.address", "endereço de destinador"),
            Map.entry("consumer_units.coordinates", "localização da ligação (rede), usada pelo GIS"),
            Map.entry("consumer_units.address_id", "referência por id; o endereço é redigido em addresses"),
            Map.entry("waste_cdf.technician_id", "referência por id; o nome é redigido em technician_name"),
            Map.entry("customer_contacts.primary_contact", "booleano (contato principal ou não)"),
            Map.entry("lab_analysis_method.safety_notes", "nota técnica de segurança do método"),
            Map.entry("sampling_point.coordinates", "localização de ponto público de amostragem"),
            Map.entry("waste_destination.coordinates", "localização de destinador (pessoa jurídica)"));

    @BeforeAll
    static void setUp() {
        ProvisionedTenant.ensure();
    }

    @Test
    @DisplayName("toda coluna com nome de dado pessoal numa tabela auditada tem regra de redação ou justificativa")
    void everyPersonalColumnIsClassified() throws SQLException {
        List<String> unclassified = new ArrayList<>();
        String sql = """
                SELECT c.table_name, c.column_name,
                       EXISTS (SELECT 1 FROM public.audit_redaction_rule r
                               WHERE r.table_name = c.table_name AND r.column_name = c.column_name) AS has_rule
                FROM information_schema.columns c
                WHERE c.table_schema = ?
                  AND EXISTS (SELECT 1 FROM pg_trigger t
                              JOIN pg_class cl ON cl.oid = t.tgrelid
                              JOIN pg_namespace n ON n.oid = cl.relnamespace
                              WHERE t.tgname = 'audit_trigger' AND n.nspname = c.table_schema
                                AND cl.relname = c.table_name)
                ORDER BY 1, 2""";
        int audited = 0;
        try (Connection conn = ProvisionedTenant.migratorDs.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ProvisionedTenant.SCHEMA);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    audited++;
                    String table = rs.getString(1);
                    String column = rs.getString(2);
                    String key = table + "." + column;
                    if (PERSONAL.matcher(column).matches() && !rs.getBoolean(3) && !NOT_PERSONAL.containsKey(key)) {
                        unclassified.add(key);
                    }
                }
            }
        }
        assertThat(audited).as("colunas de tabelas auditadas encontradas").isPositive();
        assertThat(unclassified)
                .as("colunas sem regra em audit_redaction_rule nem justificativa em NOT_PERSONAL")
                .isEmpty();
    }

    @Test
    @DisplayName("nenhuma justificativa aponta para coluna que não existe mais")
    void allowlistIsNotStale() throws SQLException {
        List<String> stale = new ArrayList<>();
        try (Connection conn = ProvisionedTenant.migratorDs.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM information_schema.columns WHERE table_schema = ? AND table_name = ? AND column_name = ?")) {
            for (String key : NOT_PERSONAL.keySet()) {
                String[] parts = key.split("\\.");
                ps.setString(1, ProvisionedTenant.SCHEMA);
                ps.setString(2, parts[0]);
                ps.setString(3, parts[1]);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        stale.add(key);
                    }
                }
            }
        }
        assertThat(stale).as("entradas de NOT_PERSONAL sem coluna correspondente").isEmpty();
    }
}
