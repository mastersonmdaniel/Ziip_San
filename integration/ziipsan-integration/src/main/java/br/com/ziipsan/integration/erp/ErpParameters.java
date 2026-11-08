package br.com.ziipsan.integration.erp;

import java.util.Map;
import java.util.Objects;

/**
 * ED-62: parametros do tenant para a integracao ERP, no mesmo espirito do
 * hub da P3.37: ausencia e recusa NOMEADA ([A DEFINIR]) — nunca leiaute,
 * versao ou oficialidade presumidos.
 *
 * <p>Chaves (classe d, por fornecedor {@code <p>} = betha|ipm|fiorilli):
 * <pre>
 *   erp.&lt;p&gt;.layout_version   versao do leiaute/API DECLARADA na contratacao/homologacao
 *   erp.&lt;p&gt;.stock_official   "ZIIPSAN" | "ERP" (ADR-0032)
 *   erp.&lt;p&gt;.transport       "API" | "FILE" (canal do aceite: por API ou arquivo)
 * </pre>
 * Os valores chegam como texto simples ja resolvido das versoes de
 * parametro vigentes e aceitas (P0.19/P1.6) pela fiacao; esta classe so
 * interpreta e valida.
 */
public final class ErpParameters {

    public enum Transport { API, FILE }

    private final Map<String, String> valueByKey;

    private ErpParameters(Map<String, String> valueByKey) { this.valueByKey = valueByKey; }

    public static ErpParameters fromValues(Map<String, String> valueByKey) {
        Objects.requireNonNull(valueByKey, "mapa de parametros erp.* e obrigatorio");
        return new ErpParameters(Map.copyOf(valueByKey));
    }

    private static String slug(ErpProvider provider) {
        return provider.name().toLowerCase(java.util.Locale.ROOT);
    }

    public static String layoutVersionKey(ErpProvider p) { return "erp." + slug(p) + ".layout_version"; }
    public static String stockOfficialKey(ErpProvider p) { return "erp." + slug(p) + ".stock_official"; }
    public static String transportKey(ErpProvider p) { return "erp." + slug(p) + ".transport"; }

    /** Versao do leiaute DECLARADA (nunca presumida de fornecedor real). */
    public String layoutVersion(ErpProvider provider) { return required(layoutVersionKey(provider)); }

    /** Oficialidade do estoque (ADR-0032): decide se movimentos sao exportados. */
    public ErpProvider.StockOfficial stockOfficial(ErpProvider provider) {
        String raw = required(stockOfficialKey(provider));
        try {
            return ErpProvider.StockOfficial.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(stockOfficialKey(provider) + ": esperado ZIIPSAN ou ERP, veio " + raw, e);
        }
    }

    public Transport transport(ErpProvider provider) {
        String raw = required(transportKey(provider));
        try {
            return Transport.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(transportKey(provider) + ": esperado API ou FILE, veio " + raw, e);
        }
    }

    private String required(String key) {
        String v = valueByKey.get(key);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("parametro " + key + " [A DEFINIR]: sem valor vigente do tenant, a integracao ERP nao opera");
        }
        return v;
    }
}
