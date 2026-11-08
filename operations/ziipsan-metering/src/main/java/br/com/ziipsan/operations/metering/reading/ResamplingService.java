package br.com.ziipsan.operations.metering.reading;

import br.com.ziipsan.operations.metering.rules.MeteringErrorCode;
import br.com.ziipsan.operations.metering.rules.MeteringInputException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Releitura amostral por critério (ED-15): de cada rota lida, uma fração
 * dada por {@code read.resample_pct} volta para releitura, por <b>OS
 * seletiva</b> — uma ordem por unidade sorteada pela regra, nunca a rota
 * inteira.
 *
 * <p>O critério de seleção é <b>sistemático e determinístico</b>: sobre a
 * lista de unidades lidas na ordem do roteiro, escolhe-se a cada
 * {@code passo = N / quantidade} posições, começando da primeira. Sem
 * aleatoriedade: a mesma rota e o mesmo percentual devolvem sempre a
 * mesma amostra, o que torna a amostragem auditável e o teste exato.
 * A quantidade é {@code teto(N × pct / 100)}: qualquer percentual
 * positivo amostra ao menos uma unidade; {@code pct = 0} não amostra
 * nenhuma e {@code pct = 100} amostra todas.
 *
 * <p>O tipo de OS da releitura NÃO é inventado aqui: é o código do
 * catálogo de tipos de OS da P1.31 que quem chama informa
 * ({@code serviceOrderTypeCode} obrigatório).
 */
public final class ResamplingService {

    private ResamplingService() {
    }

    /** Um pedido de OS seletiva de releitura para uma unidade amostrada. */
    public record ResampleOrder(UUID unitId, String serviceOrderTypeCode) {
        public ResampleOrder {
            if (unitId == null) {
                throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                        "OS de releitura sem unidade");
            }
            if (serviceOrderTypeCode == null || serviceOrderTypeCode.isBlank()) {
                throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                        "OS de releitura sem o tipo de OS (catálogo da P1.31)");
            }
        }
    }

    /**
     * Seleciona a amostra e devolve um pedido de OS seletiva por unidade.
     *
     * @param readUnitIdsInRouteOrder unidades efetivamente lidas, na ordem do roteiro
     * @param resamplePct             valor vigente de {@code read.resample_pct} (0..100)
     * @param serviceOrderTypeCode    tipo de OS de releitura no catálogo da P1.31
     */
    public static List<ResampleOrder> select(List<UUID> readUnitIdsInRouteOrder, BigDecimal resamplePct,
                                             String serviceOrderTypeCode) {
        if (readUnitIdsInRouteOrder == null) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    "a releitura amostral exige a lista de unidades lidas");
        }
        ReadingCycleParameters.requireValidResamplePct(resamplePct);
        if (serviceOrderTypeCode == null || serviceOrderTypeCode.isBlank()) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    "tipo de OS de releitura é obrigatório (catálogo da P1.31)");
        }
        if (new java.util.HashSet<>(readUnitIdsInRouteOrder).size() != readUnitIdsInRouteOrder.size()) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    "a lista de unidades lidas tem unidade duplicada: a releitura geraria OS repetida");
        }
        int n = readUnitIdsInRouteOrder.size();
        if (n == 0 || resamplePct.signum() == 0) {
            return List.of();
        }
        int count = resamplePct.multiply(BigDecimal.valueOf(n))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.CEILING)
                .intValueExact();
        count = Math.min(count, n);
        List<ResampleOrder> orders = new ArrayList<>();
        // Sistemático: posições floor(i * N / count), i = 0..count-1 — distintas e crescentes.
        for (int i = 0; i < count; i++) {
            int index = (int) (((long) i * n) / count);
            orders.add(new ResampleOrder(readUnitIdsInRouteOrder.get(index), serviceOrderTypeCode));
        }
        return List.copyOf(orders);
    }
}
