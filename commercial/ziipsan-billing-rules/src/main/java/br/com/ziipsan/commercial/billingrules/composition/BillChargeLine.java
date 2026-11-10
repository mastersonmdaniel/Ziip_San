package br.com.ziipsan.commercial.billingrules.composition;

import br.com.ziipsan.commercial.billingrules.catalog.CalculationModel;
import br.com.ziipsan.commercial.billingrules.catalog.ChargeOwner;
import br.com.ziipsan.commercial.billingrules.catalog.ChargeResolution;
import br.com.ziipsan.commercial.billingrules.servicecatalog.RevenueNature;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Linha de cobranca calculada pelo maestro (desenho da Camada 0,
 * secao 2.1 item 9, com o municipio por linha do adendo D-04).
 *
 * @param chargeCode       codigo da cobranca no catalogo (Camada 1)
 * @param chargeVersionId  id da versao da cobranca efectivamente usada;
 *                         e o que o snapshot regista (INV-C2-06). So e
 *                         nulo na projecao classica sem catalogo do
 *                         {@link BillCalculatorAdapter} — nunca numa
 *                         linha produzida por uma calculadora
 * @param owner            dono da cobranca (propria x terceiro)
 * @param revenueNature    natureza da receita (enum da ED-34, reutilizado)
 * @param model            modelo de calculo que produziu a linha
 * @param amount           valor da linha (escala monetaria, &gt;= 0)
 * @param municipalityIbge escopo resolvido da linha: o
 *                         {@code municipalityIbge} da definicao vencedora
 *                         (municipal quando a precedencia D-02.2 escolheu
 *                         a municipal; {@code null} = escopo "empresa").
 *                         E o escopo EFFECTIVAMENTE usado, nao um valor
 *                         declarado (adendo D-04)
 * @param detail           detalhe opaco da calculadora (para snapshot/
 *                         explicacao e para o adapter classico: as
 *                         calculadoras progressiva/derivada guardam aqui
 *                         o {@code TariffCharge}/{@code SewerCharge} do
 *                         motor vigente); pode ser nulo
 */
public record BillChargeLine(String chargeCode, UUID chargeVersionId, ChargeOwner owner,
                             RevenueNature revenueNature, CalculationModel model,
                             BigDecimal amount, String municipalityIbge, Object detail) {

    public BillChargeLine {
        if (chargeCode == null || chargeCode.isBlank()) {
            throw new IllegalArgumentException("codigo da cobranca da linha e obrigatorio");
        }
        Objects.requireNonNull(owner, "dono da linha e obrigatorio");
        Objects.requireNonNull(revenueNature, "natureza da receita da linha e obrigatoria");
        Objects.requireNonNull(model, "modelo da linha e obrigatorio");
        Objects.requireNonNull(amount, "valor da linha e obrigatorio");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("valor da linha nao pode ser negativo: " + amount);
        }
        if (municipalityIbge != null && !municipalityIbge.matches("[0-9]{7}")) {
            throw new IllegalArgumentException(
                    "municipalityIbge da linha invalido: codigo IBGE tem exactamente 7 digitos: "
                            + municipalityIbge);
        }
    }

    /** Linha a partir da resolucao do catalogo, com o dono da definicao. */
    public static BillChargeLine fromResolution(ChargeResolution resolution, BigDecimal amount,
                                                Object detail) {
        return fromResolution(resolution, resolution.definition().owner(), amount, detail);
    }

    /** Linha a partir da resolucao, com dono forcado (ex.: EXTERNAL_VALUE nasce THIRD_PARTY). */
    public static BillChargeLine fromResolution(ChargeResolution resolution, ChargeOwner owner,
                                                BigDecimal amount, Object detail) {
        Objects.requireNonNull(resolution, "resolucao e obrigatoria");
        return new BillChargeLine(resolution.definition().code(), resolution.version().id(), owner,
                resolution.definition().revenueNature(), resolution.definition().calculationModel(),
                amount, resolution.definition().municipalityIbge(), detail);
    }

    /** A linha e de terceiro (fora do teto de creditos e da base de retencao)? */
    public boolean isThirdParty() {
        return owner == ChargeOwner.THIRD_PARTY;
    }
}
