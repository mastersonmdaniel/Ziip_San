package br.com.ziipsan.operations.metering.critique;

import java.math.BigDecimal;

/**
 * Um disparo de regra (II-2): qual regra, o valor observado e o limiar
 * contra o qual ele foi comparado -- o rasto que a fila de excepcoes e o
 * operador precisam para decidir sem reabrir o motor.
 *
 * @param ruleCode codigo da regra que disparou
 * @param kind     tipo da regra
 * @param observed valor observado (consumo, desvio percentual ou leitura, conforme o tipo)
 * @param threshold limiar configurado que foi excedido; nulo nos tipos sem limiar
 * @param detail   descricao deterministica do disparo (sem texto livre de operador)
 */
public record RuleFinding(String ruleCode, CritiqueRuleKind kind, BigDecimal observed,
                          BigDecimal threshold, String detail) {
}
