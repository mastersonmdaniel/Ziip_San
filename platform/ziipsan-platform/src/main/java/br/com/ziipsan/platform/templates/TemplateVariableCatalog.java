package br.com.ziipsan.platform.templates;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Catálogo de variáveis tipadas por tipo de documento (P2.30). Como os
 * perfis de fábrica da P1.37, o catálogo vive em código e é só leitura para
 * a aplicação: variável fora do catálogo não existe para o motor, e é por
 * isso que a validação consegue recusar o modelo em vez de renderizar um
 * buraco no documento.
 *
 * <p>Cada variável tem um valor sintético de exemplo: é a massa sintética da
 * prévia (nenhum dado real passa pela prévia — a prévia nunca recebe dado
 * do titular). Os valores são genéricos de propósito (nomes como "Fulano de
 * Tal" não existem aqui: o exemplo usa marcadores óbvios, como
 * "NOME EXEMPLO").
 *
 * <p>Limite declarado: o catálogo desta frente é fixo em código. Variável
 * nova por tenant (ou por tipo novo) é extensão futura, não improvisada
 * aqui — um tipo sem variável no catálogo ainda aceita modelo, desde que o
 * corpo não referencie variável nenhuma.
 */
public final class TemplateVariableCatalog {

    /** Uma variável do catálogo: nome, tipo e valor sintético da prévia. */
    public record VariableDef(String name, TemplateVariableType type, String sampleValue) {
    }

    private static VariableDef v(String name, TemplateVariableType type, String sample) {
        return new VariableDef(name, type, sample);
    }

    private static final Map<TemplateDocumentType, List<VariableDef>> BY_TYPE;

