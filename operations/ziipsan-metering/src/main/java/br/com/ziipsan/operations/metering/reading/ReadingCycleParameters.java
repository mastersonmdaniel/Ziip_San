package br.com.ziipsan.operations.metering.reading;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.operations.metering.rules.MeteringErrorCode;
import br.com.ziipsan.operations.metering.rules.MeteringInputException;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Porta dos parâmetros do ciclo de leitura da ED-15, lidos do catálogo de
 * parâmetros do tenant (nunca fixados no código):
 *
 * <ul>
 *   <li>{@code read.resample_pct} — percentual da releitura amostral;</li>
 *   <li>{@code read.photo_required_on} — quando a foto é obrigatória:
 *       no impedimento ou em toda leitura;</li>
 *   <li>{@code read.impediment_attempts} — número de tentativas exigido
 *       para aceitar um impedimento.</li>
 * </ul>
 *
 * <p>São as três chaves nomeadas no aceite da ED-15. Nenhuma outra chave é
 * inventada aqui: limiares que o aceite não nomeia (ex.: o desvio de
 * posição) chegam como argumento obrigatório de quem chama.
 * Ausência de valor vigente é erro explícito da implementação da porta,
 * como nas demais portas de parâmetros do projeto.
 */
public interface ReadingCycleParameters {

    String RESAMPLE_PCT_KEY = "read.resample_pct";
    String PHOTO_REQUIRED_ON_KEY = "read.photo_required_on";
    String IMPEDIMENT_ATTEMPTS_KEY = "read.impediment_attempts";

    /** Percentual de releitura amostral vigente (0 a 100). */
    BigDecimal resamplePct(UUID tenantId);

    /** Quando a foto é obrigatória no tenant. */
    PhotoRequiredOn photoRequiredOn(UUID tenantId);

    /** Tentativas mínimas para aceitar um impedimento (>= 1). */
    int impedimentAttempts(UUID tenantId);

    /** Quando a foto é obrigatória: {@code read.photo_required_on}. */
    enum PhotoRequiredOn {
        /** Foto obrigatória só quando há impedimento. */
        IMPEDIMENT_ONLY,
        /** Foto obrigatória em toda leitura (com ou sem impedimento). */
        EVERY_READING
    }

    /**
     * Interpreta o valor gravado de {@code read.photo_required_on} (texto JSON
     * com o nome da opção), SOMENTE pela classe {@link Json} do core.
     * Valor ausente, não textual ou fora do catálogo é erro explícito.
     */
    static PhotoRequiredOn parsePhotoRequiredOn(String json) {
        if (json == null || json.isBlank()) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    PHOTO_REQUIRED_ON_KEY + ": nenhum valor vigente foi informado");
        }
        JsonNode node = Json.readTree(json);
        if (!node.isTextual()) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    PHOTO_REQUIRED_ON_KEY + ": valor precisa ser texto JSON com IMPEDIMENT_ONLY ou EVERY_READING");
        }
        try {
            return PhotoRequiredOn.valueOf(node.asText().trim());
        } catch (IllegalArgumentException e) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    PHOTO_REQUIRED_ON_KEY + ": '" + node.asText() + "' fora do catálogo "
                            + "(IMPEDIMENT_ONLY, EVERY_READING)");
        }
    }

    /** Valida o domínio de {@code read.resample_pct}: 0 a 100, nunca presumido. */
    static BigDecimal requireValidResamplePct(BigDecimal pct) {
        if (pct == null) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    RESAMPLE_PCT_KEY + ": nenhum valor vigente foi informado");
        }
        if (pct.signum() < 0 || pct.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    RESAMPLE_PCT_KEY + ": percentual fora de 0..100: " + pct);
        }
        return pct;
    }

    /** Valida o domínio de {@code read.impediment_attempts}: inteiro >= 1. */
    static int requireValidImpedimentAttempts(int attempts) {
        if (attempts < 1) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    IMPEDIMENT_ATTEMPTS_KEY + ": tentativas precisam ser >= 1: " + attempts);
        }
        return attempts;
    }
}
