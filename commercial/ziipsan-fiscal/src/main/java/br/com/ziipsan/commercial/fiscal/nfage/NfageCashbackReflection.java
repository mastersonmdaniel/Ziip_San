package br.com.ziipsan.commercial.fiscal.nfage;

import br.com.ziipsan.core.json.Json;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Ponto de integração do cashback CBS/IBS (P2.34) com a NFAg-e da P2.6:
 * o reflexo da devolução no documento fiscal, <b>condicional ao MOC
 * definitivo</b>.
 *
 * <p><b>CONDICIONALIDADE EXPRESSA:</b> o aceite da P2.34 manda refletir o
 * cashback "nos campos do leiaute vigente, condicionais ao MOC definitivo".
 * Enquanto o MOC definitivo não definir os campos do cashback no leiaute
 * do modelo 75, <b>nenhum nome de campo de leiaute é inventado aqui</b> —
 * este DTO carrega os valores internos da devolução ligados à
 * {@code versaoLeiaute} e declara {@code aplicavelAoLeiaute = false}. O
 * payload abaixo é JSON <b>interno</b> (pela classe {@code Json} do core),
 * como o da emissão da P2.6 — não é o XML da NFAg-e. Quando o MOC definir
 * os campos, a fiação da integração mapeia este DTO para eles e vira a
 * chave {@code aplicavelAoLeiaute}.
 */
public final class NfageCashbackReflection {

    /** Marcador da condicionalidade, presente em todo payload enquanto o MOC não define os campos. */
    public static final String CONDICIONALIDADE = "CONDICIONAL AO MOC DEFINITIVO (P2.34)";

    private NfageCashbackReflection() {
    }

    /**
     * @param billId          conta cuja NFAg-e refletiria o cashback
     * @param cbsDevolvida    parcela de CBS devolvida na fatura (P2.34)
     * @param ibsDevolvida    parcela de IBS devolvida na fatura (P2.34)
     * @param totalDevolvido  total do crédito de cashback na fatura
     * @param versaoLeiaute   versão do leiaute (MOC) à qual o reflexo se liga
     * @param mocDefineCampos verdadeiro só quando o MOC definitivo definir os campos do cashback
     */
    public record ReflexoCashback(UUID billId, BigDecimal cbsDevolvida, BigDecimal ibsDevolvida,
                                  BigDecimal totalDevolvido, String versaoLeiaute, boolean mocDefineCampos) {

        public ReflexoCashback {
            Objects.requireNonNull(billId, "Conta é obrigatória");
            Objects.requireNonNull(cbsDevolvida, "CBS devolvida é obrigatória");
            Objects.requireNonNull(ibsDevolvida, "IBS devolvida é obrigatória");
            Objects.requireNonNull(totalDevolvido, "Total devolvido é obrigatório");
            if (versaoLeiaute == null || versaoLeiaute.isBlank()) {
                throw new IllegalArgumentException("Versão do leiaute é obrigatória: o reflexo é condicional ao MOC e se liga a ela");
            }
            if (totalDevolvido.compareTo(cbsDevolvida.add(ibsDevolvida)) != 0) {
                throw new IllegalArgumentException("Total do cashback deve ser CBS + IBS: " + totalDevolvido
                        + " != " + cbsDevolvida + " + " + ibsDevolvida);
            }
        }

        /** O reflexo pode ser aplicado a campos do leiaute? Só com o MOC definitivo definindo os campos. */
        public boolean aplicavelAoLeiaute() {
            return mocDefineCampos;
        }

        /** Registro interno do reflexo (JSON do core; NÃO é campo de leiaute nem XML). */
        public String payloadInternoJson() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("origem", "CASHBACK_CBS_IBS_P2_34");
            payload.put("billId", billId.toString());
            payload.put("cbsDevolvida", cbsDevolvida.toPlainString());
            payload.put("ibsDevolvida", ibsDevolvida.toPlainString());
            payload.put("totalDevolvido", totalDevolvido.toPlainString());
            payload.put("versaoLeiaute", versaoLeiaute);
            payload.put("condicionalidade", CONDICIONALIDADE);
            payload.put("aplicavelAoLeiaute", aplicavelAoLeiaute());
            return Json.write(payload);
        }
    }
}
