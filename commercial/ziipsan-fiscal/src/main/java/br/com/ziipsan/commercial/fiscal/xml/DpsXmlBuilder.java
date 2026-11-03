package br.com.ziipsan.commercial.fiscal.xml;

import br.com.ziipsan.commercial.fiscal.rules.DpsId;
import br.com.ziipsan.commercial.fiscal.rules.TaxEngine;

import java.math.BigDecimal;

/**
 * Builder da DPS (Declaração de Prestação de Serviço) da NFS-e Nacional, na forma canônica
 * do porte — XML <b>não assinado</b>, que é o que o corpus de referência congela (P1.10).
 *
 * <p>Limite honesto deste builder, registrado aqui e no README do corpus: o XSD oficial da
 * DPS <b>não está disponível neste sandbox</b> (o próprio documento de porte registra que o
 * repositório C# não tem XSD nem XML de referência, FISCAL-PORTE-JAVA §1.1), e o elemento
 * aqui não afirma conformidade com o schema oficial. O que este builder garante, e o que os
 * dois CIs comparam: a mesma entrada produz o mesmo XML canônico, com a versão 1.00 ou 1.01
 * conforme o grupo IBSCBS (regra do documento de porte, §1) e todos os valores escapados. A
 * validação contra o XSD oficial é pendência da homologação, antes de qualquer emissão fora
 * do simulador — e o XML assinado nunca é gravado neste repositório (ADR-0033, ajuste v2.9).
 *
 * <p>O builder é determinístico: {@code dhEmi} e {@code applicationVersion} entram como
 * parâmetros (o C# lê o relógio direto, o que impede comparação byte a byte — porte §5.3).
 * A assinatura (XMLDSig) não vive aqui: ela é aplicada pelo assinador da P2, sobre este XML.
 */
public final class DpsXmlBuilder {

    private DpsXmlBuilder() {
    }

    /** Entrada da DPS. Valores monetários com escala 2; {@code dhEmi} em ISO-8601 com offset. */
    public record DpsInput(
            String dpsId,
            String dhEmi,
            String series,
            long number,
            String issuerIbge,
            String issuerInscription,
            String takerName,
            BigDecimal gross,
            TaxEngine.TaxResult taxes,
            boolean withIbsCbs) {
    }

    /** Monta o XML não assinado. Nunca inclui elemento {@code Signature}. */
    public static String build(DpsInput input) {
        if (input == null) {
            throw new IllegalArgumentException("A entrada da DPS e obrigatoria");
        }
        if (input.dpsId() == null || input.dpsId().length() != 42) {
            throw new IllegalArgumentException("O Id da DPS deve ter 42 posicoes (DpsId)");
        }
        String version = input.withIbsCbs() ? "1.01" : "1.00";
        TaxEngine.TaxResult t = input.taxes();
        StringBuilder xml = new StringBuilder(1024);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<DPS versao=\"").append(version).append("\">");
        xml.append("<infDPS Id=\"").append(XmlEscape.escape(input.dpsId())).append("\">");
        xml.append("<dhEmi>").append(XmlEscape.escape(input.dhEmi())).append("</dhEmi>");
        xml.append("<serie>").append(XmlEscape.escape(input.series())).append("</serie>");
        xml.append("<numero>").append(input.number()).append("</numero>");
        xml.append("<prestador><ibge>").append(XmlEscape.escape(input.issuerIbge())).append("</ibge>")
                .append("<inscricao>").append(XmlEscape.escape(input.issuerInscription())).append("</inscricao></prestador>");
        if (input.takerName() != null && !input.takerName().isBlank()) {
            xml.append("<tomador><nome>").append(XmlEscape.escape(input.takerName())).append("</nome></tomador>");
        }
        xml.append("<valores>");
        xml.append("<vBruto>").append(money(input.gross())).append("</vBruto>");
        xml.append("<vIss>").append(money(t.iss())).append("</vIss>");
        xml.append("<vIrrf>").append(money(t.irrf())).append("</vIrrf>");
        xml.append("<vCsrf>").append(money(t.csrf())).append("</vCsrf>");
        xml.append("<vLiquido>").append(money(t.net())).append("</vLiquido>");
        xml.append("</valores>");
        if (input.withIbsCbs()) {
            xml.append("<IBSCBS>");
            xml.append("<vCbs>").append(money(t.cbsInformative())).append("</vCbs>");
            xml.append("<vIbs>").append(money(t.ibsInformative())).append("</vIbs>");
            xml.append("</IBSCBS>");
        }
        xml.append("</infDPS></DPS>");
        return xml.toString();
    }

    /** Monta o Id da DPS e o XML em um passo, validando os campos pelo {@link DpsId}. */
    public static String buildForIssuer(String ibge, char inscriptionType, String inscription,
            String series, long number, DpsInput template) {
        String numberField = String.format("%015d", number);
        String dpsId = DpsId.of(ibge, inscriptionType, inscription, series, numberField);
        return build(new DpsInput(dpsId, template.dhEmi(), series, number, ibge, inscription,
                template.takerName(), template.gross(), template.taxes(), template.withIbsCbs()));
    }

    private static String money(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("O valor monetario e obrigatorio");
        }
        return value.setScale(2, java.math.RoundingMode.HALF_EVEN).toPlainString();
    }
}
