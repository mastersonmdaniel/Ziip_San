package br.com.ziipsan.commercial.registry.application;

import java.util.List;

/**
 * Esquema CSV do cadastro (P1.3, dona desta frente). O importador genérico é
 * da P1.40: ele lê esta declaração para gerar o modelo para baixar (colunas,
 * tipos, domínios e exemplo), validar cada linha (tipo, domínio, chave
 * estrangeira, duplicidade) e aplicar por OS interna de lote da P1.31. Esta
 * classe NÃO importa nada: ela só declara o esquema.
 *
 * <p>Nota da integração P1-C (encaixe P1.3 × P1.40, declarado, não escondido):
 * esta declaração ainda NÃO está registrada como bean {@code ImportSchema} do
 * importador — os únicos esquemas importáveis hoje são {@code registry.categories}
 * e {@code registry.subcategories} (as provas do framework). Importar clientes e
 * unidades por CSV via {@code /imports} exige uma frente de acompanhamento com
 * os aplicadores (gravar/desfazer por OS interna, respeitando rascunho,
 * validate-step e documento cifrado); a integração não inventa esses aplicadores.
 *
 * <p>Regras da declaração:
 * <ul>
 *   <li>Colunas em inglês camelCase na API viram snake_case no CSV, na ordem da tela.</li>
 *   <li>{@code osType} é o tipo de OS da P1.31 de cada coluna (mapa de
 *   {@link CadastralChangeService}); coluna sem efeito de OS (ex.: chave de
 *   correlação da linha) tem {@code osType} nulo.</li>
 *   <li>Documento entra cifrado na aplicação, nunca em claro no relatório de
 *   recusas: a coluna é validada como documento, e o relatório cita só a linha
 *   e o campo.</li>
 *   <li>Dados sensíveis (LGPD, art. 11) NÃO têm coluna: pessoa dependente de
 *   abastecimento contínuo só é coletada a pedido do titular, fora do CSV.</li>
 * </ul>
 */
public final class RegistryCsvSchema {

    private RegistryCsvSchema() {
    }

    public record Column(String csv, String apiField, String target, String type, boolean required,
                         String domain, String osType, String example) {
    }

    /** Colunas do CSV do cliente (target CUSTOMER). */
    public static final List<Column> CUSTOMER_COLUMNS = List.of(
            new Column("person_type", "personType", "CUSTOMER", "enum(PF,PJ)", true, "PF|PJ",
                    "CUSTOMER_DATA_UPDATE", "PF"),
            new Column("name", "name", "CUSTOMER", "string(200)", true, null,
                    "CUSTOMER_DATA_UPDATE", "Maria da Silva"),
            new Column("trade_name", "tradeName", "CUSTOMER", "string(200)", false, "nulo em PF",
                    "CUSTOMER_DATA_UPDATE", ""),
            new Column("document", "document", "CUSTOMER", "cpf_cnpj", false, "CPF ou CNPJ válido",
                    "CUSTOMER_DATA_UPDATE", "529.982.247-25"),
            new Column("document_exemption_reason_id", "documentExemptionReasonId", "CUSTOMER", "uuid", false,
                    "catálogo document_exemption_reasons", "CUSTOMER_DATA_UPDATE", ""),
            new Column("customer_type_id", "customerTypeId", "CUSTOMER", "uuid", false,
                    "catálogo customer_types, compatível com person_type", "CUSTOMER_DATA_UPDATE", ""),
            new Column("government_sphere", "governmentSphere", "CUSTOMER", "enum", false,
                    "FEDERAL|STATE|MUNICIPAL|NONE; obrigatório para ente público", "CUSTOMER_DATA_UPDATE", ""),
            new Column("parent_customer_id", "parentCustomerId", "CUSTOMER", "uuid", false,
                    "customers, sem ciclo", "CUSTOMER_DATA_UPDATE", ""),
            new Column("birth_date", "birthDate", "CUSTOMER", "date", false, "AAAA-MM-DD, no passado",
                    "CUSTOMER_DATA_UPDATE", "1980-05-12"),
            new Column("email", "emails", "CUSTOMER", "email", false, "RFC 5322",
                    "CUSTOMER_CONTACT_UPDATE", "maria@exemplo.com"),
            new Column("phone", "phones", "CUSTOMER", "e164", false, "E.164 (+5511987654321)",
                    "CUSTOMER_CONTACT_UPDATE", "+5511987654321"),
            new Column("cep", "addresses.cep", "CUSTOMER", "cep", false, "8 dígitos",
                    "DELIVERY_ADDRESS_CHANGE", "88000-000"),
            new Column("street_name", "addresses.streetName", "CUSTOMER", "string(200)", false, null,
                    "DELIVERY_ADDRESS_CHANGE", "Rua das Flores"),
            new Column("address_number", "addresses.number", "CUSTOMER", "string(20)", false, null,
                    "DELIVERY_ADDRESS_CHANGE", "123"),
            new Column("municipality_ibge", "addresses.municipalityIbge", "CUSTOMER", "ibge", false,
                    "catálogo ibge_municipalities, 7 dígitos", "DELIVERY_ADDRESS_CHANGE", "4205407"));

