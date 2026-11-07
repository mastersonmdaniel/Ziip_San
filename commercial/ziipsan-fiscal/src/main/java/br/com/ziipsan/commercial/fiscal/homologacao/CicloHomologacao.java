package br.com.ziipsan.commercial.fiscal.homologacao;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Ciclo de testes da homologação fiscal local (P3.7). Um ciclo é a rodada de
 * homologação com começo e fim definidos: enquanto ABERTO, os XMLs assinados com o
 * e-CNPJ da Ziip/TGP vivem SÓ na área separada ({@link AreaSeparadaHomologacao});
 * o ciclo só pode ser ENCERRADO com o resultado registrado no diário (referência
 * da entrada do diário de bordo, nunca presumida); e só um ciclo ENCERRADO pode
 * ser EXPURGADO (apagamento dos XMLs com exclusão registrada).
 *
 * <p>Etapas que compõem o ciclo completo do aceite da P3.7: emissão, cancelamento
 * e substituição da NFS-e, ciclo de faturamento com NFAg-e e ensaio de
 * contingência. Etapa não executada não é marcada por presunção: o encerramento
 * exige o conjunto completo — quem rodou um ciclo parcial registra um ciclo novo
 * para o restante, em vez de encerrar este como completo.
 */
public final class CicloHomologacao {

    /** Etapas do ciclo completo de homologação do aceite da P3.7. */
    public enum Etapa {
        EMISSAO_NFSE,
        CANCELAMENTO_NFSE,
        SUBSTITUICAO_NFSE,
        CICLO_FATURAMENTO_NFAGE,
        ENSAIO_CONTINGENCIA
    }

    /** Situação do ciclo: ABERTO → ENCERRADO → EXPURGADO, sem atalhos. */
    public enum Situacao { ABERTO, ENCERRADO, EXPURGADO }

    private final String id;
    private final Instant abertoEm;
    private final EnumSet<Etapa> etapasExecutadas = EnumSet.noneOf(Etapa.class);
    private Situacao situacao = Situacao.ABERTO;
    private String referenciaResultadoDiario;
    private Instant encerradoEm;

    public CicloHomologacao(String id, Instant abertoEm) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Ciclo de homologação exige identificação (ex.: 2026-11-ciclo-01)");
        }
        this.id = id;
        this.abertoEm = Objects.requireNonNull(abertoEm, "Instante de abertura do ciclo é obrigatório");
    }

    public String id() {
        return id;
    }

    public Situacao situacao() {
        return situacao;
    }

    public Set<Etapa> etapasExecutadas() {
        return Set.copyOf(etapasExecutadas);
    }

    /** Referência da entrada do diário com o resultado (só depois do encerramento). */
    public String referenciaResultadoDiario() {
        return referenciaResultadoDiario;
    }

    public Instant encerradoEm() {
        return encerradoEm;
    }

    /** Registra uma etapa efetivamente executada neste ciclo (só com o ciclo aberto). */
    public void registrarEtapa(Etapa etapa) {
        Objects.requireNonNull(etapa, "Etapa do ciclo é obrigatória");
        if (situacao != Situacao.ABERTO) {
            throw new IllegalStateException("Ciclo " + id + " não está aberto (situação: " + situacao + ")");
        }
        etapasExecutadas.add(etapa);
    }

    /** Verdadeiro quando as cinco etapas do aceite foram executadas neste ciclo. */
    public boolean completo() {
        return etapasExecutadas.size() == Etapa.values().length;
    }

    /**
     * Encerra o ciclo com o resultado registrado no diário.
     *
     * @param referenciaResultadoDiario referência localizável da entrada do diário de
     *                                  bordo com o resultado da rodada (obrigatória)
     * @param agora                     instante do encerramento, do relógio de quem opera
     */
    public void encerrar(String referenciaResultadoDiario, Instant agora) {
        if (situacao != Situacao.ABERTO) {
            throw new IllegalStateException("Ciclo " + id + " não está aberto (situação: " + situacao + ")");
        }
        if (!completo()) {
            throw new IllegalStateException(
                    "Ciclo " + id + " incompleto: faltam as etapas " + etapasFaltantes()
                            + " — ciclo parcial não se encerra como completo");
        }
        if (referenciaResultadoDiario == null || referenciaResultadoDiario.isBlank()) {
            throw new IllegalArgumentException(
                    "Encerramento exige o resultado registrado no diário (referência da entrada é obrigatória)");
        }
        this.referenciaResultadoDiario = referenciaResultadoDiario;
        this.encerradoEm = Objects.requireNonNull(agora, "Instante de encerramento é obrigatório");
        this.situacao = Situacao.ENCERRADO;
    }

    /** Etapas ainda não executadas (para mensagem de erro e para o roteiro). */
    public Set<Etapa> etapasFaltantes() {
        EnumSet<Etapa> faltam = EnumSet.allOf(Etapa.class);
        faltam.removeAll(etapasExecutadas);
        return Set.copyOf(faltam);
    }

    /** Transição interna do expurgo — só o serviço de ciclo a chama, após apagar. */
    void marcarExpurgado() {
        if (situacao != Situacao.ENCERRADO) {
            throw new IllegalStateException(
                    "Expurgo exige ciclo ENCERRADO com resultado no diário (situação: " + situacao + ")");
        }
        situacao = Situacao.EXPURGADO;
    }
}
