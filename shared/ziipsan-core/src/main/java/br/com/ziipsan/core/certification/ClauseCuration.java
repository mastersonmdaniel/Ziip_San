package br.com.ziipsan.core.certification;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Curadoria do mapa de clausulas por edicao (ED-76; ADR-0041 item 11;
 * origem ADR-0041, 21a CQ-15, 21b L-18).
 *
 * <p>O que esta classe e: o formato, em memoria, do mapa que a ED-76
 * acrescenta ao catalogo-base de normas de {@code docs/normas} (carregado
 * pela P1.34): para cada edicao de uma norma, a lista das suas clausulas
 * com numero, titulo e resumo escritos em palavras proprias pela
 * curadoria, a partir dos textos comprados na P1.28. E exatamente o
 * conteudo da tabela {@code platform.cert_clause} da V9_8 (P1.32):
 * {@code clause_number}, {@code title}, {@code summary} e
 * {@code sort_order}, ligados a edicao de
 * {@code platform.cert_standard_edition}.
 *
 * <p>O que esta classe deliberadamente NAO e: nao existe campo capaz de
 * guardar texto de norma. O produto guarda so titulo, edicao e numero de
 * clausula (e o resumo em palavras proprias da curadoria) — os textos
 * comprados ficam fora do repositorio, conforme a licenca (P1.28). A
 * leitura do JSON de curadoria recusa qualquer chave fora da lista
 * fechada de campos: um campo chamado {@code texto}, {@code text} ou
 * qualquer outro nome inesperado e erro de validacao, nunca dado
 * ignorado em silencio — e assim que um texto de norma poderia entrar
 * no produto sem ninguem perceber.
 *
 * <p>Limite honesto da trava (achado 5 da revisao da ED-76): ela barra
 * campo novo/dedicado para texto de norma, mas {@code title} e
 * {@code summary} sao texto livre (como na V9_8) — texto copiado colado
 * dentro do resumo nao e detectado pelo codigo. O controle sobre o
 * conteudo desses dois campos e o processo de curadoria humana.
 *
 * <p>O arquivo entregue em {@code docs/normas/mapa-clausulas.json} e um
 * ENVELOPE ({@code mapas} + {@code versaoFormato}), nao um mapa solto:
 * {@link #fromJsonEnvelope(String)} e o caminho de carga dele — valida
 * as chaves fechadas do envelope, exige {@code versaoFormato} 1 e
 * desdobra cada elemento de {@code mapas} pelo mesmo {@link #fromJson}
 * por mapa, com a mesma trava de chaves em todos os niveis.
 *
 * <p>Conteudo real nao nasce aqui: esta classe e o mecanismo. Os mapas
 * reais sao escritos pela curadoria sobre os textos comprados e chegam
 * por {@code docs/normas/mapa-clausulas.json} e pela carga repetivel
 * {@code R__norm_clause_map.sql}; os testes usam apenas dados
 * sinteticos minimos, com o prefixo SYN- e titulos assumidamente
 * ficticios, como os testes da P1.32 ja faziam.
 *
 * <p>JSON somente pela classe {@link Json} do core, como manda o padrao
 * do modulo: serializar com {@code Map.toString()} gravaria texto que
 * nao e JSON na fonte de curadoria.
 */
public final class ClauseCuration {

    /** Numero de clausula: grupos alfanumericos separados por ponto (4, 4.1, 8.1.2, A.1). */
    private static final Pattern CLAUSE_NUMBER = Pattern.compile("[A-Za-z0-9]+(\\.[A-Za-z0-9]+)*");

    /** Chaves fechadas do objeto de curadoria de uma edicao. Nada alem disto e aceito. */
    private static final Set<String> MAP_KEYS = Set.of("standardCode", "edition", "clauses");

    /** Chaves fechadas de uma clausula do mapa. Nao ha chave de texto de norma. */
    private static final Set<String> CLAUSE_KEYS = Set.of("clauseNumber", "title", "summary", "sortOrder");

    /** Chaves fechadas do envelope do arquivo de curadoria (docs/normas/mapa-clausulas.json). */
    private static final Set<String> ENVELOPE_KEYS = Set.of("mapas", "versaoFormato");

    /** Versao de formato do envelope aceita por esta leitura. Outra versao e erro, nao chute. */
    private static final int ENVELOPE_FORMAT_VERSION = 1;

    private ClauseCuration() {
    }

    /**
     * Uma clausula do mapa: numero, titulo em palavras proprias e resumo
     * em palavras proprias (pode faltar). Sem campo de texto da norma.
     */
    public record ClauseEntry(String clauseNumber, String title, String summary, int sortOrder) {

        public ClauseEntry {
            if (clauseNumber == null || clauseNumber.isBlank()) {
                throw validation("clauseNumber", "required", "O número da cláusula é obrigatório.");
            }
            clauseNumber = clauseNumber.trim();
            if (!CLAUSE_NUMBER.matcher(clauseNumber).matches()) {
                throw validation("clauseNumber", "invalid",
                        "Número de cláusula fora do formato (grupos separados por ponto): " + clauseNumber + ".");
            }
            if (title == null || title.isBlank()) {
                throw validation("title", "required",
                        "O título em palavras próprias da cláusula " + clauseNumber + " é obrigatório.");
            }
            title = title.trim();
            if (summary != null && summary.isBlank()) {
                summary = null;
            }
            if (summary != null) {
                summary = summary.trim();
            }
            if (sortOrder < 0) {
                throw validation("sortOrder", "invalid", "A ordem da cláusula não pode ser negativa.");
            }
        }
    }

    /**
     * O mapa de clausulas de UMA edicao de UMA norma.
     * {@code standardCode} e o {@code code} de {@code platform.cert_standard}
     * (V9_8) — a chave que o {@code CertificationService} usa para achar a
     * norma ({@code WHERE code = ?}) e que a ativacao grava como
     * {@code standard_code}. Nao e o {@code catalog_key}: as duas colunas
     * sao UNIQUE distintas na V9_8, e a carga SQL
     * ({@code R__norm_clause_map.sql}) resolve a edicao por {@code code},
     * pela mesma razao. A lista sai ordenada
     * por {@code sortOrder} e numero de clausula — a mesma ordem da
     * consulta da V9_8 ({@code ORDER BY sort_order, clause_number}) — e
     * numero repetido na mesma edicao e recusado, como a trava
     * {@code uq_cert_clause_edition_number} do banco.
     */
    public record EditionClauseMap(String standardCode, String edition, List<ClauseEntry> clauses) {

        public EditionClauseMap {
            if (standardCode == null || standardCode.isBlank()) {
                throw validation("standardCode", "required", "O código da norma é obrigatório.");
            }
            standardCode = standardCode.trim();
            if (edition == null || edition.isBlank()) {
                throw validation("edition", "required", "A edição do mapa de cláusulas é obrigatória.");
            }
            edition = edition.trim();
            if (clauses == null) {
                throw validation("clauses", "required", "A lista de cláusulas do mapa é obrigatória.");
            }
            Set<String> seen = new HashSet<>();
            for (ClauseEntry entry : clauses) {
                if (entry == null) {
                    throw validation("clauses", "invalid", "O mapa não aceita cláusula nula.");
                }
                if (!seen.add(entry.clauseNumber())) {
                    throw validation("clauseNumber", "duplicate",
                            "Cláusula repetida no mapa da edição " + edition + ": "
                                    + entry.clauseNumber() + ".");
                }
            }
            List<ClauseEntry> sorted = new ArrayList<>(clauses);
            sorted.sort(Comparator.comparingInt(ClauseEntry::sortOrder).thenComparing(ClauseEntry::clauseNumber));
            clauses = List.copyOf(sorted);
        }

        /** A clausula de um numero, ou {@code null} se o mapa nao a tem. */
        public ClauseEntry clause(String clauseNumber) {
            for (ClauseEntry entry : clauses) {
                if (entry.clauseNumber().equals(clauseNumber)) {
                    return entry;
                }
            }
            return null;
        }
    }

    // ------------------------------------------------------------------
    // JSON de curadoria (docs/normas/mapa-clausulas.json)
    //
    // O arquivo e um envelope {"mapas":[...],"versaoFormato":1}: quem o
    // carrega e fromJsonEnvelope, que desdobra cada elemento de "mapas"
    // pelo parseMap abaixo — o mesmo caminho de fromJson, com a mesma
    // lista fechada de chaves nos tres niveis (envelope, mapa, clausula).
    // ------------------------------------------------------------------

    /**
     * Le um mapa de curadoria do JSON. Chave desconhecida em qualquer
     * nivel e erro de validacao: e a trava que impede texto de norma de
     * entrar no produto por um campo novo nao revisado.
     */
    public static EditionClauseMap fromJson(String json) {
        return parseMap(Json.readMap(json));
    }

    /** Escreve o mapa no JSON de curadoria, sempre pela classe {@link Json}. */
    public static String toJson(EditionClauseMap map) {
        if (map == null) {
            throw new IllegalArgumentException("map is required");
        }
        return Json.write(mapToJsonObject(map));
    }

    /**
     * Le o arquivo de curadoria inteiro (o envelope de
     * {@code docs/normas/mapa-clausulas.json}): chaves fechadas
     * {@code mapas}/{@code versaoFormato}, versao de formato obrigatoria
     * e igual a 1, e cada elemento de {@code mapas} lido como um mapa
     * por {@link #fromJson} (mesma trava). O envelope com a lista vazia
     * — o estado entregue, sem nenhum mapa curado ainda — e legivel e
     * devolve lista vazia.
     */
    public static List<EditionClauseMap> fromJsonEnvelope(String json) {
        Map<String, Object> root = Json.readMap(json);
        requireOnlyKeys(root.keySet(), ENVELOPE_KEYS, "arquivo de mapas de cláusulas");
        Object version = root.get("versaoFormato");
        if (!(version instanceof Number number) || number.intValue() != ENVELOPE_FORMAT_VERSION) {
            throw validation("versaoFormato", "invalid",
                    "A versão de formato do arquivo de mapas deve ser " + ENVELOPE_FORMAT_VERSION + ".");
        }
        Object rawMaps = root.get("mapas");
        if (!(rawMaps instanceof List<?> mapList)) {
            throw validation("mapas", "required", "A lista de mapas do arquivo é obrigatória.");
        }
        List<EditionClauseMap> maps = new ArrayList<>();
        for (Object raw : mapList) {
            if (!(raw instanceof Map<?, ?> mapObject)) {
                throw validation("mapas", "invalid", "Cada mapa do arquivo deve ser um objeto JSON.");
            }
            maps.add(parseMap(mapObject));
        }
        return List.copyOf(maps);
    }

    /** Escreve os mapas no envelope do arquivo de curadoria, sempre pela classe {@link Json}. */
    public static String toJsonEnvelope(List<EditionClauseMap> maps) {
        if (maps == null) {
            throw new IllegalArgumentException("maps is required");
        }
        List<Object> mapObjects = new ArrayList<>();
        for (EditionClauseMap map : maps) {
            if (map == null) {
                throw new IllegalArgumentException("maps must not contain null");
            }
            mapObjects.add(mapToJsonObject(map));
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("mapas", mapObjects);
        root.put("versaoFormato", ENVELOPE_FORMAT_VERSION);
        return Json.write(root);
    }

    private static EditionClauseMap parseMap(Map<?, ?> root) {
        requireOnlyKeys(root.keySet(), MAP_KEYS, "mapa de cláusulas");
        String standardCode = requiredText(root, "standardCode");
        String edition = requiredText(root, "edition");
        Object rawClauses = root.get("clauses");
        if (!(rawClauses instanceof List<?> clauseList)) {
            throw validation("clauses", "required", "A lista de cláusulas do mapa é obrigatória.");
        }
        List<ClauseEntry> entries = new ArrayList<>();
        for (Object raw : clauseList) {
            if (!(raw instanceof Map<?, ?> clauseMap)) {
                throw validation("clauses", "invalid", "Cada cláusula do mapa deve ser um objeto JSON.");
            }
            requireOnlyKeys(clauseMap.keySet(), CLAUSE_KEYS, "cláusula do mapa");
            String clauseNumber = requiredText(clauseMap, "clauseNumber");
            String title = requiredText(clauseMap, "title");
            Object summary = clauseMap.get("summary");
            if (summary != null && !(summary instanceof String)) {
                throw validation("summary", "invalid", "O resumo da cláusula deve ser texto.");
            }
            Object sortOrder = clauseMap.get("sortOrder");
            int order = entries.size();
            if (sortOrder != null) {
                if (!(sortOrder instanceof Number number)) {
                    throw validation("sortOrder", "invalid", "A ordem da cláusula deve ser número.");
                }
                order = number.intValue();
            }
            entries.add(new ClauseEntry(clauseNumber, title, (String) summary, order));
        }
        return new EditionClauseMap(standardCode, edition, entries);
    }

    private static Map<String, Object> mapToJsonObject(EditionClauseMap map) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("standardCode", map.standardCode());
        root.put("edition", map.edition());
        List<Object> clauses = new ArrayList<>();
        for (ClauseEntry entry : map.clauses()) {
            Map<String, Object> clause = new LinkedHashMap<>();
            clause.put("clauseNumber", entry.clauseNumber());
            clause.put("title", entry.title());
            clause.put("summary", entry.summary());
            clause.put("sortOrder", entry.sortOrder());
            clauses.add(clause);
        }
        root.put("clauses", clauses);
        return root;
    }

    private static void requireOnlyKeys(Set<?> keys, Set<String> allowed, String what) {
        for (Object key : keys) {
            if (!(key instanceof String name) || !allowed.contains(name)) {
                throw validation(String.valueOf(key), "unknown-field",
                        "Campo não previsto no " + what + ": " + key
                                + ". O mapa guarda só título, edição, número de cláusula e resumo"
                                + " em palavras próprias; texto de norma não entra no produto.");
            }
        }
    }

    private static String requiredText(Map<?, ?> map, String field) {
        Object value = map.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw validation(field, "required", "O campo " + field + " é obrigatório.");
        }
        return text;
    }

    private static ApiException.Validation validation(String field, String code, String message) {
        return new ApiException.Validation(message,
                List.of(new ApiException.FieldError(field, code, message)), List.of());
    }
}
