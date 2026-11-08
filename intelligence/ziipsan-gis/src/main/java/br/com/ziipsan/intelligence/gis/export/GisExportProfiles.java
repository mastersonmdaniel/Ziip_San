package br.com.ziipsan.intelligence.gis.export;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Perfis de exportação do §9.2. CORSAN: os nomes de camada são os do
 * Anexo A (rev. 03/2024) TRANSCRITOS do §9.2 do GIS-CADASTRO-TECNICO
 * — fonte do projeto, não leiaute de piloto (o piloto real, P0.30,
 * ainda não foi escolhido e nenhum leiaute dele existe no projeto).
 * SANEAGO: o §9.2 só documenta o modelo Giswater ({@code arc},
 * {@code node}, {@code connec}) e declara o conteúdo da IN 00.0229
 * (rev. 04) "a validar na leitura da IN" — por isso o perfil Saneago
 * nasce com {@code validated() == false} e a exportação por ele é
 * PROPOSTA, nunca entrega oficial à companhia.
 */
public final class GisExportProfiles {

    public enum Profile { GENERIC, CORSAN_ANEXO_A, SANEAGO_GISWATER_PROPOSTA }

    /** Camadas Corsan do §9.2 (água A_* e esgoto E_*). */
    public static final List<String> CORSAN_LAYERS = List.of(
        "A_REDE","A_CONEXOES","A_VALVULA","A_HIDRANTE","A_PROTEHIDRAULICAS","A_RAMAIS",
        "A_MACROMEDIDOR","A_PCP","A_PCQ","A_POCO","A_RESERVATORIO","A_EEAT","A_MOTOBOMBA",
        "A_ETA","A_CAPTACOES","A_SETOR_FORNECIMENTO",
        "E_REDE","E_POCO_VISITA","E_TIL","E_CX_LIGACAO","E_CX_DESCARGA","E_RAMAL",
        "E_VALVULAS","E_EEE","E_MOTOBOMBA","E_ETE","E_BACIA","E_SUB_BACIA");

    /** Tabelas do modelo Giswater citadas no §9.2 para a Saneago. */
    public static final List<String> SANEAGO_GISWATER_TABLES = List.of("arc","node","connec");

    private GisExportProfiles(){}

    public static boolean validated(Profile p){ return p != Profile.SANEAGO_GISWATER_PROPOSTA; }

    /** Camada de destino do perfil para um tipo de feição (Corsan: mapa do §5/§9.2; genérico: a própria camada). */
    public static String layerFor(Profile p, GisExportFeature f){
        if (p == Profile.SANEAGO_GISWATER_PROPOSTA) {
            return switch (f.geometryType()) {
                case "LINESTRING" -> "arc";
                case "POINT" -> f.typeCode() != null && f.typeCode().contains("SERVICE") ? "connec" : "node";
                default -> "node";
            };
        }
        if (p == Profile.CORSAN_ANEXO_A) {
            String t = f.typeCode() == null ? "" : f.typeCode();
            if (t.contains("VALVE")) return f.layerKey().startsWith("sewer") ? "E_VALVULAS" : "A_VALVULA";
            if (t.contains("MANHOLE")) return "E_POCO_VISITA";
            if (t.contains("HYDRANT")) return "A_HIDRANTE";
            if (t.contains("SERVICE")) return f.layerKey().startsWith("sewer") ? "E_RAMAL" : "A_RAMAIS";
            return f.layerKey().startsWith("sewer") ? "E_REDE" : "A_REDE";
        }
        return f.layerKey();
    }

    /**
     * Mapa de nomes do Shapefile (§9.3: atributo até 10 caracteres):
     * trunca e desambigua com sufixo numérico, de forma determinística.
     */
    public static Map<String,String> shapefileNameMap(List<String> attributeNames){
        Map<String,String> out = new LinkedHashMap<>();
        for (String name : attributeNames) {
            String base = name.length() <= 10 ? name : name.substring(0,10);
            String candidate = base; int i = 1;
            while (out.containsValue(candidate)) {
                String suffix = String.valueOf(i++);
                candidate = base.substring(0, 10 - suffix.length()) + suffix;
            }
            out.put(name, candidate);
        }
        return Map.copyOf(out);
    }
}
