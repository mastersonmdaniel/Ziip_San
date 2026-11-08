package br.com.ziipsan.commercial.registry.recadastramento;

import java.util.UUID;

/**
 * Leitura do valor vigente de um campo do cadastro (ED-16), como porta:
 * o servico de recadastramento precisa do valor atual em dois momentos
 * — na coleta (a fotografia contra a qual a diferenca e medida) e na
 * aplicacao (a conferencia que detecta a ocorrencia incorreta/STALE).
 *
 * <p>A implementacao de producao le das tabelas donas do registry
 * ({@code customers}/{@code consumer_units} e vinculadas), na fiacao de
 * integracao. Para o campo de documento, o valor devolvido e a MASCARA
 * gravada, nunca o numero em claro (regra da P1.3). Campo sem valor
 * devolve {@code null}.
 */
public interface CadastralFieldReader {

    String currentValue(String targetType, UUID targetId, String field);
}
