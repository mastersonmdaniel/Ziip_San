package br.com.ziipsan.commercial.fiscal.homologacao;

import java.time.Instant;
import java.util.Objects;

/**
 * Orquestrador do ciclo de homologação da P3.7: amarra {@link CicloHomologacao}
 * (etapas e encerramento com resultado no diário) e
 * {@link AreaSeparadaHomologacao} (XMLs assinados e expurgo registrado) na ordem
 * que o aceite exige —
 *
 * <pre>
 * abrir → (etapas executadas e XMLs gravados SÓ na área separada) →
 * encerrar com resultado no diário → expurgar com exclusão registrada →
 * varredura pós-ciclo limpa
 * </pre>
 *
 * <p><b>Guarda aplicada neste caminho (correção D3 da revisão):</b> o serviço é
 * construído com o {@link HomologacaoFiscalGuard.ContextoHomologacao} da rodada
 * e chama {@link HomologacaoFiscalGuard#exigirHomologacao} na ABERTURA do ciclo
 * e de novo em CADA gravação de XML — nenhuma gravação acontece sem a trava
 * ({@code tpAmb=2} + e-CNPJ da Ziip/TGP) satisfeita. Além disso, a área é
 * configurada com o thumbprint esperado do próprio contexto e confere o
 * thumbprint de cada XML gravado: XML de certificado de cliente é recusado na
 * gravação, não só na abertura. O que este serviço NÃO cobre — e o roteiro
 * declara — são as operações executadas diretamente pelas portas dos motores
 * (P1.8/P2.6/P3.6) fora deste orquestrador: nelas, quem integra liga a mesma
 * guarda antes de transmitir/assinar.
 *
 * <p>O serviço não transmite nada à SEFAZ nem assina nada: emissão, cancelamento,
 * substituição, faturamento e contingência acontecem pelas portas das tarefas
 * donas (P1.8/P2.6/P3.6) durante a homologação real, e cada etapa só é marcada
 * aqui quando quem operou a declara executada com o seu registro. Declarar
 * etapa não executada é fraude de processo que este código não pode detectar —
 * por isso o encerramento exige a referência do resultado no diário, onde a
 * rodada real fica registrada (ver roteiro).
 */
public final class HomologacaoCicloService {

    private final CicloHomologacao ciclo;
    private final AreaSeparadaHomologacao area;
    private final HomologacaoFiscalGuard.ContextoHomologacao contexto;

    /**
     * Abre o ciclo JÁ sob a guarda: contexto fora da homologação
     * ({@code tpAmb=2} + e-CNPJ da Ziip/TGP) é recusado antes de qualquer
     * estado nascer.
     */
    public HomologacaoCicloService(String cicloId, Instant abertoEm,
                                   HomologacaoFiscalGuard.ContextoHomologacao contexto) {
        this.contexto = Objects.requireNonNull(contexto, "Contexto da homologação é obrigatório");
        HomologacaoFiscalGuard.exigirHomologacao(contexto);
        this.ciclo = new CicloHomologacao(cicloId, abertoEm);
        this.area = new AreaSeparadaHomologacao(ciclo, contexto.thumbprintEsperado());
    }

    public CicloHomologacao ciclo() {
        return ciclo;
    }

    public AreaSeparadaHomologacao area() {
        return area;
    }

    /** Marca uma etapa como executada (ver javadoc da classe sobre a declaração). */
    public void registrarEtapaExecutada(CicloHomologacao.Etapa etapa) {
        ciclo.registrarEtapa(etapa);
    }

    /**
     * Grava um XML assinado do ciclo na área separada (único destino admitido).
     * A guarda é revalidada a cada gravação e a área confere o thumbprint do
     * XML contra o esperado do cofre: certificado diverso é recusado aqui.
     */
    public void gravarXmlAssinado(AreaSeparadaHomologacao.XmlHomologacao xml) {
        HomologacaoFiscalGuard.exigirHomologacao(contexto);
        area.gravar(xml);
    }

    /**
     * Fecha o ciclo na ordem do aceite: encerra com o resultado no diário, apaga
     * os XMLs da área com exclusão registrada e devolve o registro. Se a área
     * estiver vazia, o encerramento é recusado ANTES de mudar a situação do
     * ciclo: ciclo sem nenhum XML assinado é ciclo que não homologou —
     * erro explícito sem encerrar (a rodada não aconteceu).
     */
    public AreaSeparadaHomologacao.RegistroExclusao encerrarEExpurgar(
            String referenciaResultadoDiario, Instant agora) {
        Objects.requireNonNull(agora, "Instante do fechamento é obrigatório");
        if (area.quantidade() == 0) {
            throw new IllegalStateException(
                    "Ciclo " + ciclo.id() + " sem nenhum XML na área separada: nenhuma homologação foi registrada");
        }
        ciclo.encerrar(referenciaResultadoDiario, agora);
        return area.expurgar(agora);
    }
}
