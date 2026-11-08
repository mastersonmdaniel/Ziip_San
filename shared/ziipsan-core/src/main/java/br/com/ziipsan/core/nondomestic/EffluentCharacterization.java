package br.com.ziipsan.core.nondomestic;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Caracterizacao do efluente do usuario nao domestico, declarada no
 * cadastro (aceite da ED-83): os parametros do efluente como o gerador
 * os declara, o pre-tratamento exigido e a prova de que ele corre
 * AS EXPENSAS DO USUARIO, nos padroes do prestador (NR 11/ANA).
 *
 * <p>Regras duras:
 * <ul>
 *   <li>sem parametro caracterizado, nao ha caracterizacao — a
 *       autorizacao de lancamento nao nasce sobre efluente
 *       desconhecido;</li>
 *   <li>{@code pretreatmentAtUserExpense} tem de ser VERDADEIRO: o
 *       aceite fixa o pre-tratamento as expensas do usuario; declarar
 *       o contrario recusa a caracterizacao (nao e um default, e uma
 *       condicao);</li>
 *   <li>a descricao do pre-tratamento e texto DECLARADO do processo
 *       (metodo/sistema do usuario) — nenhum texto de termo, metodo
 *       ou padrao e redigido aqui [SEM FONTE para texto padrao].</li>
 * </ul>
 * Os valores declarados sao os do usuario; a conformidade deles e
 * medida depois, por amostragem e automonitoramento, contra o
 * catalogo de padrao de lancamento do prestador.
 */
public record EffluentCharacterization(
        List<CharacterizedParameter> parameters,
        String pretreatmentDescription,
        boolean pretreatmentAtUserExpense,
        String declaredByRef,
        Instant declaredAt) {

    /** Um parametro do efluente como declarado: numerico OU qualitativo, como reportado. */
    public record CharacterizedParameter(String parameterKey, BigDecimal numericValue,
                                         String qualitativeValue, String unit) {
        public CharacterizedParameter {
            if (parameterKey == null || parameterKey.isBlank())
                throw new IllegalArgumentException("parameterKey da caracterizacao e obrigatorio");
            boolean numeric = numericValue != null;
            boolean qualitative = qualitativeValue != null && !qualitativeValue.isBlank();
            if (numeric == qualitative)
                throw new IllegalArgumentException("parametro " + parameterKey
                        + " da caracterizacao exige exatamente uma forma:"
                        + " numerica OU qualitativa, como declarada");
        }
    }

    public EffluentCharacterization {
        if (parameters == null || parameters.isEmpty())
            throw new IllegalArgumentException("caracterizacao sem parametro nao e caracterizacao:"
                    + " a autorizacao nao nasce sobre efluente desconhecido");
        parameters = List.copyOf(parameters);
        long distinct = parameters.stream().map(CharacterizedParameter::parameterKey).distinct().count();
        if (distinct != parameters.size())
            throw new IllegalArgumentException("caracterizacao com parametro duplicado");
        if (pretreatmentDescription == null || pretreatmentDescription.isBlank())
            throw new IllegalArgumentException("caracterizacao exige a descricao do pre-tratamento"
                    + " nos padroes do prestador (NR 11/ANA)");
        if (!pretreatmentAtUserExpense)
            throw new IllegalArgumentException("pre-tratamento do efluente nao domestico e as"
                    + " expensas do usuario (aceite da ED-83, NR 11/ANA): caracterizacao que"
                    + " declara o contrario e recusada");
        if (declaredByRef == null || declaredByRef.isBlank())
            throw new IllegalArgumentException("caracterizacao exige quem declarou (declaredByRef)");
        if (declaredAt == null)
            throw new IllegalArgumentException("caracterizacao exige o instante da declaracao");
    }
}
