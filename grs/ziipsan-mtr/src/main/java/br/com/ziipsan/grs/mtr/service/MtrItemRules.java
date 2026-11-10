package br.com.ziipsan.grs.mtr.service;

import br.com.ziipsan.grs.mtr.domain.WasteClass;

import java.math.BigDecimal;

// ED-104 (produtor, sessão 6d762d43-e1a9-4984-b50d-571ba0643b52) — E8.
// Validadores de item do MTR a partir das regras do Manual de Ajuda ao Utilizador do MTR Nacional
// confirmadas pelo levantamento regulatório: limite de 45 t / 45.000 kg por resíduo e por MTR,
// mínimo de 0,1 kg com passo de 0,1 kg, RSS só em kg ou litro, e 1 só resíduo por MTR quando há
// armazenador temporário. São REGRAS DO SISTEMA parametrizáveis (o manual pode mudar sem
// portaria) — declaradas como tal, não como texto de portaria. O leiaute do WS federal continua
// [SEM FONTE] (pendência P-ED104-01); nada aqui é payload de submissão.
public final class MtrItemRules {

    /** Limites vigentes declarados; outra jurisdição/período pode injetar outros valores. */
    public record Limits(BigDecimal maxKgPerItem, BigDecimal minKg, BigDecimal stepKg) {
        public Limits {
            if (maxKgPerItem == null || minKg == null || stepKg == null
                    || maxKgPerItem.signum() <= 0 || minKg.signum() < 0 || stepKg.signum() <= 0) {
                throw new IllegalArgumentException("limites do MTR inválidos");
            }
        }

        /** Valores do Manual do Utilizador do MTR Nacional (via levantamento, 12/10/2026). */
        public static Limits nationalDefaults() {
            return new Limits(new BigDecimal("45000"), new BigDecimal("0.1"), new BigDecimal("0.1"));
        }
    }

    private MtrItemRules() {
    }

    /**
     * Valida a quantidade (kg) de um item/MTR contra os limites.
     *
     * @throws IllegalArgumentException acima do máximo, abaixo do mínimo ou fora do passo
     */
    public static void validateQuantityKg(BigDecimal quantityKg, Limits limits) {
        if (quantityKg == null) {
            throw new IllegalArgumentException("quantidade do MTR é obrigatória");
        }
        if (quantityKg.compareTo(limits.maxKgPerItem()) > 0) {
            throw new IllegalArgumentException("quantidade acima do limite por MTR: "
                    + quantityKg.toPlainString() + " kg > " + limits.maxKgPerItem().toPlainString()
                    + " kg (regra do sistema, parametrizável)");
        }
        if (quantityKg.compareTo(limits.minKg()) < 0) {
            throw new IllegalArgumentException("quantidade abaixo do mínimo: "
                    + quantityKg.toPlainString() + " kg < " + limits.minKg().toPlainString() + " kg");
        }
        if (quantityKg.remainder(limits.stepKg()).compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalArgumentException("quantidade fora do passo de "
                    + limits.stepKg().toPlainString() + " kg: " + quantityKg.toPlainString());
        }
    }

    /** RSS só em kg ou litro (regra do manual, via levantamento). */
    public static void validateUnitForScheme(WasteClass.ClassificationScheme scheme, String unit) {
        if (scheme == WasteClass.ClassificationScheme.RSS
                && !("kg".equalsIgnoreCase(unit) || "L".equalsIgnoreCase(unit))) {
            throw new IllegalArgumentException("RSS só admite kg ou litro, recebido: " + unit);
        }
    }

    /** Com armazenador temporário, 1 só resíduo por MTR (regra do manual, via levantamento). */
    public static void validateItemCount(boolean hasTemporaryStorage, int itemCount) {
        if (hasTemporaryStorage && itemCount != 1) {
            throw new IllegalArgumentException("com armazenador temporário o MTR leva 1 só resíduo, "
                    + "recebidos: " + itemCount);
        }
    }
}
