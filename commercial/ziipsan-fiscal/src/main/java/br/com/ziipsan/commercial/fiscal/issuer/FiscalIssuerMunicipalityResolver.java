package br.com.ziipsan.commercial.fiscal.issuer;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolução do emissor fiscal por município (MM-05, decisão item 45).
 *
 * <p>Classe NOVA, ao lado do acesso vigente — nenhum ficheiro entregue é
 * editado. O acesso vigente NÃO resolve emissor: a emissão
 * ({@code NfageEmissionService}, P2.6) recebe os dados do emitente
 * (CNPJ, UF, série e número já reservados) como <b>entradas do comando</b>,
 * e as operações do operador (P2.33) endereçam o emissor por
 * {@code issuerId} recebido. Quem chama continua a decidir o emissor;
 * esta classe é o mecanismo de escopo que faltava para o decidir
 * <b>por município</b>, quando o chamador tiver o município do contexto
 * de emissão (p.ex. derivado da UC pela derivação canónica do MM-01).
 *
 * <p>REDESENHO — ciclo de correcção R8-C (achado A-02 da Rodada 8,
 * 09/11/2026). O eixo municipal do emissor <b>já existe</b> no DDL real
 * da V4_5: {@code fiscal_issuers.ibge CHAR(7) NOT NULL
 * CHECK (ibge ~ '^[0-9]{7}$')} (linhas 72–73, blob {@code a1c1347e…};
 * R8 §A-02). A versão anterior desta classe (e do SQL) criava uma
 * segunda coluna, {@code municipality_ibge} anulável, com a semântica
 * "NULL = emissor da empresa" — segunda fonte de verdade, e semântica
 * incoerente (nenhum emissor existe sem {@code ibge}). O original está
 * preservado em {@code trabalho-r8c/} e no registo do ciclo
 * (CORRECAO-MM-05-R8C.md).
 *
 * <p>Semântica redesenhada (espelho do SQL pendente, agora guarda de
 * pré-condição sobre {@code fiscal_issuers.ibge}):
 * <ul>
 *   <li>o eixo de município vive <b>só no emissor</b>
 *       ({@code fiscal_issuers.ibge}); a série e o documento
 *       <b>herdam</b> o município pela cadeia emissor → série → documento
 *       — nenhuma coluna neles, nenhum estado novo aqui;</li>
 *   <li><b>não há emissor sem município</b>: {@code ibge} é NOT NULL
 *       em todos os emissores; a semântica antiga "NULL = empresa"
 *       não existe nesta versão;</li>
 *   <li>"emissor da empresa" <b>redefinido</b> (D-MM05-R8C-02): é o
 *       <b>tenant mono-emissor</b> — o único emissor do tenant é o
 *       emissor da empresa e resolve em qualquer contexto (com
 *       município, com outro município, ou sem município). É a
 *       preservação do comportamento vigente para o caso comum;</li>
 *   <li>resolver por (tenant, município): vence o emissor com
 *       {@code ibge} <b>daquele</b> município; sem ele, e sem
 *       mono-emissor, vazio — o chamador mantém o seu erro explícito
 *       de "sem emissor", como hoje. Não há fallback para um
 *       "emissor da empresa" que não existe;</li>
 *   <li>resolver <b>sem</b> município (contexto sem município): só há
 *       resposta determinística com 0 ou 1 emissor no tenant (vazio
 *       ou o único); com 2+ emissores e nada que discrimine, a
 *       resolução falha fechada — nenhuma escolha silenciosa;</li>
 *   <li><b>ambiguidade = falha fechada</b>: 2+ emissores no escopo que
 *       devia responder (municipal do município pedido, ou o tenant
 *       inteiro sem município no contexto) lançam
 *       {@link AmbiguousFiscalIssuerException} — nunca há escolha
 *       silenciosa (primeiro da lista, ordem de base de dados, etc.).
 *       A ambiguidade municipal continua real: a UNIQUE vigente é
 *       {@code (tenant_id, document_hash)} (V4_5, linha 86), <b>sem</b>
 *       UNIQUE em {@code (tenant_id, ibge)} — 2+ emissores com o mesmo
 *       {@code ibge} são possíveis no cadastro.</li>
 * </ul>
 *
 * <p><b>Porquê falha fechada e não unicidade nova:</b> nenhuma fonte
 * autoriza criar uma UNIQUE sobre {@code fiscal_issuers} — o DDL real
 * já a define por {@code (tenant_id, document_hash)} e este pacote
 * <b>não</b> cria nenhuma unicidade sobre a tabela. A defesa é a falha
 * fechada na resolução.
 *
 * <p>Classe pura, sem JPA/Spring: opera sobre o mínimo provado do
 * emissor — {@code (tenantId, id, ibge)} — onde {@code ibge} é a coluna
 * da V4_5. Nenhuma outra coluna de {@code fiscal_issuers} é presumida.
 * Emissores de outro tenant nunca resolvem (a fronteira é o
 * {@code tenant_id}, como em todas as portas fiscais do projeto).
 */
public final class FiscalIssuerMunicipalityResolver {

