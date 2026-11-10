package br.com.ziipsan.commercial.billing.catalog;

import br.com.ziipsan.commercial.billing.servicecatalog.RevenueNature;

import java.util.Objects;
import java.util.UUID;

/**
 * Definicao de uma cobranca no registro unico e versionado de cobrancas
 * (proposta de arquitetura tarifaria, secoes 2.1 e 2.2; decisao do dono,
 * item 43 do registro de decisoes). Toda cobranca que pode entrar numa
 * conta e um registro deste catalogo: codigo, dono (propria x terceiro),
 * natureza da receita, modelo de calculo e homologacao quando exigida.
 * Cobre tarifa de agua, tarifa de esgoto, coleta de residuos, TFDI e
 * taxas/tarifas adicionais fixas ou variaveis — sem nenhuma formula aqui:
 * a definicao declara O QUE a cobranca e; os valores e a vigencia vivem
 * na versao ({@link ChargeVersion}) e o calculo e da camada seguinte.
 *
 * <p>A {@link RevenueNature} e REUTILIZADA da ED-34 (pacote
 * {@code servicecatalog}) — nunca duplicada neste pacote — e o campo
 * {@code homologationRequired} espelha o {@code rev.homologation_required}
 * daquela entrega.
 *
 * <p>Regra do prestador de terceiro (decisao de desenho desta camada,
 * documentada no relatorio): {@code thirdPartyProviderId} e OBRIGATORIO
 * quando {@code owner} e {@link ChargeOwner#THIRD_PARTY} (identifica o
 * convenio/prestador dono do valor, no espirito do contrato da ED-25) e
 * deve ser {@code null} quando {@code owner} e {@link ChargeOwner#OWN}
 * (cobranca propria nao tem prestador de terceiro; preencher o campo
 * seria contradicao de dono e e recusado).
 *
 * <p>Campos acrescentados na correcao da Camada 1 (reconciliacao com o
 * DESENHO-CAMADAS.md da Camada 0, secao 1):
 * <ul>
 *   <li>{@code fieldCalculable} — se a cobranca pode ser calculada em
 *       campo (consumo da Camada 4); vale para TODAS as cobrancas e
 *       nasce {@code false} nas formas anteriores do construtor;</li>
 *   <li>{@code accountingKeyRef} — referencia opaca e anulavel ao
 *       de-para contabil; o conteudo do de-para e da Camada 3/ED-32,
 *       esta camada so carrega a referencia, sem interpreta-la;</li>
 *   <li>{@code municipalityIbge} — escopo municipal TIPADO da definicao
 *       (ver paragrafo proprio abaixo).</li>
 * </ul>
 *
 * <p><b>Escopo municipal (decisao do dono, item 45; adendo de desenho
 * da Camada 0, D-01 e premissas PM-01/PM-05):</b> o ultimo campo,
 * {@code municipalityIbge}, e o escopo da definicao:
 * <ul>
 *   <li>{@code null} = escopo <b>"empresa"</b>: a definicao vale para
 *       todo o tenant — e o comportamento vigente, preservado (toda
 *       definicao sem escopo municipal comporta-se exactamente como
 *       antes do item 45);</li>
 *   <li>valor presente = escopo <b>municipal</b>: a definicao vale so
 *       para as unidades consumidoras cujo municipio derivado e esse
 *       IBGE. O valor e o codigo IBGE de <b>exactamente 7 digitos</b>
 *       numericos do cadastro territorial vigente — o mesmo codigo que
 *       vive em {@code localities.municipality_ibge}, com a tabela
 *       mestre {@code ibge_municipalities} (registry). O billing nunca
 *       digita este codigo por conta propria: recebe-o derivado da
 *       cadeia territorial da unidade consumidora (PM-05); aqui ele e
 *       apenas declarado na definicao e validado na forma.</li>
 * </ul>
 * A unicidade do codigo e por (tenant, escopo): o mesmo {@code code}
 * pode coexistir no escopo empresa e em municipios diferentes, e e
 * recusado dentro do mesmo escopo (ver {@link ChargeCatalog}). Na
 * resolucao, a definicao municipal do municipio da unidade vence a de
 * empresa para o mesmo codigo (precedencia, INV-C1-04-v2 — ver
 * {@link ChargeResolver}). A versao NAO tem escopo proprio: herda o
 * desta definicao (PM-02). Este campo substitui, na mesma posicao, o
 * antigo campo generico de escopo da mecanica pre-item 45: a semantica
 * "generica, sem significado atribuido" esta supersedida pela decisao
 * do item 45 — o escopo nasce real, tipado e com regra de resolucao.
 *
 * <p>O tenant NAO e campo deste record: como nos records vizinhos
 * vigentes, o tenant e a fronteira do repositorio/RLS; o registro em
 * memoria e montado ja dentro de um tenant pelo chamador.
 *
 * <p>O catalogo nasce vazio (precedente DAD-20 da P2.5: "nenhum dado
 * inventado"): nenhum codigo de cobranca e semeado nem embutido aqui —
 * codigo e politica do tenant/agencia.
 *
 * @param id                   identificador da definicao
 * @param code                 codigo da cobranca, unico por tenant e escopo
 * @param name                 nome/descricao da cobranca
 * @param owner                dono da cobranca: propria ou de terceiro
 * @param revenueNature        natureza da receita (enum da ED-34)
 * @param calculationModel     modelo de calculo declarado (secao 2.2)
 * @param thirdPartyProviderId prestador/convenio quando de terceiro;
 *                             obrigatorio para THIRD_PARTY, nulo para OWN
 * @param homologationRequired se a cobranca exige homologacao regulatoria
 * @param active               se a definicao esta ativa no catalogo
 * @param fieldCalculable      se a cobranca e calculavel em campo
 * @param accountingKeyRef     referencia opaca ao de-para contabil
 *                             (Camada 3/ED-32); anulavel
 * @param municipalityIbge     escopo municipal tipado: codigo IBGE de
 *                             exactamente 7 digitos, ou {@code null} para
 *                             o escopo "empresa" (todo o tenant)
 */
