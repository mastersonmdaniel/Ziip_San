package br.com.ziipsan.commercial.registry.application;

import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.type.StandardBasicTypes;

/**
 * P0.22: registra {@code immutable_unaccent(text)} no Hibernate como função que
 * devolve String.
 *
 * <p>POR QUE EXISTE: a busca por nome usa a função do banco (wrapper IMMUTABLE de
 * unaccent, criado pelo init de segurança como public.immutable_unaccent IMMUTABLE
 * LEAKPROOF de ziipsan_owner) para casar com o índice trigram. (Nota: LEAKPROOF
 * na função NÃO faz o planner usar o índice sob RLS — o bloqueador é o operador
 * textlike, não-leakproof; ver TrigramSearchIT.) Chamar uma
 * função desconhecida via JPQL {@code function('immutable_unaccent', ...)} faz o
 * Hibernate 7 tipá-la como Object — e {@code Object like :nome} falha na
 * validação semântica ("Operand of 'like' is of type 'java.lang.Object'"), um
 * 500 em toda busca por nome. Registrada aqui com retorno String, a chamada
 * direta {@code immutable_unaccent(lower(c.name))} tipa certo, e o SQL gerado é
 * exatamente a expressão do índice (sem CAST em volta, que poderia impedir o
 * planner de usar o índice).
 *
 * <p>Descoberta pelo ServiceLoader do Hibernate (arquivo em
 * META-INF/services/org.hibernate.boot.model.FunctionContributor): nenhum
 * registro manual no Spring, nenhum dialect próprio.
 */
public class ImmutableUnaccentFunctionContributor implements FunctionContributor {

    @Override
    public void contributeFunctions(FunctionContributions contributions) {
        contributions.getFunctionRegistry().registerPattern(
                "immutable_unaccent",
                "immutable_unaccent(?1)",
                contributions.getTypeConfiguration().getBasicTypeRegistry()
                        .resolve(StandardBasicTypes.STRING));
    }
}
