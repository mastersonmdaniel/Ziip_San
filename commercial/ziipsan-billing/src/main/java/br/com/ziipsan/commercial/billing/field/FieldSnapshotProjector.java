package br.com.ziipsan.commercial.billing.field;

import br.com.ziipsan.commercial.billingrules.FieldTariffSnapshot;
import br.com.ziipsan.commercial.billingrules.TariffBracket;
import br.com.ziipsan.commercial.billingrules.catalog.CalculationModel;
import br.com.ziipsan.commercial.billingrules.catalog.ChargeResolution;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Projector do snapshot de campo (Arquitetura Tarifaria Unificada,
 * Camada 4 — desenho da Camada 0, seccao 4.1 item 3, na forma da
 * fronteira D-06 do adendo de desenho do municipio): projecta as
 * cobrancas calculaveis em campo resolvidas por unidade
 * ({@link FieldChargeResolver}) no {@link FieldTariffSnapshot}
 * VIGENTE da ED-19 — mesma forma, mesmos campos, nenhum campo novo
 * no artefacto. O snapshot projectado e o da tarifa do municipio da
 * unidade: a resolucao ja vem do escopo resolvido (precedencia
 * municipal sobre empresa, D-02) e este projector valida que os
 * dados tarifarios recebidos sao os desse escopo (ver abaixo).
 *
 * <p><b>Entrada tipada explicita (decisao de desenho desta
 * camada):</b> os dados tarifarios que o snapshot carrega — versao
 * de tarifa, categoria, minimo, parcela fixa, faixas, esgoto e
 * versoes de parametro — NAO vivem no catalogo de cobrancas (a
 * Camada 1 referencia a versao de tarifa por UUID; o conteudo da
 * tarifa e da P2.5) e esta camada nao inventa fonte para eles:
 * chegam por entrada explicita do chamador, no record tipado
 * {@link FieldTariffData}. Sem valores de fabrica, sem leitura de
 * banco, sem parametro escondido: quem monta a tarefa de campo (no
 * bloco multimunicipio, o montador de rotas) resolve a tarifa
 * vigente do escopo da unidade e entrega os dados aqui.
 *
 * <p><b>Seleccao/validacao a partir do resolvido (falha fechada,
 * INV-C4-05):</b> o projector nao copia os dados cegamente —
 * confronta-os com o resolvido do catalogo e FALHA FECHADO
 * ({@link IllegalStateException} com a causa identificada) quando a
 * situacao nao e representavel no snapshot vigente:
 * <ul>
 *   <li>alguma resolucao recebida nao e calculavel em campo pela
 *       {@link FieldCalculablePolicy} (incl. convenio
 *       {@code EXTERNAL_VALUE}/terceiro — INV-C4-02 re-verificado
 *       aqui, para o caso de a lista nao ter passado pelo
 *       {@link FieldChargeResolver});</li>
 *   <li>nao ha exactamente UMA cobranca
 *       {@code PROGRESSIVE_BY_CONSUMPTION} resolvida — ela e a
 *       ancora tarifaria do snapshot: sem ela nao ha tarifa a
 *       projectar; com duas, a ancora e ambigua;</li>
 *   <li>a versao de tarifa dos dados ({@code tariffVersionId}) nao
 *       e a referenciada pela versao vigente da ancora resolvida —
 *       os dados nao sao os da tarifa do escopo resolvido;</li>
 *   <li>ha mais de uma cobranca {@code DERIVED_FROM_VOLUME}
 *       resolvida (fonte do esgoto ambigua);</li>
 *   <li>ha cobranca derivada resolvida mas os dados nao faturam
 *       esgoto, ou o percentual da versao derivada (quando a versao
 *       o declara) difere do percentual dos dados;</li>
 *   <li>os dados faturam esgoto sem nenhuma cobranca derivada
 *       resolvida que o sustente no catalogo.</li>
 * </ul>
 * Nunca devolve valor chutado nem omite silenciosamente: ou o
 * snapshot sai inteiro e coerente com o resolvido, ou nao sai.
 * (A retencao de tarefa via {@code DivergenceEvaluator} pertence ao
 * montador de rotas do bloco multimunicipio, D-06 — aqui, a falha
 * fechada declarada e o comportamento.)
 *
 * <p><b>O que nao entra no snapshot:</b> cobrancas
 * {@code FIXED_PER_UNIT} resolvidas (ex.: TFDI) nao tem campo no
 * snapshot vigente — a parcela fixa do snapshot e a da TARIFA
 * (vem dos dados); as fixas do catalogo sao lancadas na conta de
 * campo como linhas de debito pelo chamador, fora do retrato, como
 * o calculador vigente ja admite ({@code FieldBillInput.debits}).
 * O projector apenas confirma que sao calculaveis (politica) — nao
 * as dobra dentro do snapshot, o que corromperia a parcela fixa da
 * tarifa.
 *
 * <p><b>Fotografia estavel (INV-C4-06):</b> o snapshot devolvido e o
 * record vigente, que copia faixas e versoes de parametro na
 * construcao; {@link FieldTariffData} faz a mesma copia defensiva
 * na entrada. Alteracao posterior do catalogo ou das coleccoes do
 * chamador nao altera o snapshot ja projectado.
 */
