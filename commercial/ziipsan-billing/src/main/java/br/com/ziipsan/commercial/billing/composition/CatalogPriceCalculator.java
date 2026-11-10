package br.com.ziipsan.commercial.billing.composition;

import br.com.ziipsan.commercial.billing.catalog.CalculationModel;
import br.com.ziipsan.commercial.billing.catalog.ChargeResolution;
import br.com.ziipsan.commercial.billing.math.Money;
import br.com.ziipsan.commercial.billing.servicecatalog.ServicePriceItem;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Cobranca por preco de catalogo (servicos da ED-34): preco unitario
 * vezes quantidade, com {@link Money#roundHalfUp} — o regime vigente
 * do proprio {@code ServicePriceItem} (regra de fronteira (v)).
 *
 * <p><b>Precedencia do preco unitario</b> (documentada, no espirito da
 * tensao T-1 registada na Camada 1 para o modelo CATALOG_PRICE):
 * <ol>
 *   <li>o item RESOLVIDO que o contexto traz pelo {@code priceItemCode}
 *       da versao ({@link ChargeCalculationContext#priceItems()}) — e a
 *       resolucao viva da tabela de precos da ED-34 feita pelo chamador,
 *       e vence;</li>
 *   <li>na ausencia desse item no contexto, o {@code unitPrice} dos
 *       parametros da versao — que, na forma reconciliada da Camada 1,
 *       e o valor resolvido desse mesmo item quando o chamador o traz
 *       nos parametros.</li>
 * </ol>
 * Nenhum dos dois presente = falha fechada (preco nunca presumido).
 * A quantidade vem de {@code catalogQuantities} por codigo e e
 * obrigatoria e positiva.
 */
public final class CatalogPriceCalculator implements ChargeCalculator {

    @Override
    public CalculationModel model() {
        return CalculationModel.CATALOG_PRICE;
    }

    @Override
    public BillChargeLine calculate(ChargeCalculationContext context) {
        Objects.requireNonNull(context, "contexto de calculo e obrigatorio");
        ChargeResolution resolution = context.requireResolution(model());
        String code = resolution.definition().code();
        String priceItemCode = resolution.version().priceItemCode();
        ServicePriceItem item = priceItemCode == null ? null : context.priceItems().get(priceItemCode);
        BigDecimal unitPrice;
        if (item != null) {
            unitPrice = item.unitPrice();
        } else if (resolution.version().parameters().unitPrice() != null) {
            unitPrice = resolution.version().parameters().unitPrice();
        } else {
            throw new IllegalStateException("cobranca de catalogo " + code
                    + " sem preco resolvido: nem item " + priceItemCode
                    + " no contexto, nem unitPrice nos parametros da versao");
        }
        BigDecimal quantity = context.catalogQuantities().get(code);
        if (quantity == null) {
            throw new IllegalArgumentException(
                    "cobranca de catalogo " + code + " sem quantidade pre-resolvida no contexto");
        }
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException(
                    "quantidade da cobranca de catalogo " + code + " deve ser positiva: " + quantity);
        }
        BigDecimal amount = Money.roundHalfUp(unitPrice.multiply(quantity));
        return BillChargeLine.fromResolution(resolution, amount, item);
    }
}
