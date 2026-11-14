package br.com.ziipsan.commercial.fiscal.relief;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Tipos de substituicao da NFAg 01-09 (F-16, art. 898 da base J-Tech/Sansys,
 * LOTE-03 parte 13): codigos fiscais de 2 digitos pre-definidos pelo manual
 * da NFAg. Entram <b>por cadastro a partir da lista de referencia</b>, com a
 * fonte citada no registo de carga — nao por seed cego. Depois de criado, so
 * descricao e estado mudam; o codigo e imutavel; tipo inactivo bloqueia a
 * operacao.
 *
 * <p>Fonte da lista: F-16/art. 898. A lista e conteudo de referencia a
 * carregar no provisionamento do tenant que emite NFAg; {@link #loadReference}
 * exige a referencia da fonte no acto da carga e grava-a.
 */
public final class SubstitutionTypes {

    public record SubstitutionType(String code, String description, boolean active) {
        public SubstitutionType {
            if (code == null || !code.matches("\\d{2}")) {
                throw new IllegalArgumentException("codigo do tipo de substituicao tem 2 digitos: " + code);
            }
            if (description == null || description.isBlank()) {
                throw new IllegalArgumentException("descricao do tipo e obrigatoria");
            }
        }

        SubstitutionType withDescription(String newDescription) {
            return new SubstitutionType(code, newDescription, active);
        }

        SubstitutionType withActive(boolean value) {
            return new SubstitutionType(code, description, value);
        }
    }

    /** Lista de referencia F-16 (codigo -> descricao do manual, via art. 898). */
    public static final List<String[]> REFERENCE = List.of(
            new String[]{"01", "erro de leitura"},
            new String[]{"02", "erro de preco/tarifa"},
            new String[]{"03", "decisao judicial"},
            new String[]{"04", "erro cadastral"},
            new String[]{"05", "erro de tributacao"},
            new String[]{"06", "decisao reguladora"},
            new String[]{"07", "acumulo de consumo"},
            new String[]{"08", "vazamento interno"},
            new String[]{"09", "alteracao de vencimento"});

    private final Map<String, SubstitutionType> types = new LinkedHashMap<>();
    private String loadSourceRef;

    /**
     * Carrega a lista de referencia no cadastro do tenant.
     *
     * @param sourceRef fonte citada no registo de carga (obrigatoria — sem
     *        fonte declarada, a carga recusa: nao e seed cego)
     */
    public void loadReference(String sourceRef) {
        if (sourceRef == null || sourceRef.isBlank()) {
            throw new IllegalArgumentException(
                    "Carga dos tipos de substituicao exige a fonte citada no registo de carga");
        }
        this.loadSourceRef = sourceRef;
        for (String[] row : REFERENCE) {
            types.putIfAbsent(row[0], new SubstitutionType(row[0], row[1], true));
        }
    }

    public String loadSourceRef() {
        return loadSourceRef;
    }

    /** So descricao muda depois da criacao (F-16); o codigo nunca muda. */
    public void updateDescription(String code, String description) {
        types.put(code, require(code).withDescription(description));
    }

    public void setActive(String code, boolean active) {
        types.put(code, require(code).withActive(active));
    }

    /** Tipo para uso: inexistente ou inactivo e recusado. */
    public SubstitutionType resolveActive(String code) {
        SubstitutionType found = require(code);
        if (!found.active()) {
            throw new IllegalStateException("Tipo de substituicao inactivo bloqueia a operacao: " + code);
        }
        return found;
    }

    public List<SubstitutionType> all() {
        return List.copyOf(types.values());
    }

    private SubstitutionType require(String code) {
        SubstitutionType found = types.get(code);
        if (found == null) {
            throw new IllegalStateException("Tipo de substituicao nao cadastrado: " + code);
        }
        return found;
    }
}
