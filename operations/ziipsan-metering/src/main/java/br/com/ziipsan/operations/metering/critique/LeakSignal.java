package br.com.ziipsan.operations.metering.critique;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Sinal proactivo de vazamento de uma UC (II-2, E5). E um SINAL para
 * investigacao (fila/OS), nao um facto de vazamento nem base de cobranca --
 * a revisao por vazamento da P3.28 continua a ser o caminho reactivo, apos
 * reclamacao e afericao.
 *
 * @param unitId                 unidade sinalizada
 * @param firstNight             primeira noite da sequencia que disparou
 * @param lastNight              ultima noite da sequencia que disparou
 * @param minimumNightMean       menor media nocturna da sequencia (o "caudal minimo nocturno" observado)
 * @param configuredMinNightFlow limiar configurado contra o qual a sequencia foi medida
 * @param consecutiveNights      noites consecutivas observadas na sequencia
 */
public record LeakSignal(UUID unitId, LocalDate firstNight, LocalDate lastNight,
                         BigDecimal minimumNightMean, BigDecimal configuredMinNightFlow,
                         int consecutiveNights) {
}
