package br.com.ziipsan.commercial.fiscal.homologacao;

import br.com.ziipsan.core.json.Json;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Área separada da homologação fiscal local (P3.7; P1.8; ADR-0033; P0.28): o ÚNICO
 * lugar onde um XML assinado com o e-CNPJ da Ziip/TGP — que carrega o certificado
 * público com o CPF do responsável, na exceção consentida do ADR-0033 — pode ser
 * gravado na base local.
 *
 * <p><b>Separação por construção:</b> toda referência desta área usa o esquema
 * {@value #ESQUEMA_AREA}; qualquer URI de outro esquema — referências do
 * armazenamento imutável da P2.18, das tabelas do produto e dos backups locais
 * incluídas — é recusada na entrada: um XML da homologação nunca entra nesses
 * lugares por esta classe (a varredura de {@link VarreduraHomologacao} confere
 * o resto do disco).
 *
 * <p><b>Conferência de certificado na gravação (correção D3 da revisão):</b> a
 * área é construída com o thumbprint esperado do e-CNPJ da Ziip/TGP (lido do
 * cofre) e {@link #gravar(XmlHomologacao)} recusa qualquer XML cujo thumbprint
 * divirja dele — XML assinado com certificado de cliente não entra na área.
 *
 * <p><b>Expurgo com exclusão registrada:</b> {@link #expurgar(Instant)} só roda
 * com o ciclo {@link CicloHomologacao.Situacao#ENCERRADO} (resultado no diário);
 * devolve o {@link RegistroExclusao} — ciclo, data, quantidade, hash do lote e
 * referência do resultado no diário — serializado com {@link Json} do core. Depois do expurgo a área fica vazia e o
 * ciclo passa a EXPURGADO: qualquer XML com o certificado da Ziip/TGP encontrado
 * depois disso é violação (varredura).
 *
 * <p>Esta classe é o modelo executável da regra (memória); a gravação física na
 * pasta/volume separado do ambiente local e a exclusão física dos arquivos são
 * do roteiro e dos scripts em {@code scripts/homologacao-fiscal/}, que aplicam
 * estas mesmas regras sobre o diretório da área (ver roteiro da P3.7).
 */
public final class AreaSeparadaHomologacao {

    /** Esquema de URI exclusivo da área separada da homologação fiscal local. */
    public static final String ESQUEMA_AREA = "homologacao-fiscal-local://area-separada/";

    /**
     * @param ciclo        identificação do ciclo dono do XML
     * @param uri          URI do XML na área separada (esquema {@link #ESQUEMA_AREA})
     * @param xmlSha256    SHA-256 do XML assinado (64 hex minúsculos)
     * @param thumbprintCertificado thumbprint do certificado que assina o XML
     */
    public record XmlHomologacao(String ciclo, String uri, String xmlSha256, String thumbprintCertificado) {
        public XmlHomologacao {
            Objects.requireNonNull(ciclo, "Ciclo do XML é obrigatório");
            // Barreira ÚNICA de destino: só o esquema da área separada é aceito;
            // imutável, backups e tabelas do produto caem aqui por não serem ele.
            // (Correção D4 da revisão: a antiga segunda lista de esquemas
            // proibidos era inalcançável e foi removida.)
            if (uri == null || !uri.startsWith(ESQUEMA_AREA)) {
                throw new IllegalArgumentException(
                        "XML da homologação só pode ser gravado na área separada (" + ESQUEMA_AREA
                                + "…) — nunca no armazenamento imutável, nos backups locais nem nas tabelas do produto: "
                                + uri);
            }
            if (xmlSha256 == null || !xmlSha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("SHA-256 do XML exige 64 hex minúsculos");
            }
            Objects.requireNonNull(thumbprintCertificado, "Thumbprint do certificado do XML é obrigatório");
        }
    }

    /**
     * Exclusão registrada do fim do ciclo (aceite da P3.7): ciclo, data,
     * quantidade de XMLs apagados, hash do lote apagado e a referência do
     * resultado no diário que autorizou o encerramento. É o MESMO esquema de
     * 5 campos que o script {@code encerrar-e-expurgar-ciclo.sh} emite
     * (correção D7 da revisão: um só formato, o deste record via {@link Json}).
     */
    public record RegistroExclusao(String ciclo, Instant dataExclusao, int quantidade, String hashDoLote,
                                   String resultadoDiario) {

        /** JSON do registro, exclusivamente via {@link Json} do core. */
        public String paraJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ciclo", ciclo);
            m.put("dataExclusao", dataExclusao.toString());
            m.put("quantidade", quantidade);
            m.put("hashDoLote", hashDoLote);
            m.put("resultadoDiario", resultadoDiario);
            return Json.write(m);
        }
    }

    private final CicloHomologacao ciclo;
    private final String thumbprintCertificadoEsperado;
    private final Map<String, XmlHomologacao> xmlsPorUri = new LinkedHashMap<>();

    /**
     * @param thumbprintCertificadoEsperado thumbprint do e-CNPJ da Ziip/TGP
     *                                      (configuração do cofre local): é o
     *                                      ÚNICO certificado cujos XMLs esta
     *                                      área aceita gravar
     */
    public AreaSeparadaHomologacao(CicloHomologacao ciclo, String thumbprintCertificadoEsperado) {
        this.ciclo = Objects.requireNonNull(ciclo, "Ciclo dono da área é obrigatório");
        if (thumbprintCertificadoEsperado == null || thumbprintCertificadoEsperado.isBlank()) {
            throw new IllegalArgumentException(
                    "Thumbprint esperado do e-CNPJ da Ziip/TGP é configuração obrigatória da área separada");
        }
        this.thumbprintCertificadoEsperado = thumbprintCertificadoEsperado;
    }

    /**
     * Grava (registra) um XML assinado na área — só com o ciclo aberto, dono
     * certo e certificado igual ao esperado do cofre (correção D3: XML de
     * certificado de cliente é recusado AQUI, na gravação).
     */
    public void gravar(XmlHomologacao xml) {
        Objects.requireNonNull(xml, "XML da homologação é obrigatório");
        if (ciclo.situacao() != CicloHomologacao.Situacao.ABERTO) {
            throw new IllegalStateException(
                    "Área separada do ciclo " + ciclo.id() + " não aceita gravação fora de ciclo aberto");
        }
        if (!xml.ciclo().equals(ciclo.id())) {
            throw new IllegalArgumentException(
                    "XML do ciclo " + xml.ciclo() + " não entra na área do ciclo " + ciclo.id());
        }
        if (!VarreduraHomologacao.normalizarThumbprint(xml.thumbprintCertificado())
                .equals(VarreduraHomologacao.normalizarThumbprint(thumbprintCertificadoEsperado))) {
            throw new IllegalArgumentException(
                    "XML assinado com certificado diverso do e-CNPJ esperado da Ziip/TGP não entra na área separada"
                            + " (certificado de cliente é recusado na gravação)");
        }
        if (xmlsPorUri.putIfAbsent(xml.uri(), xml) != null) {
            throw new IllegalArgumentException("URI já gravada nesta área (sem sobrescrita): " + xml.uri());
        }
    }

    public int quantidade() {
        return xmlsPorUri.size();
    }

    public List<XmlHomologacao> xmls() {
        return List.copyOf(xmlsPorUri.values());
    }

    /**
     * Apaga todos os XMLs da área e devolve a exclusão registrada. Exige o ciclo
     * ENCERRADO (resultado no diário — ver {@link CicloHomologacao#encerrar});
     * área vazia não gera registro de exclusão de mentira: é erro explícito.
     */
    public RegistroExclusao expurgar(Instant agora) {
        Objects.requireNonNull(agora, "Instante do expurgo é obrigatório (data da exclusão registrada)");
        if (ciclo.situacao() != CicloHomologacao.Situacao.ENCERRADO) {
            throw new IllegalStateException(
                    "Expurgo exige ciclo ENCERRADO com resultado registrado no diário (situação: "
                            + ciclo.situacao() + ")");
        }
        if (xmlsPorUri.isEmpty()) {
            throw new IllegalStateException(
                    "Área separada vazia: não há exclusão a registrar no ciclo " + ciclo.id());
        }
        String hashLote = Sha256.doLote(xmlsPorUri.values().stream().map(XmlHomologacao::xmlSha256).toList());
        RegistroExclusao registro = new RegistroExclusao(ciclo.id(), agora, xmlsPorUri.size(), hashLote,
                ciclo.referenciaResultadoDiario());
        xmlsPorUri.clear();
        ciclo.marcarExpurgado();
        return registro;
    }
}