    /** Colunas do CSV da unidade consumidora (target CONSUMER_UNIT). */
    public static final List<Column> UNIT_COLUMNS = List.of(
            new Column("block_id", "blockId", "CONSUMER_UNIT", "uuid", false, "blocks",
                    "UNIT_PROFILE_UPDATE", ""),
            new Column("block_face_id", "blockFaceId", "CONSUMER_UNIT", "uuid", false,
                    "catálogo block_faces, quando registry.use_block_face", "UNIT_PROFILE_UPDATE", ""),
            new Column("lot", "lot", "CONSUMER_UNIT", "int", false, "0-9999, com quadra",
                    "UNIT_PROFILE_UPDATE", "12"),
            new Column("sublot", "sublot", "CONSUMER_UNIT", "int", false, "0-999, com quadra",
                    "UNIT_PROFILE_UPDATE", "1"),
            new Column("reading_route_id", "readingRouteId", "CONSUMER_UNIT", "uuid", false, "reading_routes",
                    "UNIT_PROFILE_UPDATE", ""),
            new Column("reading_sequence", "readingSequence", "CONSUMER_UNIT", "int", false, ">= 0",
                    "UNIT_PROFILE_UPDATE", "45"),
            new Column("cep", "address.cep", "CONSUMER_UNIT", "cep", true, "8 dígitos",
                    "DELIVERY_ADDRESS_CHANGE", "88000-000"),
            new Column("street_name", "address.streetName", "CONSUMER_UNIT", "string(200)", true, null,
                    "DELIVERY_ADDRESS_CHANGE", "Rua das Flores"),
            new Column("address_number", "address.number", "CONSUMER_UNIT", "string(20)", true, null,
                    "DELIVERY_ADDRESS_CHANGE", "123"),
            new Column("municipality_ibge", "address.municipalityIbge", "CONSUMER_UNIT", "ibge", false,
                    "catálogo ibge_municipalities, 7 dígitos", "DELIVERY_ADDRESS_CHANGE", "4205407"),
            new Column("profile_id", "profileId", "CONSUMER_UNIT", "uuid", false,
                    "catálogo property_profiles (coluna property_profile_id); provisório só em PROVISIONAL",
                    "UNIT_PROFILE_CHANGE", ""),
            new Column("sewer_discharge_id", "sewerDischargeId", "CONSUMER_UNIT", "uuid", false,
                    "catálogo sewer_discharges", "UNIT_PROFILE_UPDATE", ""),
            new Column("parent_unit_id", "parentUnitId", "CONSUMER_UNIT", "uuid", false,
                    "consumer_units, mesma quadra, sem ciclo", "UNIT_PROFILE_UPDATE", ""),
            new Column("parent_relation_type", "parentRelationType", "CONSUMER_UNIT", "enum", false,
                    "CONDOMINIUM_MACRO|ANNEX; obrigatório com parent_unit_id", "UNIT_PROFILE_UPDATE", ""),
            new Column("iptu", "externalIds.iptu", "CONSUMER_UNIT", "string(30)", false, null,
                    "UNIT_PROFILE_UPDATE", "123456"),
            new Column("iptu_municipality_ibge", "iptuMunicipalityIbge", "CONSUMER_UNIT", "ibge", false,
                    "catálogo ibge_municipalities, 7 dígitos; único por município com iptu",
                    "UNIT_PROFILE_UPDATE", "4205407"),
            new Column("cnae_code", "cnaeCode", "CONSUMER_UNIT", "string(7)", false, "7 dígitos",
                    "UNIT_PROFILE_UPDATE", "3600601"),
            new Column("seasonal", "seasonal", "CONSUMER_UNIT", "boolean", false, "true|false",
                    "UNIT_PROFILE_UPDATE", "false"),
            new Column("due_day", "dueDay", "CONSUMER_UNIT", "int", false, "1-31",
                    "ALTERNATIVE_DUE_DAY", "10"),
            new Column("built_area_m2", "builtAreaM2", "CONSUMER_UNIT", "decimal", false, ">= 0",
                    "UNIT_PROFILE_UPDATE", "85.5"),
            new Column("status", "status", "CONSUMER_UNIT", "enum", false, "ACTIVE|PROVISIONAL",
                    "UNIT_PROFILE_UPDATE", "ACTIVE"));

    /** Toda coluna com tipo de OS precisa casar com o mapa campo → OS (ou seu campo-base, com ponto). */
    public static void validateDeclarations() {
        for (Column column : CUSTOMER_COLUMNS) {
            checkColumn(column);
        }
        for (Column column : UNIT_COLUMNS) {
            checkColumn(column);
        }
    }

    private static void checkColumn(Column column) {
        if (column.osType() == null) {
            return;
        }
        String base = column.apiField().contains(".")
                ? column.apiField().substring(0, column.apiField().indexOf('.'))
                : column.apiField();
        String expected = CadastralChangeService.osTypeFor(column.target(), base);
        if (expected != null && !expected.equals(column.osType())) {
            throw new IllegalStateException("Coluna CSV com tipo de OS divergente do mapa: " + column.csv()
                    + " (declarado " + column.osType() + ", mapa " + expected + ").");
        }
        if (expected == null) {
            throw new IllegalStateException("Coluna CSV sem tipo de OS no mapa: " + column.csv());
        }
    }
}
