package br.com.ziipsan.reporting.spi;

import java.util.UUID;

/**
 * Resolve o contexto (permissoes/teto de mascara) de um usuario ja
 * conhecido por tenant+id - necessario ao worker, que executa fora de
 * uma requisicao HTTP (exportacoes ad-hoc enfileiradas e a reconstrucao
 * do dono de execucoes antigas). A fiacao de identidade do app substitui
 * o resolvedor conservador padrao da ReportingConfiguration (sem
 * permissoes, mascara total): sem identidade confiavel, a geracao e
 * negada e registrada, nunca executada com contexto fabricado.
 */
public interface ReportUserContextResolver {

    ReportUserContext resolve(UUID tenantId, UUID userId);
}