public record ChargeDefinition(UUID id, String code, String name, ChargeOwner owner,
                               RevenueNature revenueNature, CalculationModel calculationModel,
                               UUID thirdPartyProviderId, boolean homologationRequired,
                               boolean active, boolean fieldCalculable,
                               String accountingKeyRef, String municipalityIbge) {

    public ChargeDefinition {
        Objects.requireNonNull(id, "id da definicao de cobranca e obrigatorio");
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException(
                    "codigo da cobranca e obrigatorio (unico por tenant e escopo)");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("nome da cobranca e obrigatorio");
        }
        Objects.requireNonNull(owner, "dono da cobranca e obrigatorio (propria x terceiro)");
        Objects.requireNonNull(revenueNature, "natureza da receita e obrigatoria");
        Objects.requireNonNull(calculationModel, "modelo de calculo e obrigatorio");
        if (owner == ChargeOwner.THIRD_PARTY && thirdPartyProviderId == null) {
            throw new IllegalArgumentException(
                    "cobranca de terceiro exige thirdPartyProviderId (convenio/prestador): " + code);
        }
        if (owner == ChargeOwner.OWN && thirdPartyProviderId != null) {
            throw new IllegalArgumentException(
                    "cobranca propria nao tem prestador de terceiro: thirdPartyProviderId deve ser nulo: " + code);
        }
        // Escopo municipal tipado (item 45 / D-01): null = escopo
        // "empresa"; presente, tem de ser o IBGE de exactamente 7
        // digitos numericos — 6/8 digitos, letras e branco recusados.
        if (municipalityIbge != null && !municipalityIbge.matches("[0-9]{7}")) {
            throw new IllegalArgumentException(
                    "municipalityIbge invalido: codigo IBGE tem exactamente 7 digitos numericos: "
                            + municipalityIbge);
        }
        // accountingKeyRef e referencia opaca: nenhum conteudo e
        // validado nesta camada (sem semantica atribuida).
    }

    /**
     * Forma anterior a correcao da Camada 1 (sem os campos novos):
     * {@code fieldCalculable} nasce {@code false}, a referencia
     * contabil nasce nula e o escopo nasce "empresa"
     * ({@code municipalityIbge = null}) — precedente do construtor de
     * compatibilidade do {@code fixedPartValue} da P2.5.
     */
    public ChargeDefinition(UUID id, String code, String name, ChargeOwner owner,
                            RevenueNature revenueNature, CalculationModel calculationModel,
                            UUID thirdPartyProviderId, boolean homologationRequired,
                            boolean active) {
        this(id, code, name, owner, revenueNature, calculationModel, thirdPartyProviderId,
                homologationRequired, active, false, null, null);
    }

    /**
     * Forma sem o campo de escopo municipal: delega com
     * {@code municipalityIbge = null} (escopo "empresa" — vale para
     * todo o tenant, comportamento vigente).
     */
    public ChargeDefinition(UUID id, String code, String name, ChargeOwner owner,
                            RevenueNature revenueNature, CalculationModel calculationModel,
                            UUID thirdPartyProviderId, boolean homologationRequired,
                            boolean active, boolean fieldCalculable,
                            String accountingKeyRef) {
        this(id, code, name, owner, revenueNature, calculationModel, thirdPartyProviderId,
                homologationRequired, active, fieldCalculable, accountingKeyRef, null);
    }

    /** Atalho de leitura: a cobranca e de terceiro (convenio/cofaturamento)? */
    public boolean isThirdParty() {
        return owner == ChargeOwner.THIRD_PARTY;
    }

    /** Atalho de leitura: a definicao tem escopo municipal (IBGE presente)? */
    public boolean hasMunicipalScope() {
        return municipalityIbge != null;
    }
}