public final class FieldSnapshotProjector {

    /**
     * Dados tarifarios resolvidos da tarifa vigente do escopo da
     * unidade, entregues explicitamente pelo chamador — a porta
     * tipada do projector. Espelha, campo a campo, o conteudo do
     * {@link FieldTariffSnapshot} vigente; quem preenche e quem
     * resolveu a tarifa (P2.5) para o escopo da unidade.
     *
     * @param tariffVersionId       versao de tarifa de onde os dados
     *                              sairam; tem de ser a referenciada
     *                              pela cobranca progressiva resolvida
     * @param categoryId            categoria tarifaria da unidade
     * @param minimumM3PerEconomy   consumo minimo por economia (m3)
     * @param fixedChargePerEconomy parcela fixa da tarifa por economia
     * @param brackets              faixas progressivas (ultima aberta)
     * @param sewerEnabled          esgoto faturado nesta unidade?
     * @param sewerPct              percentual do esgoto sobre a agua;
     *                              obrigatorio quando
     *                              {@code sewerEnabled}
     * @param parameterVersions     versoes de parametro da conta
     *                              (chave -&gt; versao/valor resolvido);
     *                              {@code null} tratado como vazio,
     *                              como no snapshot vigente
     */
    public record FieldTariffData(UUID tariffVersionId, UUID categoryId,
                                  int minimumM3PerEconomy, BigDecimal fixedChargePerEconomy,
                                  List<TariffBracket> brackets,
                                  boolean sewerEnabled, BigDecimal sewerPct,
                                  Map<String, String> parameterVersions) {

        public FieldTariffData {
            Objects.requireNonNull(tariffVersionId, "versao de tarifa dos dados e obrigatoria");
            Objects.requireNonNull(categoryId, "categoria dos dados e obrigatoria");
            Objects.requireNonNull(fixedChargePerEconomy, "parcela fixa dos dados e obrigatoria");
            // Copia defensiva na entrada (INV-C4-06): a fotografia
            // nao segue coleccoes que o chamador ainda possa mutar.
            // A validacao de forma das faixas e do esgoto e a do
            // construtor do snapshot vigente, exercida na projeccao.
            brackets = brackets == null ? null : List.copyOf(brackets);
            parameterVersions = parameterVersions == null ? null : Map.copyOf(parameterVersions);
        }
    }

