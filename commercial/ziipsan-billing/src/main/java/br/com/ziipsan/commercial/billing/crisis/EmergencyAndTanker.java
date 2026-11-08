package br.com.ziipsan.commercial.billing.crisis;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * Fornecimento de emergencia e carro-pipa (OPE-10).
 *
 * - Emergencia a unidades essenciais em interrupcao SUPERIOR a 24 h
 *   (NR 11/ANA): 24 h exatas NAO disparam; acima, disparam.
 * - Carro-pipa: autorizacao + documento de abastecimento obrigatorios e
 *   cloro residual livre medido >= minimo do tenant (padrao 0,5 mg/L, so
 *   amplia - CrisisParameters). MECANISMO + registro: a logistica real
 *   (frota, rota, contratado) e pendencia externa B nomeada no relatorio;
 *   nada aqui finge operacao real.
 * - Ponto de abastecimento de carro-pipa com venda avulsa e comprovante
 *   (CAD-72): valor vem do chamador (preco do catalogo da ED-34 e fiacao);
 *   comprovante obrigatorio.
 */
public final class EmergencyAndTanker {

    public static final Duration EMERGENCY_THRESHOLD = Duration.ofHours(24);

    private EmergencyAndTanker() {}

    public static boolean emergencySupplyRequired(Duration outageDuration, boolean hasEssentialUnits) {
        Objects.requireNonNull(outageDuration, "duracao obrigatoria");
        return hasEssentialUnits && outageDuration.compareTo(EMERGENCY_THRESHOLD) > 0;
    }

    public record TankerDelivery(UUID id, String authorizationRef, String supplyDocumentRef,
                                 BigDecimal measuredChlorineMgL, BigDecimal requiredMinChlorineMgL,
                                 BigDecimal volumeLiters, UUID destinationUnitId) {
        public TankerDelivery {
            Objects.requireNonNull(id, "entrega obrigatoria");
            if (authorizationRef == null || authorizationRef.isBlank())
                throw new IllegalArgumentException("autorizacao do carro-pipa obrigatoria");
            if (supplyDocumentRef == null || supplyDocumentRef.isBlank())
                throw new IllegalArgumentException("documento de abastecimento obrigatorio");
            Objects.requireNonNull(measuredChlorineMgL, "cloro medido obrigatorio");
            Objects.requireNonNull(requiredMinChlorineMgL, "minimo do tenant obrigatorio");
            if (requiredMinChlorineMgL.compareTo(CrisisParameters.CHLORINE_FLOOR_MG_L) < 0)
                throw new IllegalArgumentException("minimo abaixo do piso de 0,5 mg/L");
            if (measuredChlorineMgL.compareTo(requiredMinChlorineMgL) < 0)
                throw new IllegalArgumentException("cloro residual livre " + measuredChlorineMgL
                        + " mg/L abaixo do minimo " + requiredMinChlorineMgL + " mg/L: entrega nao conforme");
            Objects.requireNonNull(volumeLiters, "volume obrigatorio");
            if (volumeLiters.signum() <= 0) throw new IllegalArgumentException("volume deve ser positivo");
            Objects.requireNonNull(destinationUnitId, "destino obrigatorio");
        }
    }

    public record FillingPointSale(UUID id, String fillingPointCode, BigDecimal volumeLiters,
                                   BigDecimal amount, String receiptCode) {
        public FillingPointSale {
            Objects.requireNonNull(id, "venda obrigatoria");
            if (fillingPointCode == null || fillingPointCode.isBlank())
                throw new IllegalArgumentException("ponto de abastecimento obrigatorio");
            Objects.requireNonNull(volumeLiters, "volume obrigatorio");
            if (volumeLiters.signum() <= 0) throw new IllegalArgumentException("volume deve ser positivo");
            Objects.requireNonNull(amount, "valor obrigatorio (preco da ED-34 via fiacao)");
            if (amount.signum() < 0) throw new IllegalArgumentException("valor nao pode ser negativo");
            if (receiptCode == null || receiptCode.isBlank())
                throw new IllegalArgumentException("comprovante da venda avulsa obrigatorio (CAD-72)");
        }
    }
}