    static {
        Map<TemplateDocumentType, List<VariableDef>> map = new LinkedHashMap<>();
        // Variáveis comuns a documentos dirigidos ao titular.
        List<VariableDef> titular = List.of(
                v("customer_name", TemplateVariableType.TEXT, "NOME EXEMPLO"),
                v("registration_number", TemplateVariableType.TEXT, "000000-0"),
                v("protocol_number", TemplateVariableType.TEXT, "2026-000001"),
                v("amount", TemplateVariableType.MONEY, "123.45"),
                v("due_date", TemplateVariableType.DATE, "2026-12-10"));
        map.put(TemplateDocumentType.PROTOCOL_RECEIPT, List.of(
                v("customer_name", TemplateVariableType.TEXT, "NOME EXEMPLO"),
                v("protocol_number", TemplateVariableType.TEXT, "2026-000001"),
                v("request_summary", TemplateVariableType.TEXT, "Solicitação exemplo"),
                v("service_unit", TemplateVariableType.TEXT, "Unidade exemplo")));
        map.put(TemplateDocumentType.USER_RESPONSE, List.of(
                v("customer_name", TemplateVariableType.TEXT, "NOME EXEMPLO"),
                v("protocol_number", TemplateVariableType.TEXT, "2026-000001"),
                v("response_text", TemplateVariableType.TEXT, "Resposta exemplo"),
                v("service_unit", TemplateVariableType.TEXT, "Unidade exemplo")));
        map.put(TemplateDocumentType.CUT_NOTICE, titular);
        map.put(TemplateDocumentType.COLLECTION_LETTER, titular);
        map.put(TemplateDocumentType.INSTALLMENT_TERM, List.of(
                v("customer_name", TemplateVariableType.TEXT, "NOME EXEMPLO"),
                v("registration_number", TemplateVariableType.TEXT, "000000-0"),
                v("protocol_number", TemplateVariableType.TEXT, "2026-000001"),
                v("total_amount", TemplateVariableType.MONEY, "1200.00"),
                v("installment_count", TemplateVariableType.NUMBER, "12"),
                v("installment_amount", TemplateVariableType.MONEY, "100.00"),
                v("first_due_date", TemplateVariableType.DATE, "2026-12-10")));
        map.put(TemplateDocumentType.INFRACTION_NOTICE, List.of(
                v("customer_name", TemplateVariableType.TEXT, "NOME EXEMPLO"),
                v("registration_number", TemplateVariableType.TEXT, "000000-0"),
                v("protocol_number", TemplateVariableType.TEXT, "2026-000001"),
                v("infraction_description", TemplateVariableType.TEXT, "Descrição exemplo"),
                v("appeal_days", TemplateVariableType.NUMBER, "30")));
        map.put(TemplateDocumentType.CERTIFICATE, List.of(
                v("customer_name", TemplateVariableType.TEXT, "NOME EXEMPLO"),
                v("registration_number", TemplateVariableType.TEXT, "000000-0"),
                v("certificate_text", TemplateVariableType.TEXT, "Texto exemplo da certidão"),
                v("issue_date", TemplateVariableType.DATE, "2026-11-04")));
        map.put(TemplateDocumentType.CDA, List.of(
                v("customer_name", TemplateVariableType.TEXT, "NOME EXEMPLO"),
                v("registration_number", TemplateVariableType.TEXT, "000000-0"),
                v("cda_number", TemplateVariableType.TEXT, "2026-0001"),
                v("amount", TemplateVariableType.MONEY, "123.45"),
                v("due_date", TemplateVariableType.DATE, "2026-12-10")));
        map.put(TemplateDocumentType.SERVICE_ORDER_PRINT, List.of(
                v("service_order_number", TemplateVariableType.TEXT, "OS-000001"),
                v("registration_number", TemplateVariableType.TEXT, "000000-0"),
                v("service_address", TemplateVariableType.TEXT, "Endereço exemplo"),
                v("service_description", TemplateVariableType.TEXT, "Serviço exemplo")));
        map.put(TemplateDocumentType.LAB_REPORT, List.of(
                v("sample_id", TemplateVariableType.TEXT, "AMOSTRA-0001"),
                v("result_text", TemplateVariableType.TEXT, "Resultado exemplo"),
                v("issue_date", TemplateVariableType.DATE, "2026-11-04")));
        map.put(TemplateDocumentType.AUDIT_REPORT, List.of(
                v("audit_period", TemplateVariableType.TEXT, "2026-10"),
                v("finding_summary", TemplateVariableType.TEXT, "Achado exemplo"),
                v("issue_date", TemplateVariableType.DATE, "2026-11-04")));
        map.put(TemplateDocumentType.EMAIL, titular);
        map.put(TemplateDocumentType.SMS, List.of(
                v("customer_name", TemplateVariableType.TEXT, "NOME EXEMPLO"),
                v("amount", TemplateVariableType.MONEY, "123.45"),
                v("due_date", TemplateVariableType.DATE, "2026-12-10")));
        map.put(TemplateDocumentType.WHATSAPP, List.of(
                v("customer_name", TemplateVariableType.TEXT, "NOME EXEMPLO"),
                v("amount", TemplateVariableType.MONEY, "123.45"),
                v("due_date", TemplateVariableType.DATE, "2026-12-10")));
        BY_TYPE = Map.copyOf(map);
    }

    private TemplateVariableCatalog() {
    }

    /** Variáveis do catálogo para o tipo de documento, na ordem de exibição. */
    public static List<VariableDef> variablesFor(TemplateDocumentType type) {
        return BY_TYPE.getOrDefault(type, List.of());
    }

    /** A definição da variável no catálogo do tipo, ou vazio se não existe. */
    public static Optional<VariableDef> variable(TemplateDocumentType type, String name) {
        return variablesFor(type).stream().filter(d -> d.name().equals(name)).findFirst();
    }

    /** Massa sintética da prévia: nome -> valor de exemplo, para o tipo. */
    public static Map<String, String> sampleValues(TemplateDocumentType type) {
        Map<String, String> samples = new LinkedHashMap<>();
        for (VariableDef def : variablesFor(type)) {
            samples.put(def.name(), def.sampleValue());
        }
        return Map.copyOf(samples);
    }
}
