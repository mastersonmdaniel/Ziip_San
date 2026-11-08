package br.com.ziipsan.core.regulatory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Instrumento contratual regulatorio do prestador (ED-96; GES-02):
 * registro, por municipio e por servico, do instrumento sob o qual o
 * tenant presta o servico — como DADO CADASTRAL. Esta entrega nao
 * cria metas de universalizacao por municipio (ED-77) nem pleito de
 * reequilibrio (P2.34/GES-13): ela registra a CLAUSULA de
 * reequilibrio e as metas CONTRATUAIS declaradas no instrumento.
 *
 * <p><b>Nenhum dado inventado:</b> partes, ato de regulacao, vigencia,
 * metas, aditivos, clausulas e documentos sao FATOS DO INSTRUMENTO
 * do tenant, declarados no cadastro. Nenhum texto de clausula, prazo
 * ou valor regulatorio e semeado por esta entrega: clausula ausente
 * fica AUSENTE (nula) e aparece como lacuna na completude
 * ({@link RegulatoryContractService#completeness}) — nunca e
 * preenchida com texto presumido. Onde o aceite remete a norma sem
 * fixar conteudo (NR 8/ANA nas metas, NR 5/ANA na matriz, NR 3/ANA na
 * indenizacao), esta entrega guarda a REFERENCIA declarada pelo
 * tenant, nao o conteudo da norma.
 */
public final class RegulatoryContract {

    private RegulatoryContract() {
    }

    /**
     * Tipo do instrumento — dominio do parametro {@code reg.contract_type}
     * (enum, classe d, REG) e do campo cadastral de cada registro.
     * Os cinco valores sao os do aceite, sem nenhum outro.
     */
    public enum ContractType {
        PROGRAM_CONTRACT, CONCESSION, PPP, SUBDELEGATION, DIRECT_PROVISION;

        /** Sob contrato formal? Prestacao direta nao e instrumento contratual. */
        public boolean isContractual() {
            return this != DIRECT_PROVISION;
        }
    }

    /**
     * Servico prestado sob o instrumento. Componentes do saneamento
     * basico (Lei 11.445/2007, art. 3o): o registro e por municipio E
     * por servico — agua e esgoto sob o mesmo instrumento sao DOIS
     * registros, um por servico.
     */
    public enum ServiceKind { WATER_SUPPLY, SEWAGE, SOLID_WASTE, URBAN_DRAINAGE }

    /** Partes do instrumento (fato cadastral declarado). */
    public record Parties(String grantor, String provider, String regulatorAgency) {
        public Parties {
            if (grantor == null || grantor.isBlank())
                throw new IllegalArgumentException("instrumento sem poder concedente/titular (parte obrigatoria)");
            if (provider == null || provider.isBlank())
                throw new IllegalArgumentException("instrumento sem prestador (parte obrigatoria)");
        }
    }

    /** Vigencia do instrumento: inicio obrigatorio; fim nulo = sem data declarada [A DEFINIR no cadastro]. */
    public record Validity(LocalDate validFrom, LocalDate validTo) {
        public Validity {
            Objects.requireNonNull(validFrom, "vigencia sem data de inicio");
            if (validTo != null && !validTo.isAfter(validFrom))
                throw new IllegalArgumentException("vigencia: fim deve ser posterior ao inicio");
        }

        public boolean covers(LocalDate date) {
            return !date.isBefore(validFrom) && (validTo == null || !date.isAfter(validTo));
        }

        public boolean overlaps(Validity other) {
            LocalDate end = validTo == null ? LocalDate.MAX : validTo;
            LocalDate otherEnd = other.validTo == null ? LocalDate.MAX : other.validTo;
            return !validFrom.isAfter(otherEnd) && !other.validFrom.isAfter(end);
        }
    }

    /**
     * Clausula de reequilibrio economico-financeiro: apenas a
     * REFERENCIA (numero/identificacao da clausula no instrumento) e
     * a descricao declarada. O PLEITO de reequilibrio e da P2.34
     * (GES-13) — fora desta entrega.
     */
    public record RebalancingClause(String clauseRef, String description) {
        public RebalancingClause {
            if (clauseRef == null || clauseRef.isBlank())
                throw new IllegalArgumentException("clausula de reequilibrio sem referencia no instrumento");
        }
    }

    /**
     * Indenizacao de investimentos nao amortizados (NR 3/ANA):
     * apenas a referencia da clausula e o metodo COMO DECLARADO no
     * instrumento. Nenhuma formula de indenizacao e fixada por esta
     * entrega — o metodo e texto do contrato do tenant [SEM FONTE
     * para qualquer formula presumida].
     */
    public record UnamortizedIndemnity(String clauseRef, String methodDescription) {
        public UnamortizedIndemnity {
            if (clauseRef == null || clauseRef.isBlank())
                throw new IllegalArgumentException("indenizacao sem referencia da clausula no instrumento");
            if (methodDescription == null || methodDescription.isBlank())
                throw new IllegalArgumentException("indenizacao sem o metodo declarado no instrumento (NR 3/ANA)");
        }
    }

    /**
     * Meta contratual declarada no instrumento (ou em aditivo).
     * NAO e a meta de universalizacao por municipio/ano da ED-77:
     * aqui fica o fato contratual (descricao + alvo declarado);
     * {@code indicatorCode} opcional liga a meta ao registro unico
     * de indicadores da P3.41 quando o tenant assim declarar.
     * {@code targetValue}/{@code targetUnit} viajam juntos ou nenhum.
     */
    public record ContractGoal(UUID id, UUID tenantId, UUID contractId, String description,
                               String indicatorCode, BigDecimal targetValue, String targetUnit,
                               Integer dueYear, UUID amendmentId) {
        public ContractGoal {
            Objects.requireNonNull(id, "meta sem id");
            Objects.requireNonNull(tenantId, "meta sem tenant");
            Objects.requireNonNull(contractId, "meta sem instrumento");
            if (description == null || description.isBlank())
                throw new IllegalArgumentException("meta contratual sem descricao declarada");
            if ((targetValue == null) != (targetUnit == null || targetUnit.isBlank()))
                throw new IllegalArgumentException("meta contratual: valor-alvo e unidade viajam juntos");
            if (targetValue != null && targetUnit.isBlank())
                throw new IllegalArgumentException("meta contratual: unidade do alvo em branco");
            if (dueYear != null && (dueYear < 1900 || dueYear > 2200))
                throw new IllegalArgumentException("meta contratual: ano de vencimento fora da faixa plausivel: " + dueYear);
        }
    }

    /** Natureza do aditivo. GOAL_UPDATE_NR8 = aditivo de metas da NR 8/ANA (citado no aceite). */
    public enum AmendmentKind { GENERAL, GOAL_UPDATE_NR8, TERM_EXTENSION, ECONOMIC_REBALANCING, OTHER_DECLARED }

    /** Aditivo ao instrumento (fato cadastral; numero e data como constam do aditivo). */
    public record ContractAmendment(UUID id, UUID tenantId, UUID contractId, String amendmentRef,
                                    LocalDate signedOn, AmendmentKind kind, String description,
                                    String documentRef) {
        public ContractAmendment {
            Objects.requireNonNull(id, "aditivo sem id");
            Objects.requireNonNull(tenantId, "aditivo sem tenant");
            Objects.requireNonNull(contractId, "aditivo sem instrumento");
            if (amendmentRef == null || amendmentRef.isBlank())
                throw new IllegalArgumentException("aditivo sem referencia/numero");
            Objects.requireNonNull(signedOn, "aditivo sem data de assinatura");
            Objects.requireNonNull(kind, "aditivo sem natureza");
            if (description == null || description.isBlank())
                throw new IllegalArgumentException("aditivo sem descricao declarada");
        }
    }

    /** Tipo de documento do instrumento guardado no cadastro. */
    public enum DocumentKind { CONTRACT, AMENDMENT, REGULATION_ACT, RISK_MATRIX, OTHER }

    /** Documento do instrumento: metadado + referencia de armazenamento (o binario fica no cofre de documentos). */
    public record ContractDocument(UUID id, UUID tenantId, UUID contractId, DocumentKind kind,
                                   String title, String storageRef, LocalDate registeredOn) {
        public ContractDocument {
            Objects.requireNonNull(id, "documento sem id");
            Objects.requireNonNull(tenantId, "documento sem tenant");
            Objects.requireNonNull(contractId, "documento sem instrumento");
            Objects.requireNonNull(kind, "documento sem tipo");
            if (title == null || title.isBlank())
                throw new IllegalArgumentException("documento sem titulo");
            if (storageRef == null || storageRef.isBlank())
                throw new IllegalArgumentException("documento sem referencia de armazenamento");
            Objects.requireNonNull(registeredOn, "documento sem data de registro");
        }
    }

    /**
     * O registro do instrumento. {@code municipalityCode} e o codigo
     * IBGE de 7 digitos (mesmo formato do {@code loc_ibge} da P1.35).
     * {@code regulationAct} e o ato de regulacao como declarado
     * (lei, decreto, resolucao da agencia) — texto do tenant, nunca
     * presumido. Clausulas nulas = nao declaradas no cadastro
     * (lacuna de completude, nao erro de construcao: o cadastro
     * registra o que existe).
     */
    public record Contract(UUID id, UUID tenantId, String municipalityCode, String municipalityName,
                           ServiceKind service, ContractType contractType, String contractRef,
                           Parties parties, String regulationAct, Validity validity,
                           RebalancingClause rebalancingClause, UnamortizedIndemnity indemnity) {
        public Contract {
            Objects.requireNonNull(id, "instrumento sem id");
            Objects.requireNonNull(tenantId, "instrumento sem tenant");
            if (municipalityCode == null || !municipalityCode.matches("\\d{7}"))
                throw new IllegalArgumentException("codigo de municipio deve ser o IBGE de 7 digitos: " + municipalityCode);
            if (municipalityName == null || municipalityName.isBlank())
                throw new IllegalArgumentException("instrumento sem nome do municipio");
            Objects.requireNonNull(service, "instrumento sem servico (registro e por municipio e por servico)");
            Objects.requireNonNull(contractType, "instrumento sem tipo (reg.contract_type)");
            if (contractRef == null || contractRef.isBlank())
                throw new IllegalArgumentException("instrumento sem referencia/numero do contrato ou ato");
            Objects.requireNonNull(parties, "instrumento sem partes");
            if (regulationAct == null || regulationAct.isBlank())
                throw new IllegalArgumentException("instrumento sem ato de regulacao declarado");
            Objects.requireNonNull(validity, "instrumento sem vigencia");
        }

        public boolean underContract() {
            return contractType.isContractual();
        }
    }
}