    /**
     * Projecta o resolvido de campo da unidade no snapshot vigente.
     *
     * @param resolvedFieldCharges resolucoes calculaveis em campo da
     *                             unidade (saida do
     *                             {@link FieldChargeResolver})
     * @param tariffData           dados da tarifa vigente do escopo
     *                             resolvido, explicitos do chamador
     * @return o {@link FieldTariffSnapshot} vigente, campo a campo
     *         igual ao montado pelo caminho directo da tarifa do
     *         mesmo escopo (INV-C4-01)
     * @throws IllegalStateException se a situacao nao e representavel
     *         no snapshot vigente (falha fechada, INV-C4-05)
     */
    public FieldTariffSnapshot project(List<ChargeResolution> resolvedFieldCharges,
                                       FieldTariffData tariffData) {
        Objects.requireNonNull(resolvedFieldCharges, "resolucoes de campo sao obrigatorias");
        Objects.requireNonNull(tariffData, "dados tarifarios explicitos sao obrigatorios");

        for (ChargeResolution resolution : resolvedFieldCharges) {
            Objects.requireNonNull(resolution, "resolucao nula nao e projectavel");
            if (!FieldCalculablePolicy.isFieldCalculable(resolution)) {
                throw new IllegalStateException(
                        "cobranca nao calculavel em campo nao entra no snapshot de campo: "
                                + resolution.definition().code()
                                + " (modelo " + resolution.definition().calculationModel()
                                + ", dono " + resolution.definition().owner() + ")");
            }
        }

        List<ChargeResolution> anchors = resolvedFieldCharges.stream()
                .filter(r -> r.definition().calculationModel()
                        == CalculationModel.PROGRESSIVE_BY_CONSUMPTION)
                .toList();
        if (anchors.size() != 1) {
            throw new IllegalStateException(
                    "snapshot de campo exige exactamente uma cobranca progressiva resolvida "
                            + "como ancora tarifaria; resolvidas: " + anchors.size());
        }
        ChargeResolution anchor = anchors.get(0);
        if (!tariffData.tariffVersionId().equals(anchor.version().tariffVersionId())) {
            throw new IllegalStateException(
                    "dados tarifarios nao sao os da tarifa resolvida no catalogo: dados="
                            + tariffData.tariffVersionId() + ", resolvida="
                            + anchor.version().tariffVersionId()
                            + " (cobranca " + anchor.definition().code() + ")");
        }

        List<ChargeResolution> derived = resolvedFieldCharges.stream()
                .filter(r -> r.definition().calculationModel()
                        == CalculationModel.DERIVED_FROM_VOLUME)
                .toList();
        if (derived.size() > 1) {
            throw new IllegalStateException(
                    "mais de uma cobranca derivada resolvida: fonte do esgoto ambigua ("
                            + derived.size() + ")");
        }
        if (derived.size() == 1) {
            if (!tariffData.sewerEnabled()) {
                throw new IllegalStateException(
                        "cobranca derivada resolvida (" + derived.get(0).definition().code()
                                + ") mas os dados tarifarios nao faturam esgoto");
            }
            BigDecimal declaredPct = derived.get(0).version().parameters().percent();
            if (declaredPct != null && (tariffData.sewerPct() == null
                    || declaredPct.compareTo(tariffData.sewerPct()) != 0)) {
                throw new IllegalStateException(
                        "percentual de esgoto dos dados (" + tariffData.sewerPct()
                                + ") difere do percentual da cobranca derivada resolvida ("
                                + declaredPct + ")");
            }
        } else if (tariffData.sewerEnabled()) {
            throw new IllegalStateException(
                    "dados tarifarios faturam esgoto sem cobranca derivada resolvida "
                            + "que o sustente no catalogo");
        }

        return new FieldTariffSnapshot(tariffData.tariffVersionId(), tariffData.categoryId(),
                tariffData.minimumM3PerEconomy(), tariffData.fixedChargePerEconomy(),
                tariffData.brackets(), tariffData.sewerEnabled(), tariffData.sewerPct(),
                tariffData.parameterVersions());
    }
}
