package br.com.ziipsan.app;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Padrão de nome de coluna de dado pessoal e allowlist de exceções, compartilhados pelos
 * testes de cobertura de redação ({@link AuditRedactionCoverageTest}) e de classificação
 * ({@link DataClassificationCoverageTest}).
 *
 * <p>Uma coluna nova que case com o padrão QUEBRA o build até ser classificada (e redigida):
 * sem isto, uma coluna nova com CPF ou e-mail entraria em claro na trilha de auditoria sem
 * que ninguém percebesse.
 *
 * <p>P0.11: {@code consumer_units.coordinates} SAIU desta allowlist — a isenção foi revista
 * e a coluna passou a ter regra de redação e classificação LOC (localização da ligação
 * vinculada a pessoa). As isenções restantes são localizações públicas ou de pessoa
 * jurídica, que não são dado pessoal.
 */
final class PersonalDataColumns {

    static final Pattern PERSONAL = Pattern.compile(
            "(?i).*(cpf|cnpj|document|email|phone|birth|ip_address|user_agent|geoloc|driver|contact|street"
                    + "|full_name|username|signature|address|coordinates|photos|notes|technician)"
                    + ".*|^cep$|^name$|.*_name$");

    /** "tabela.coluna" que casa com o padrão mas não é dado pessoal, com o motivo. */
    // P0.25: roles.* e service_orders.* sairam da V1_1 (voltam em P0.13/P1.31); as
    // entradas correspondentes sairam daqui junto, para a allowlist nao ficar obsoleta.
    static final Map<String, String> NOT_PERSONAL = Map.ofEntries(
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
            Map.entry("consumer_units.pavement_street_id", "referência por id ao tipo de pavimentação da rua (catálogo pavements, ainda sem tabela própria); não é dado pessoal"),
            Map.entry("consumer_units.address_id", "referência por id; o endereço é redigido em addresses"),
            Map.entry("waste_cdf.technician_id", "referência por id; o nome é redigido em technician_name"),
            Map.entry("customer_contacts.primary_contact", "booleano (contato principal ou não)"),
            Map.entry("lab_analysis_method.safety_notes", "nota técnica de segurança do método"),
            Map.entry("sampling_point.coordinates", "localização de ponto público de amostragem"),
            Map.entry("waste_destination.coordinates", "localização de destinador (pessoa jurídica)"),
            // P1.31: nome de tipo de servico (catalogo, nao dado pessoal).
            Map.entry("service_types.name", "nome de tipo de servico (catalogo)"),
            // P1.2: colunas do staging do legado que casam com o padrao por nome
            // mas nao sao dado pessoal — mesmo precedente de
            // fiscal_issuers.document_kind e payments.document_id acima. O dado
            // pessoal de verdade do staging (holder_name, holder_document_masked)
            // tem regra de redacao + classificacao na V14_1, nao allowlist.
            Map.entry("legacy_import_staging_units.holder_document_kind",
                    "especie do documento do titular (CPF/CNPJ), sem o numero"),
            Map.entry("legacy_import_staging_units.holder_document_valid",
                    "booleano (documento valido ou nao), sem o numero"),
            Map.entry("legacy_import_batches.mapping_name",
                    "nome do arquivo de mapeamento, nao dado pessoal"),
            Map.entry("legacy_import_lineage.mapping_name",
                    "nome do arquivo de mapeamento, nao dado pessoal"),
            // P1-A (integracao): colunas novas da P1.8/P1.26 que casam com o padrao por
            // nome mas nao sao dado pessoal — mesmo precedente de payments.document_id,
            // payments.document_type, collection_agreements.document_kind e
            // gis_network_element.name acima.
            Map.entry("fiscal_document_batch_items.document_id", "id interno do documento fiscal do item do lote"),
            Map.entry("fiscal_document_batches.document_type", "tipo do documento fiscal do lote"),
            Map.entry("fiscal_document_events.document_id", "id interno do documento fiscal do evento"),
            Map.entry("fiscal_document_taxes.document_id", "id interno do documento fiscal do tributo"),
            Map.entry("fiscal_documents.document_type", "tipo do documento fiscal"),
            Map.entry("fiscal_issuers.document_kind", "tipo do documento do emitente (CPF/CNPJ)"),
            Map.entry("fiscal_layout_versions.document_type", "tipo do documento fiscal do leiaute"),
            Map.entry("fiscal_series.document_type", "tipo do documento fiscal da série"),
            Map.entry("gis_attachment.document_id", "referência interna (UUID) ao documento anexado, não número de documento pessoal"),
            Map.entry("gis_network_element_legacy.name", "nome de elemento de rede legado"),
            // P1.20 (V1_16): ids internos do pipeline de documentos — referência ao
            // catálogo / ao documento anexo, não número de documento pessoal.
            Map.entry("documents.document_type_id", "id interno do tipo de documento do catálogo"),
            Map.entry("document_scan_events.document_id", "id interno do documento anexo do evento de varredura"),
            // P1-C (integracao): colunas novas da camada C que casam com o padrao
            // por nome mas nao sao dado pessoal — catalogos (nome), referencias
            // por id e flags de modelo. O dado pessoal de verdade (a referencia
            // ao documento comprobatorio de vulneravel) tem regra de redacao na
            // V3_13, nao allowlist.
            Map.entry("addresses.street_id", "referência por id ao logradouro (catálogo street_names)"),
            Map.entry("block_faces.name", "nome de face de quadra (catálogo)"),
            Map.entry("consent_purposes.name", "nome de finalidade de consentimento (catálogo)"),
            Map.entry("customer_addresses.address_id", "referência por id; o endereço é redigido em addresses"),
            Map.entry("customer_contacts.phone_type", "tipo de telefone (móvel/fixo), sem o número"),
            // P1.41 (V3_8): espécie do documento validado (CPF/CNPJ), sem o
            // número — mesmo precedente de holder_document_kind acima. O
            // resultado da validação guarda só situação e data; o número
            // continua cifrado em customers.
            Map.entry("customer_document_validations.document_kind",
                    "espécie do documento validado (CPF/CNPJ), sem o número"),
            Map.entry("customer_delete_reasons.name", "nome de motivo de exclusão (catálogo)"),
            Map.entry("customer_types.name", "nome de tipo de cliente (catálogo)"),
            Map.entry("customers.document_exemption_reason_id", "referência por id ao motivo de dispensa (catálogo)"),
            Map.entry("document_exemption_reasons.name", "nome de motivo de dispensa (catálogo)"),
            Map.entry("ibge_municipalities.name", "nome de município (geografia pública)"),
            Map.entry("neighborhoods.name", "nome de bairro (catálogo)"),
            Map.entry("property_profiles.name", "nome de perfil do imóvel (catálogo)"),
            Map.entry("relation_roles.name", "nome de papel do vínculo (catálogo)"),
            Map.entry("service_protocol_specs.required_documents", "lista de documentos exigidos pelo protocolo (modelo, não documento de pessoa)"),
            Map.entry("service_protocol_specs.requires_address", "booleano (protocolo exige endereço ou não)"),
            Map.entry("sewer_discharges.name", "nome de tipo de esgotamento (catálogo)"),
            Map.entry("street_names.name", "nome de logradouro (catálogo)"),
            Map.entry("unit_link_end_reasons.name", "nome de motivo de fim de vínculo (catálogo)"));

    private PersonalDataColumns() {
    }
}
