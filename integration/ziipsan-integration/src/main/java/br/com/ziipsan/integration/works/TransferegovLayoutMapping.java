package br.com.ziipsan.integration.works;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * ED-102: mapeamento de leiaute DECLARADO da prestacao de contas —
 * campo canonico desta entrega -> nome do campo no leiaute de destino,
 * no mesmo padrao do {@code ErpLayoutMapping} da ED-62.
 *
 * <p>[SEM FONTE] O leiaute oficial de importacao/prestacao de contas
 * do Transferegov NAO esta nas fontes locais do projeto. Por isso:
 * (a) {@code officialLayout} nasce {@code false} e nenhum mapeamento
 * oficial vem pronto; (b) sem mapeamento declarado para um campo
 * exigido, a montagem da prestacao RECUSA nomeando o campo — nunca
 * inventa campo oficial do Transferegov. Quando o leiaute oficial for
 * obtido (pendencia P-ED102-01), ele entra SOMENTE como um mapeamento
 * declarado novo, sem tocar no dominio.
 */
public final class TransferegovLayoutMapping {

    /** Campos canonicos emitidos pela prestacao de contas (C4). */
    public static final List<String> CANONICAL_FIELDS = List.of(
            "convenio_ref", "esfera", "tipo_instrumento", "concedente",
            "valor_total", "valor_concedente", "valor_contrapartida",
            "vigencia_inicio", "vigencia_fim", "projeto_gis_ref",
            "medicao_sequencia", "medicao_periodo_inicio", "medicao_periodo_fim",
            "medicao_percentual_periodo", "medicao_percentual_acumulado",
            "medicao_valor_periodo", "medicao_valor_acumulado",
            "cronograma_percentual_previsto", "cronograma_valor_previsto");

    private final String layoutVersion;
    private final boolean officialLayout;
    private final Map<String, String> fieldByCanonical;

    private TransferegovLayoutMapping(String layoutVersion, boolean officialLayout,
                                      Map<String, String> fieldByCanonical) {
        this.layoutVersion = layoutVersion;
        this.officialLayout = officialLayout;
        this.fieldByCanonical = fieldByCanonical;
    }

    /**
     * @param officialLayout so pode ser {@code true} quando o leiaute
     *        vier da fonte oficial — [SEM FONTE] nesta entrega, logo
     *        qualquer chamada com {@code true} e recusada.
     */
    public static TransferegovLayoutMapping declared(String layoutVersion, boolean officialLayout,
                                                      Map<String, String> fieldByCanonical) {
        if (layoutVersion == null || layoutVersion.isBlank())
            throw new IllegalArgumentException("versao do leiaute declarada e obrigatoria");
        if (officialLayout)
            throw new IllegalArgumentException("[SEM FONTE] leiaute oficial do Transferegov nao esta nas fontes locais; officialLayout=true e recusado (P-ED102-01)");
        Objects.requireNonNull(fieldByCanonical, "mapa de campos declarado e obrigatorio");
        return new TransferegovLayoutMapping(layoutVersion, false,
                Map.copyOf(new LinkedHashMap<>(fieldByCanonical)));
    }

    public String layoutVersion() { return layoutVersion; }
    public boolean officialLayout() { return officialLayout; }

    /** Traduz um campo canonico; recusa nomeando o campo sem mapeamento. */
    public String fieldFor(String canonical) {
        String mapped = fieldByCanonical.get(canonical);
        if (mapped == null || mapped.isBlank())
            throw new IllegalArgumentException("sem mapeamento declarado para o campo canonico: " + canonical
                    + " ([SEM FONTE] nenhum campo oficial do Transferegov e presumido)");
        return mapped;
    }

    /** Valida que TODOS os campos canonicos exigidos tem mapeamento. */
    public void requireComplete() {
        for (String f : CANONICAL_FIELDS) fieldFor(f);
    }
}
