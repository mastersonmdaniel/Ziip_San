package br.com.ziipsan.integration.erp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * ED-62: mapeamento de leiaute DECLARADO — campo canonico do ZiipSan ->
 * nome do campo no leiaute do ERP, como configurado na implantacao a
 * partir do leiaute publicado pelo fornecedor. NENHUM mapeamento de
 * Betha/IPM/Fiorilli vem pronto nesta entrega: sem mapeamento declarado
 * para um campo exigido, a exportacao RECUSA nomeando o campo — ela
 * nunca inventa nome de campo de fornecedor real.
 *
 * <p>Campos canonicos desta entrega (exatamente os emitidos por
 * {@code ErpPayloads}, carga a carga):
 * contabilidade: {@code data, mes_contabil, evento, conta, dc, valor,
 * historico, origem, centro_custo};
 * previsao: {@code material, quantidade, natureza_despesa, referencia};
 * movimento: {@code material, tipo_movimento, quantidade,
 * natureza_despesa, referencia};
 * contrato: {@code contrato, empenho, ordem_compra_externa, pncp}.
 */
public final class ErpLayoutMapping {

    private final ErpProvider provider;
    private final String layoutVersion;
    private final Map<String, String> fieldByCanonical;

    private ErpLayoutMapping(ErpProvider provider, String layoutVersion, Map<String, String> fieldByCanonical) {
        this.provider = provider;
        this.layoutVersion = layoutVersion;
        this.fieldByCanonical = fieldByCanonical;
    }

    public static ErpLayoutMapping declared(ErpProvider provider, String layoutVersion, Map<String, String> fieldByCanonical) {
        Objects.requireNonNull(provider, "fornecedor do mapeamento e obrigatorio");
        if (layoutVersion == null || layoutVersion.isBlank()) {
            throw new IllegalArgumentException("versao do leiaute declarada e obrigatoria (ED-62: nenhum leiaute presumido)");
        }
        Objects.requireNonNull(fieldByCanonical, "mapa campo canonico -> campo do ERP e obrigatorio");
        if (fieldByCanonical.isEmpty()) {
            throw new IllegalArgumentException("mapeamento vazio nao e mapeamento declarado");
        }
        Map<String, String> copy = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : fieldByCanonical.entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank() || e.getValue() == null || e.getValue().isBlank()) {
                throw new IllegalArgumentException("mapeamento exige campo canonico e campo do ERP nao vazios");
            }
            if (copy.put(e.getKey(), e.getValue()) != null) {
                throw new IllegalArgumentException("campo canonico repetido no mapeamento: " + e.getKey());
            }
        }
        return new ErpLayoutMapping(provider, layoutVersion, Map.copyOf(copy));
    }

    public ErpProvider provider() { return provider; }
    public String layoutVersion() { return layoutVersion; }

    /** Campo do ERP para o canonico; ausencia e recusa nomeada. */
    public String requireField(String canonicalField) {
        String mapped = fieldByCanonical.get(canonicalField);
        if (mapped == null) {
            throw new IllegalStateException("leiaute " + provider.displayName() + " v" + layoutVersion
                    + ": campo canonico '" + canonicalField + "' sem mapeamento declarado [A DEFINIR]"
                    + " — nenhum nome de campo de fornecedor e inventado");
        }
        return mapped;
    }

    /**
     * Renderiza uma linha canonica (pares campo=valor, na ordem dada) no
     * leiaute declarado: {@code <campoERP>=<valor>} separados por ';'.
     * Valores sao sanitizados (';' e quebras viram espaco), como na
     * exportacao diaria da ED-32.
     */
    public String render(List<Map.Entry<String, String>> canonicalLine) {
        Objects.requireNonNull(canonicalLine, "linha canonica e obrigatoria");
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> pair : canonicalLine) {
            if (sb.length() > 0) sb.append(';');
            sb.append(requireField(pair.getKey())).append('=').append(sanitize(pair.getValue()));
        }
        return sb.toString();
    }

    static String sanitize(String s) {
        return s == null ? "" : s.replace(';', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
