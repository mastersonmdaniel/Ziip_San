package br.com.ziipsan.reporting.bi;

import java.util.Objects;

/**
 * ED-47: marca do produto no Superset ("Superset com marca do
 * produto").
 *
 * <p>Fato do projeto: o nome do produto é ZiipSan. O resto da marca —
 * logotipo, cores, tema — é do pacote de marca da P0.34 e dos temas da
 * P0.35/P0.36, e NÃO é reinventado aqui: este record recebe os ativos
 * como parâmetro de fiação (caminho do logotipo e cor primária no
 * formato CSS) e recusa marca incompleta, para o Superset nunca subir
 * com a marca padrão do Apache Superset por esquecimento. Enquanto a
 * fiação não fornecer os ativos da P0.34, o BI não é apresentado como
 * pronto — pendência declarada no relatório, não preenchida com cor
 * inventada.
 */
public record SupersetBranding(
        String appName,
        String logoAssetPath,
        String primaryColorCss) {

    public static final String PRODUCT_NAME = "ZiipSan";

    public SupersetBranding {
        if (!PRODUCT_NAME.equals(appName)) {
            throw new IllegalArgumentException("O BI embarcado usa a marca do produto: " + PRODUCT_NAME);
        }
        if (logoAssetPath == null || logoAssetPath.isBlank()) {
            throw new IllegalArgumentException(
                    "Logotipo do pacote de marca (P0.34) é obrigatório para habilitar o BI");
        }
        if (primaryColorCss == null || !primaryColorCss.matches("#[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException(
                    "Cor primária da marca (P0.34) deve vir como #RRGGBB do pacote de marca");
        }
        Objects.requireNonNull(appName, "appName é obrigatório");
    }
}
