package br.com.ziipsan.commercial.registry.application;

import java.util.List;

/**
 * ED-12: os 22 catálogos auxiliares da SAMAE 2.8.10, na lista fechada do
 * levantamento 18d, seção E2 ("Catálogos auxiliares (SAMAE 2.8.10)"):
 * tabelas simples (código, descrição, ativo) dos catálogos que não pertencem
 * a outra entrega — cargos, estado civil, nacionalidade, veículos e modelos,
 * materiais e tipos, unidade de medida, pavimentos, tipo de rede, tipo de
 * cobrança e os motivos do faturamento e da arrecadação.
 *
 * <p>Este registro é a fonte única da lista nesta entrega: cada entrada traz
 * o requisito SG de origem (18c/18d), o nome do catálogo, a tabela proposta
 * (padrão V3_12: {@code id, tenant_id, code} único por tenant, {@code name,
 * active}, auditoria), o módulo dono (18d E2: "cada catálogo junto do módulo
 * dono") e se o catálogo é dado pessoal que só se coleta com finalidade
 * definida (estado civil e nacionalidade, 18d E2: "só com finalidade
 * definida" — a finalidade é a do registro de
 * {@code CustomerIdentityService} quando o campo for do cliente).
 *
 * <p>Estado real, declarado e não escondido: NENHUMA dessas 22 tabelas
 * existe hoje no banco (conferido nas migrations do registry até a V14_4 e
 * no levantamento 18d, que marca cada um como "sem tabela"). O mapa Flyway
 * do plano (§3) não reserva versão para a ED-12, e número de migration não
 * se inventa: esta entrega NÃO cria as tabelas. O que ela entrega é o
 * recurso genérico pronto para elas —
 * {@code RegistryCatalogService}/{@code CatalogLookup} (CRUD genérico
 * existente, P1.3) e {@code RegistryCatalogImportSchema} (carga pelo
 * importador da P1.40 aplicada por OS de lote, ED-12) — mais este registro
 * conferido por teste. Quando a migration da sequência global (pós-P3.12,
 * número na ordem do merge, com o módulo no nome) criar uma dessas tabelas,
 * ela entra na allowlist do CRUD e ganha o seu esquema de importação sem
 * código novo de importador.
 */
public final class SamaeAuxiliaryCatalogs {

    private SamaeAuxiliaryCatalogs() {
    }

    /**
     * Um catálogo auxiliar da SAMAE 2.8.10.
     *
     * @param sg             requisito de origem no edital SAMAE (18c §4 / 18d)
     * @param name           nome do catálogo, como no levantamento 18d
     * @param proposedTable  tabela proposta (padrão V3_12), a criar por migration futura
     * @param ownerModule    módulo dono do catálogo (18d E2)
     * @param personalData   dado pessoal: só coletar com finalidade definida e registrada
     */
    public record AuxiliaryCatalog(String sg, String name, String proposedTable,
                                   String ownerModule, boolean personalData) {
    }

    /** Os 22, na ordem da lista de requisitos cobertos do 18d E2. */
    public static final List<AuxiliaryCatalog> ALL = List.of(
            new AuxiliaryCatalog("SG-095", "Cadastro de cargos", "job_positions", "platform", false),
            new AuxiliaryCatalog("SG-102", "Cadastro de estado civil", "marital_statuses", "registry", true),
            new AuxiliaryCatalog("SG-104", "Cadastro de veículos", "vehicles", "fleet", false),
            new AuxiliaryCatalog("SG-105", "Cadastro de material", "materials", "inventory", false),
            new AuxiliaryCatalog("SG-107", "Cadastro de modelo de veículo", "vehicle_models", "fleet", false),
            new AuxiliaryCatalog("SG-108", "Cadastro de motivo de cancelamento", "cancellation_reasons", "billing", false),
            new AuxiliaryCatalog("SG-110", "Cadastro de motivo de emissão de fatura", "bill_issue_reasons", "billing", false),
            new AuxiliaryCatalog("SG-111", "Cadastro de motivo de estorno de pagamento", "payment_reversal_reasons", "collection", false),
            new AuxiliaryCatalog("SG-112", "Cadastro de motivo de liberação de compensação", "compensation_release_reasons", "collection", false),
            new AuxiliaryCatalog("SG-115", "Cadastro de motivo de reenvio de fatura em débito automático", "direct_debit_resend_reasons", "collection", false),
            new AuxiliaryCatalog("SG-116", "Cadastro de motivo de situação de fatura", "bill_status_reasons", "billing", false),
            new AuxiliaryCatalog("SG-119", "Cadastro de motivo de transferência de pagamento", "payment_transfer_reasons", "collection", false),
            new AuxiliaryCatalog("SG-120", "Cadastro de motivo de valores a compensar", "compensation_reasons", "collection", false),
            new AuxiliaryCatalog("SG-121", "Cadastro de nacionalidade", "nationalities", "registry", true),
            new AuxiliaryCatalog("SG-122", "Cadastro de ocorrência de débito em conta", "direct_debit_occurrences", "collection", false),
            new AuxiliaryCatalog("SG-127", "Cadastro de tipo de cobrança", "billing_types", "collection", false),
            new AuxiliaryCatalog("SG-137", "Cadastro de tipo de material", "material_types", "inventory", false),
            new AuxiliaryCatalog("SG-138", "Cadastro de tipo de pavimento de logradouro", "street_pavements", "registry", false),
            new AuxiliaryCatalog("SG-139", "Cadastro de tipo de pavimento de calcada", "sidewalk_pavements", "registry", false),
            new AuxiliaryCatalog("SG-140", "Cadastro de tipo de rede", "network_types", "gis", false),
            new AuxiliaryCatalog("SG-143", "Cadastro de tipo de unidade de medida", "measurement_units", "inventory", false),
            new AuxiliaryCatalog("SG-144", "Cadastro de tipo de veículo", "vehicle_types", "fleet", false));

    /** Catálogo pelo requisito SG, ou {@code null} se não é um dos 22. */
    public static AuxiliaryCatalog bySg(String sg) {
        if (sg == null) {
            return null;
        }
        return ALL.stream().filter(c -> c.sg().equals(sg.trim())).findFirst().orElse(null);
    }
}
