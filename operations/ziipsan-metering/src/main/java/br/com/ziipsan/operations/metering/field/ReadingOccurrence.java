package br.com.ziipsan.operations.metering.field;

import br.com.ziipsan.operations.metering.rules.ConsumptionEffect;
import br.com.ziipsan.operations.metering.rules.ReadingEffect;

/**
 * Ocorrencia de leitura do catalogo do tenant (tabela {@code reading_anomalies} da P2.5,
 * V5_1): codigo, efeitos sobre a leitura e sobre o consumo — com e sem leitura — e se a
 * ocorrencia exige foto. A ED-18 NAO define ocorrencia nenhuma: le o catalogo vigente do
 * tenant pela porta {@link ReadingOccurrenceCatalog}. {@code requiresPhoto} e a leitura em
 * campo do catalogo (a foto por ocorrencia do aceite): quem alimenta o catalogo decide;
 * a sincronizacao apenas aplica — ocorrencia com foto exigida e sem foto e recusada.
 */
public record ReadingOccurrence(String code, boolean requiresPhoto, boolean requiresReading,
                                ReadingEffect readingEffectWithReading, ReadingEffect readingEffectWithoutReading,
                                ConsumptionEffect consumptionEffectWithReading,
                                ConsumptionEffect consumptionEffectWithoutReading) {
}
