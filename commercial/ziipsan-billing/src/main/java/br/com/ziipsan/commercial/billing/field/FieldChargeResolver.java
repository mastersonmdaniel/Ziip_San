package br.com.ziipsan.commercial.billing.field;

import br.com.ziipsan.commercial.billing.catalog.ChargeResolver;
import br.com.ziipsan.commercial.billingrules.catalog.ChargeResolution;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Resolvedor de campo POR UNIDADE (Arquitetura Tarifaria Unificada,
 * Camada 4 — desenho da Camada 0, seccao 4.1 item 2, na forma da
 * fronteira D-06 do adendo de desenho do municipio): resolve, para
 * UMA unidade consumidora, apenas as cobrancas calculaveis em campo
 * dessa unidade, a partir do {@link ChargeResolver} da Camada 1
 * filtrado pela {@link FieldCalculablePolicy}.
 *
 * <p><b>Resolucao por municipio da UC (D-02):</b> o
 * {@code municipalityIbge} que acompanha a referencia da unidade e o
 * municipio DERIVADO pelo chamador da cadeia territorial (unidade
 * &rarr; bloco &rarr; setor comercial &rarr; localidade) — nunca
 * digitado no billing (premissa PM-05 do adendo). A precedencia
 * municipal sobre empresa (a definicao municipal do municipio da UC
 * vence a de empresa para o mesmo codigo; definicao de OUTRO
 * municipio nem entra; sem municipio derivado, so o escopo empresa)
 * e aplicada pelo resolvedor da Camada 1 — esta classe nao a
 * reimplementa: delega e filtra.
 *
 * <p><b>Minimo por unidade (INV-C4-03, na leitura de D-06):</b> o
 * resultado contem APENAS cobrancas calculaveis em campo da unidade
 * resolvida — convenio ({@code EXTERNAL_VALUE}/dono
 * {@code THIRD_PARTY}) nunca aparece (INV-C4-02), modelos nao
 * reproduzidos pelo calculador vigente nao aparecem, cobrancas sem
 * marcacao {@code fieldCalculable} nao aparecem. A montagem do
 * pacote de rota (artefacto) e do bloco multimunicipio (D-06): esta
 * classe resolve por unidade, nao monta rotas.
 *
 * <p>A lista devolvida e imutavel e mantem a ordem deterministica
 * do resolvedor da Camada 1 (ordenada pelo codigo da cobranca).
 */
public final class FieldChargeResolver {

    private final ChargeResolver chargeResolver;

    public FieldChargeResolver(ChargeResolver chargeResolver) {
        this.chargeResolver = Objects.requireNonNull(chargeResolver,
                "resolvedor do catalogo (Camada 1) e obrigatorio");
    }

    /**
     * Resolve as cobrancas calculaveis em campo da unidade na
     * competencia.
     *
     * @param unitRef          referencia opaca da unidade (nao
     *                         interpretada nesta camada, como na
     *                         Camada 1)
     * @param municipalityIbge municipio da unidade, derivado pelo
     *                         chamador (IBGE de 7 digitos);
     *                         {@code null} = sem municipio derivado,
     *                         resolve so o escopo "empresa"
     * @param competence       competencia (data de referencia da
     *                         vigencia)
     * @throws IllegalArgumentException se {@code municipalityIbge}
     *         nao nulo nao tiver exactamente 7 digitos numericos
     *         (fail-fast na fronteira, R9-C4-2: o javadoc promete
     *         "IBGE de 7 digitos" e a Camada 1 so compara por
     *         igualdade, sem validar o formato; {@code null}
     *         continua a significar sem municipio derivado)
     * @return lista imutavel das resolucoes calculaveis em campo,
     *         na ordem do resolvedor da Camada 1; vazia quando
     *         nenhuma cobranca vigente da unidade e calculavel em
     *         campo
     */
    public List<ChargeResolution> resolveFieldCharges(String unitRef, String municipalityIbge,
                                                      LocalDate competence) {
        Objects.requireNonNull(unitRef, "referencia da unidade e obrigatoria");
        Objects.requireNonNull(competence, "competencia e obrigatoria");
        // Fronteira R9-C4-2: IBGE malformado nao pode cair
        // silenciosamente para o escopo empresa nem resolver a
        // tarifa de outro municipio por coincidencia. Padrao
        // estabelecido no codigo (ChargeDefinition.java:128):
        // null = escopo empresa; presente, exactamente 7 digitos.
        if (municipalityIbge != null && !municipalityIbge.matches("[0-9]{7}")) {
            throw new IllegalArgumentException(
                    "municipalityIbge invalido: codigo IBGE tem exactamente 7 digitos numericos: "
                            + municipalityIbge);
        }
        return chargeResolver.resolve(unitRef, municipalityIbge, competence).stream()
                .filter(FieldCalculablePolicy::isFieldCalculable)
                .toList();
    }
}
