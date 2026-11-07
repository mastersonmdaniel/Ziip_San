package br.com.ziipsan.integration.sinisa;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de dados de origem do pre-preenchimento (P4.2): dado um tenant, um
 * ano-base e uma variavel do mapa, devolve o valor calculado da origem declarada
 * (metering, billing, registry ou derivacao do balanco), ou vazio quando a origem
 * nao tem o dado do ano-base.
 *
 * <p>Vazio <b>nunca</b> vira zero: a variavel fica pendente no rascunho e so pode
 * ser completada por valor manual justificado. A implementacao que agrega os
 * modulos donos (P2.5: metering/billing) e fiacao da integracao, como o pacote
 * de fiacao M7 da P2.5: esta porta e o contrato, sem consulta SQL escondida aqui.
 */
public interface SinisaSourceData {

    Optional<BigDecimal> fetch(UUID tenantId, int baseYear, SinisaVariable variable);
}
