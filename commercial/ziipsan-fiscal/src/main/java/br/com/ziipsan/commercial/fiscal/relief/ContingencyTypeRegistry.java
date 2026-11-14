package br.com.ziipsan.commercial.fiscal.relief;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Cadastro de tipos de contingencia da NFAg (PA-01 V-43 = PARCIAL: a politica
 * de contingencia existe — {@code NfageContingencyPolicy}, motivo declarado
 * obrigatorio — mas nao ha cadastro administrativo de tipos como o art. 901
 * descreve). Este cadastro e analogo ao de CFOP/NCM (secao 3.7 do desenho):
 * codigo unico, descricao, estado, apagar so se nunca usado.
 *
 * <p>Fronteiras honradas: (i) V-31 = AUSENTE o valor — nenhum timeout de
 * contingencia e importado (os 15 s do Sansys NAO entram; nao ha constante
 * nem parametro de timeout neste cadastro); (ii) a regra contingencia x
 * certificado vencido (P-02-07) continua [A DEFINIR] — este cadastro nao cria
 * excepcao de contingencia a guarda de certificado.
 */
public final class ContingencyTypeRegistry {

    public record ContingencyType(String code, String description, boolean active) {
        public ContingencyType {
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("codigo do tipo de contingencia e obrigatorio");
            }
            if (description == null || description.isBlank()) {
                throw new IllegalArgumentException("descricao do tipo de contingencia e obrigatoria");
            }
        }

        ContingencyType withActive(boolean value) {
            return new ContingencyType(code, description, value);
        }
    }

    private final Map<String, ContingencyType> types = new LinkedHashMap<>();

    public ContingencyType register(String code, String description) {
        ContingencyType created = new ContingencyType(code, description, true);
        if (types.putIfAbsent(code, created) != null) {
            throw new IllegalStateException("Tipo de contingencia ja cadastrado: " + code);
        }
        return created;
    }

    public void delete(String code, Predicate<String> everUsed) {
        Objects.requireNonNull(everUsed, "verificacao de uso e obrigatoria");
        if (everUsed.test(code)) {
            throw new IllegalStateException(
                    "Tipo de contingencia ja usado: nao se apaga, inactiva-se: " + code);
        }
        types.remove(code);
    }

    public void deactivate(String code) {
        ContingencyType current = types.get(code);
        if (current == null) {
            throw new IllegalStateException("Tipo de contingencia nao cadastrado: " + code);
        }
        types.put(code, current.withActive(false));
    }

    public ContingencyType resolveActive(String code) {
        ContingencyType found = types.get(code);
        if (found == null) {
            throw new IllegalStateException("Tipo de contingencia nao cadastrado: " + code);
        }
        if (!found.active()) {
            throw new IllegalStateException("Tipo de contingencia inactivo recusado: " + code);
        }
        return found;
    }
}
