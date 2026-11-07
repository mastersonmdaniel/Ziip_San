package br.com.ziipsan.integration.sinisa;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Valor registrado de uma variavel num ciclo SINISA (P4.2).
 *
 * <p>Regra do aceite, sem excecao: <b>valor informado manualmente so com
 * justificativa</b>. Valor automatico carrega a origem do mapa; valor manual
 * carrega justificativa nao vazia e o autor. Nao existe fabrica que aceite valor
 * manual sem justificativa, e o construtor canonico revalida a regra (defesa em
 * profundidade: nenhuma desserializacao contorna a guarda).
 *
 * @param variableKey   chave interna da variavel no catalogo
 * @param value         valor na unidade declarada da variavel
 * @param origin        AUTOMATIC (veio da origem do mapa) ou MANUAL
 * @param justification justificativa obrigatoria do valor manual; nula no automatico
 * @param authorId      quem registrou (no automatico, o ator que disparou o pre-preenchimento)
 * @param registeredAt  instante do registro
 */
public record SinisaValue(
        String variableKey,
        BigDecimal value,
        Origin origin,
        String justification,
        UUID authorId,
        OffsetDateTime registeredAt) {

    public enum Origin {
        AUTOMATIC,
        MANUAL
    }

    public SinisaValue {
        if (variableKey == null || variableKey.isBlank()) {
            throw new IllegalArgumentException("Valor SINISA sem variavel");
        }
        Objects.requireNonNull(value, "Valor SINISA de " + variableKey + " e obrigatorio");
        Objects.requireNonNull(origin, "Valor SINISA de " + variableKey + " sem origem");
        Objects.requireNonNull(authorId, "Valor SINISA de " + variableKey + " sem autor");
        Objects.requireNonNull(registeredAt, "Valor SINISA de " + variableKey + " sem instante de registro");
        if (origin == Origin.MANUAL && (justification == null || justification.isBlank())) {
            throw new IllegalArgumentException("Valor manual da variavel " + variableKey
                    + " so e aceito com justificativa (aceite da P4.2)");
        }
        if (origin == Origin.AUTOMATIC && justification != null && !justification.isBlank()) {
            throw new IllegalArgumentException("Valor automatico da variavel " + variableKey
                    + " nao carrega justificativa: correcao de valor automatico e um novo valor manual");
        }
    }

    /** Valor vindo da origem declarada no mapa, pelo pre-preenchimento. */
    public static SinisaValue automatic(String variableKey, BigDecimal value, UUID actor,
                                        OffsetDateTime at) {
        return new SinisaValue(variableKey, value, Origin.AUTOMATIC, null, actor, at);
    }

    /** Valor digitado por uma pessoa: justificativa obrigatoria (regra do aceite). */
    public static SinisaValue manual(String variableKey, BigDecimal value, String justification,
                                     UUID authorId, OffsetDateTime at) {
        return new SinisaValue(variableKey, value, Origin.MANUAL, justification, authorId, at);
    }

    public boolean isManual() {
        return origin == Origin.MANUAL;
    }
}
