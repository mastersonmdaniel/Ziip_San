package br.com.ziipsan.commercial.billing.bill;

import br.com.ziipsan.core.json.Json;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fotografia do titular faturado no momento da emissao (DAD-15): nome, documento
 * <b>mascarado</b> e endereco de entrega. E o conteudo que a conta guarda em
 * {@code bills.billed_customer_snapshot} -- mas a coluna so recebe este JSON
 * <b>cifrado</b> pelo {@link BillSnapshotService}; este record, sozinho, nunca e
 * persistido.
 *
 * <p>O documento entra mascarado de proposito (NR 11, art. 33, paragrafo 2; CDC,
 * art. 42-A): a conta nao precisa do documento inteiro para provar quem foi
 * faturado, e o documento em claro continua so no cadastro, protegido pelo
 * FieldProtector. Por isso o construtor recusa documento sem mascara.
 */
public record BilledCustomerSnapshot(String name, String maskedDocument, String deliveryAddress) {

    public BilledCustomerSnapshot {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("nome do titular faturado e obrigatorio no snapshot");
        }
        if (maskedDocument == null || !maskedDocument.contains("*")) {
            throw new IllegalArgumentException(
                    "documento do snapshot deve ser o mascarado (com '*'): documento em claro nao entra na conta");
        }
        if (deliveryAddress == null || deliveryAddress.isBlank()) {
            throw new IllegalArgumentException("endereco de entrega e obrigatorio no snapshot");
        }
    }

    /** JSON do snapshot, SO pela classe Json do core. A ordem das chaves e estavel. */
    public String toJson() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("name", name);
        fields.put("maskedDocument", maskedDocument);
        fields.put("deliveryAddress", deliveryAddress);
        return Json.write(fields);
    }

    /** Le o JSON decifrado de volta. Campo faltando e erro explicito, nao snapshot pela metade. */
    public static BilledCustomerSnapshot fromJson(String json) {
        Map<String, Object> fields = Json.readMap(json);
        return new BilledCustomerSnapshot(
                required(fields, "name"), required(fields, "maskedDocument"), required(fields, "deliveryAddress"));
    }

    private static String required(Map<String, Object> fields, String key) {
        Object value = fields.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalStateException("snapshot do titular faturado sem o campo " + key);
        }
        return text;
    }
}
