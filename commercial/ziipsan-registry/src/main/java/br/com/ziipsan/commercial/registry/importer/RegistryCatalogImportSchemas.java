package br.com.ziipsan.commercial.registry.importer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static br.com.ziipsan.commercial.registry.importer.ImportColumn.ColumnType.BOOLEAN;
import static br.com.ziipsan.commercial.registry.importer.ImportColumn.ColumnType.TEXT;
import static br.com.ziipsan.commercial.registry.importer.RegistryCatalogImportSchema.Extra;

/**
 * ED-12: declaração dos esquemas de importação dos catálogos auxiliares do
 * cadastro (SAMAE 2.8.10) sobre o importador genérico da P1.40.
 *
 * <p>Cada bean {@code ImportSchema} entra no {@code ImportSchemaRegistry} e
 * passa a ser aceito nas rotas existentes de {@code /imports} — modelo para
 * baixar, validação por linha com relatório de recusas e aplicação por OS
 * interna de lote ({@code IMPORT_BATCH}, ADR-0039) são os do framework;
 * nenhuma rota nova, nenhum importador novo.
 *
 * <p>As colunas extras espelham as colunas reais das tabelas da V3_12 e os
 * extras aceitos pelo CRUD genérico ({@code RegistryCatalogService.EXTRAS}):
 * divergir daqui é divergir do banco. Os códigos seguem o padrão dos
 * esquemas embutidos ({@code registry.categories}): {@code registry.} + nome
 * da tabela.
 *
 * <p>Fora desta declaração, declarado e não escondido:
 * <ul>
 *   <li>{@code categories} e {@code subcategories}: já têm esquemas próprios
 *   embutidos da P1.40 ({@code registry.categories},
 *   {@code registry.subcategories}), com colunas além da forma simples.</li>
 *   <li>{@code block_faces}, {@code street_names} e {@code neighborhoods}:
 *   sem a forma código+nome (faces exigem a quadra; logradouros e bairros
 *   não têm coluna {@code code}); a carga deles NÃO é por este importador.</li>
 *   <li>{@code water_statuses} e {@code sewer_statuses}: tabelas tipadas com
 *   flags de faturamento próprias, fora da forma simples da V3_12; um erro
 *   de flag muda cobrança, então elas não entram num esquema genérico sem
 *   uma frente própria de conferência.</li>
 * </ul>
 *
 * <p>Limite do framework, declarado: a validação genérica da P1.40 não
 * confere comprimento nem máscara de texto (o {@code ImportColumn} não tem
 * esse atributo) — {@code code} acima de 40 caracteres e o código IBGE fora
 * de 7 dígitos só são recusados pelo CHECK/varchar do banco na aplicação,
 * que falha o lote alto em vez de gravar errado. É o mesmo limite dos
 * esquemas embutidos de categorias e subcategorias.
 */
@Configuration
public class RegistryCatalogImportSchemas {

    /** UFs do Brasil: domínio fechado e factual da coluna {@code state} de ibge_municipalities. */
    static final List<String> UF = List.of(
            "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG",
            "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO");

    @Bean
    public RegistryCatalogImportSchema customerTypesImportSchema() {
        return new RegistryCatalogImportSchema("registry.customer_types", "Tipos de cliente",
                "customer_types", List.of(
                        new Extra("applies_to", TEXT, false, List.of("PF", "PJ", "BOTH"), "BOTH"),
                        new Extra("is_public_entity", BOOLEAN, false, List.of(), "nao")));
    }

    @Bean
    public RegistryCatalogImportSchema propertyProfilesImportSchema() {
        return new RegistryCatalogImportSchema("registry.property_profiles", "Perfis do imóvel",
                "property_profiles", List.of(
                        new Extra("provisional_only", BOOLEAN, false, List.of(), "nao")));
    }

    @Bean
    public RegistryCatalogImportSchema relationRolesImportSchema() {
        return new RegistryCatalogImportSchema("registry.relation_roles", "Papéis de vínculo",
                "relation_roles", List.of(
                        new Extra("system_role", TEXT, true,
                                List.of("OWNER", "USER", "RESPONSIBLE"), "OWNER")));
    }

    @Bean
    public RegistryCatalogImportSchema documentExemptionReasonsImportSchema() {
        return new RegistryCatalogImportSchema("registry.document_exemption_reasons",
                "Motivos de dispensa de documento", "document_exemption_reasons", List.of(
                        new Extra("requires_permission", BOOLEAN, false, List.of(), "nao")));
    }

    @Bean
    public RegistryCatalogImportSchema customerDeleteReasonsImportSchema() {
        return new RegistryCatalogImportSchema("registry.customer_delete_reasons",
                "Motivos de exclusão de cliente", "customer_delete_reasons", List.of());
    }

    @Bean
    public RegistryCatalogImportSchema unitLinkEndReasonsImportSchema() {
        return new RegistryCatalogImportSchema("registry.unit_link_end_reasons",
                "Motivos de fim de vínculo", "unit_link_end_reasons", List.of());
    }

    @Bean
    public RegistryCatalogImportSchema sewerDischargesImportSchema() {
        return new RegistryCatalogImportSchema("registry.sewer_discharges",
                "Destinos de esgoto", "sewer_discharges", List.of());
    }

    @Bean
    public RegistryCatalogImportSchema consentPurposesImportSchema() {
        return new RegistryCatalogImportSchema("registry.consent_purposes",
                "Finalidades de consentimento", "consent_purposes", List.of());
    }

    @Bean
    public RegistryCatalogImportSchema ibgeMunicipalitiesImportSchema() {
        return new RegistryCatalogImportSchema("registry.ibge_municipalities",
                "Municípios do IBGE", "ibge_municipalities", List.of(
                        new Extra("state", TEXT, true, UF, "SC")));
    }
}
