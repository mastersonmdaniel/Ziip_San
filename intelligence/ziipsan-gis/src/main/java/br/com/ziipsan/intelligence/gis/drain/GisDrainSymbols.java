package br.com.ziipsan.intelligence.gis.drain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Símbolos de drenagem (GIS-13): as CHAVES e o manifesto dos símbolos
 * do §4.4, no mesmo desenho da GIS-06/P1.30 e da ED-65 — a fonte única
 * de estilo é a {@code gis_style} (V13_12) e o {@code style_key} do
 * tipo aponta para um símbolo SVG do pacote de vertical (ADR-0036).
 *
 * <p><b>MARCADOR DECLARADO (não é entrega de arte):</b> o documento
 * {@code ICONES-E-SIMBOLOGIA.md} — cuja §4.3 o aceite cita para os
 * símbolos de drenagem — NÃO existe nesta VM nem nos espelhos locais
 * (só é referenciado pelo GIS-CADASTRO-TECNICO §10.2), e a arte
 * SVG/sprite é entregável do pipeline da P1.30, que também não está
 * nesta VM. Como na P3.25 ("não inventei desenho") e na ED-65, esta
 * classe NÃO contém SVG nenhum: ela gera o INVENTÁRIO de chaves por
 * tipo e estado, a regra de cobertura (tipo ativo sem símbolo =
 * recusado, como o teste de cobertura da P1.30) e o manifesto que o
 * pipeline consome quando a arte existir. O ACEITE DO DONO sobre os
 * símbolos — exigido pelo acceptance — é pendência do dono,
 * registrada no relatório; nada aqui o presume.
 *
 * <p>Estados: o aceite pede "cinco estados". Os estados de
 * APRESENTAÇÃO que o projeto define para símbolos são os do ciclo de
 * vida (§3.7: PLANNED, UNDER_CONSTRUCTION, IN_SERVICE, ABANDONED —
 * os mesmos de {@code GisStyleRules.LIFECYCLE_STATUSES}) mais o
 * estado de precisão fora da meta (§10.2: fora da meta ganha halo/
 * marcador próprio — apresentação distinta no mapa). A lista é
 * PARÂMETRO da geração ({@link #SYMBOL_STATES} é o padrão declarado
 * desta entrega, idêntico ao da ED-65): se a §4.3, quando lida,
 * nomear outro quinto estado, a troca é de DADO nesta lista — a
 * cobertura e o manifesto não mudam. Esta leitura está declarada no
 * relatório como ponto a confirmar com o documento-fonte.
 */
public final class GisDrainSymbols {

    /**
     * Estados de apresentação do símbolo (ver javadoc da classe):
     * 4 de ciclo de vida (§3.7) + OUT_OF_TARGET (§10.2).
     */
    public static final List<String> SYMBOL_STATES = List.of(
            "PLANNED", "UNDER_CONSTRUCTION", "IN_SERVICE", "ABANDONED",
            "OUT_OF_TARGET");

    private GisDrainSymbols() {
    }

    /** Chave do símbolo de um tipo num estado: {@code <key>@<state>}. */
    public static String symbolKeyFor(GisDrainCatalog.DrainType type, String state) {
        if (!SYMBOL_STATES.contains(state)) {
            throw new IllegalArgumentException("estado de simbolo fora da lista declarada: " + state);
        }
        return type.symbolKey() + "@" + state;
    }

    /**
     * Manifesto (inventário) completo: tipo -> estado -> chave, para
     * todos os tipos do §4.4. É o que o pipeline da P1.30 consome
     * para gerar sprite MapLibre, símbolos QGIS, SLD e SVG de legenda
     * da MESMA fonte (P1.30) — a geração em si é da P1.30.
     */
    public static Map<String, Map<String, String>> manifest() {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        for (GisDrainCatalog.DrainType type : GisDrainCatalog.TYPES) {
            Map<String, String> byState = new LinkedHashMap<>();
            for (String state : SYMBOL_STATES) {
                byState.put(state, symbolKeyFor(type, state));
            }
            out.put(type.code(), Map.copyOf(byState));
        }
        return Map.copyOf(out);
    }

    /** Todas as chaves esperadas (tipos × estados): 20 × 5 = 100. */
    public static List<String> expectedKeys() {
        List<String> keys = new ArrayList<>();
        for (GisDrainCatalog.DrainType type : GisDrainCatalog.TYPES) {
            for (String state : SYMBOL_STATES) {
                keys.add(symbolKeyFor(type, state));
            }
        }
        return List.copyOf(keys);
    }

    /**
     * Regra de cobertura (espelho do teste de cobertura da P1.30:
     * "recusa tipo ativo sem símbolo"): devolve as chaves que faltam
     * na biblioteca para os tipos ATIVOS dados. Lista vazia = coberto.
     * Com a biblioteca vazia (arte da P1.30 pendente), devolve TODAS —
     * é o estado real hoje, declarado, não maquiado.
     */
    public static List<String> missingSymbols(Set<String> activeTypeCodes,
                                              Set<String> librarySymbolKeys) {
        List<String> missing = new ArrayList<>();
        for (GisDrainCatalog.DrainType type : GisDrainCatalog.TYPES) {
            if (!activeTypeCodes.contains(type.code())) {
                continue;
            }
            for (String state : SYMBOL_STATES) {
                String key = symbolKeyFor(type, state);
                if (!librarySymbolKeys.contains(key)) {
                    missing.add(key);
                }
            }
        }
        return List.copyOf(missing);
    }
}