    private FiscalIssuerMunicipalityResolver() {
    }

    /**
     * Referência mínima a um emissor do tenant.
     *
     * @param tenantId tenant dono do emissor (schema do tenant)
     * @param id       identidade do emissor ({@code fiscal_issuers.id})
     * @param ibge     código IBGE de 7 dígitos do município do emissor
     *                 ({@code fiscal_issuers.ibge}, V4_5: NOT NULL com
     *                 CHECK de 7 dígitos — nunca null nesta versão)
     */
    public record IssuerRef(UUID tenantId, UUID id, String ibge) {
        public IssuerRef {
            Objects.requireNonNull(tenantId, "tenantId do emissor é obrigatório");
            Objects.requireNonNull(id, "id do emissor é obrigatório");
            Objects.requireNonNull(ibge,
                    "ibge do emissor é obrigatório (V4_5: fiscal_issuers.ibge NOT NULL)");
            if (!ibge.matches("[0-9]{7}")) {
                throw new IllegalArgumentException(
                        "ibge deve ter 7 dígitos (V4_5: CHECK (ibge ~ '^[0-9]{7}$'))");
            }
        }
    }

    /**
     * Falha fechada de ambiguidade: o escopo que devia responder tem 2+
     * emissores e nenhuma regra provada permite escolher entre eles.
     * É {@link IllegalStateException} (estado do cadastro), não erro de
     * argumento: os argumentos podem estar certos e o cadastro é que
     * está ambíguo para aquele escopo.
     */
    public static final class AmbiguousFiscalIssuerException extends IllegalStateException {
        public AmbiguousFiscalIssuerException(UUID tenantId, String municipalityIbge, int count) {
            super("Resolução de emissor ambígua para o tenant " + tenantId
                    + (municipalityIbge == null
                            ? " sem município no contexto"
                            : " no município " + municipalityIbge)
                    + ": " + count + " emissores no escopo — falha fechada (MM-05),"
                    + " nenhuma escolha silenciosa é feita");
        }
    }

    /**
     * Resolve o emissor por (tenant, município do contexto de emissão).
     *
     * @param issuers          emissores candidatos (o chamador lê o
     *                         cadastro do tenant; linhas de outro tenant
     *                         são ignoradas, nunca resolvem)
     * @param tenantId         tenant do contexto de emissão
     * @param municipalityIbge município do contexto (IBGE, 7 dígitos),
     *                         ou {@code null} quando o contexto não tem
     *                         município — nesse caso só há resposta
     *                         determinística com 0 ou 1 emissor no tenant
     * @return o emissor resolvido, ou vazio quando nem o escopo pedido
     *         nem o mono-emissor têm emissor
     * @throws AmbiguousFiscalIssuerException se o escopo que devia
     *         responder tiver 2+ emissores
     */
    public static Optional<IssuerRef> resolve(List<IssuerRef> issuers, UUID tenantId,
                                              String municipalityIbge) {
        Objects.requireNonNull(issuers, "lista de emissores é obrigatória");
        Objects.requireNonNull(tenantId, "tenantId é obrigatório");
        if (municipalityIbge != null && !municipalityIbge.matches("[0-9]{7}")) {
            throw new IllegalArgumentException("municipality_ibge deve ter 7 dígitos");
        }
        List<IssuerRef> mine = issuers.stream()
                .filter(i -> tenantId.equals(i.tenantId()))
                .toList();
        if (municipalityIbge != null) {
            List<IssuerRef> municipal = mine.stream()
                    .filter(i -> municipalityIbge.equals(i.ibge()))
                    .toList();
            if (municipal.size() > 1) {
                throw new AmbiguousFiscalIssuerException(tenantId, municipalityIbge, municipal.size());
            }
            if (municipal.size() == 1) {
                return Optional.of(municipal.get(0));
            }
            // Sem emissor municipal daquele município: não há fallback
            // para um "emissor da empresa" — nenhum emissor existe sem
            // ibge. Excepção: tenant mono-emissor (D-MM05-R8C-02).
            if (mine.size() == 1) {
                return Optional.of(mine.get(0));
            }
            return Optional.empty();
        }
        // Sem município no contexto: só 0 ou 1 emissor no tenant dá
        // resposta determinística; 2+ sem nada que discrimine é ambiguidade.
        if (mine.size() > 1) {
            throw new AmbiguousFiscalIssuerException(tenantId, null, mine.size());
        }
        return mine.stream().findFirst();
    }

    /**
     * Município efectivo de um emissor — e, por herança da cadeia
     * emissor → série → documento, o município efectivo de qualquer
     * série desse emissor e de qualquer documento dessa série.
     * Sempre presente: todo o emissor tem {@code ibge} (V4_5 NOT NULL).
     * Nenhuma coluna de série/documento é consultada nem presumida: a
     * herança é esta função sobre o emissor da cadeia.
     */
    public static Optional<String> effectiveMunicipality(IssuerRef issuer) {
        Objects.requireNonNull(issuer, "emissor é obrigatório");
        return Optional.of(issuer.ibge());
    }
}
