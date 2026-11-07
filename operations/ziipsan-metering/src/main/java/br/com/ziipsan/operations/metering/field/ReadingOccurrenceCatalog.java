package br.com.ziipsan.operations.metering.field;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta de leitura do catalogo de ocorrencias de leitura da P2.5 (por tenant). A
 * implementacao de producao le {@code reading_anomalies} do schema do tenant; a ED-18
 * depende so desta porta para nao duplicar o catalogo nem os seus efeitos.
 */
public interface ReadingOccurrenceCatalog {

    /** Ocorrencia pelo codigo, no tenant; vazia se o codigo nao existe no catalogo. */
    Optional<ReadingOccurrence> findByCode(UUID tenantId, String code);
}
