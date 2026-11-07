package br.com.ziipsan.operations.metering.rules;

import br.com.ziipsan.operations.metering.domain.ConsumptionRecord;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Retificacao de uma linha de consumo (P2.5, DAD-14): cria a <b>versao seguinte</b>
 * em vez de atualizar a linha. A versao antiga conserva todos os valores faturados;
 * a unica escrita nela e a ligacao {@code supersededById} para a sucessora, que a
 * tira da vigencia (o indice unico parcial da V4_4 so vale para a vigente).
 *
 * <p>Fluxo de quem chama: {@code rectify} devolve a nova versao ja com os valores
 * da anterior copiados; o chamador corrige os valores que a retificacao muda
 * (pelos {@code fillFrom*} da entidade ou pelos setters), e salva as duas linhas
 * (INSERT da nova, e a ligacao na antiga). Sem banco aqui: a regra e pura e a
 * prova de "nao faz UPDATE dos valores" esta no teste.
 */
public final class ConsumptionRectification {

    private ConsumptionRectification() {
    }

    /**
     * Monta a versao retificada de {@code current}.
     *
     * @param current             a versao vigente (a substituida recusa: retificar versao
     *                            morta bifurcaria a cadeia, e o banco tambem recusa pela
     *                            unicidade de {@code superseded_by_id})
     * @param billId              a conta que fatura a nova versao (a retificada, quando a
     *                            retificacao gera conta nova; {@code null} mantem a da versao
     *                            anterior -- e ai {@code billReferenceMonth} acompanha)
     * @param billReferenceMonth  o mes da conta nova (dia 1); obrigatorio junto de um
     *                            {@code billId} novo, ignorado quando {@code billId} e null
     * @param parameterVersionIds ids das versoes de parametro do recalculo (JSON, pela
     *                            classe Json do core); {@code null} mantem os da versao anterior
     * @return a nova versao, ainda nao salva, com id ja atribuido (a ligacao da
     *         anterior precisa dele antes do INSERT -- precedente do {@code BaseEntity})
     */
    public static ConsumptionRecord rectify(ConsumptionRecord current, UUID billId, LocalDate billReferenceMonth,
                                            String parameterVersionIds) {
        Objects.requireNonNull(current, "current");
        if (!current.isCurrent()) {
            throw new IllegalStateException(
                    "so a versao vigente pode ser retificada: esta ja foi substituida por " + current.getSupersededById());
        }
        if (billId != null && billReferenceMonth == null) {
            throw new IllegalArgumentException(
                    "billReferenceMonth e obrigatorio com billId novo (a FK para bills particionada exige o mes)");
        }

        ConsumptionRecord next = new ConsumptionRecord();
        next.setId(UUID.randomUUID());
        next.setTenantId(current.getTenantId());
        current.copyValuesTo(next);
        next.setVersionNo(current.getVersionNo() + 1);
        next.setSupersededById(null);
        next.setBillId(billId != null ? billId : current.getBillId());
        next.setBillReferenceMonth(billId != null ? billReferenceMonth : current.getBillReferenceMonth());
        next.setParameterVersionIds(parameterVersionIds != null ? parameterVersionIds : current.getParameterVersionIds());

        // A unica escrita na versao antiga: a ligacao para a sucessora.
        current.setSupersededById(next.getId());
        return next;
    }
}
