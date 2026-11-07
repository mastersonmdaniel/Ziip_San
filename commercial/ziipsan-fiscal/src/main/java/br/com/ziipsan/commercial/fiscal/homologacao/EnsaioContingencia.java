package br.com.ziipsan.commercial.fiscal.homologacao;

import br.com.ziipsan.commercial.fiscal.nfage.NfageProductionGuard;
import br.com.ziipsan.core.json.Json;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Ensaio de contingência da homologação (etapa {@link CicloHomologacao.Etapa#ENSAIO_CONTINGENCIA}
 * do aceite da P3.7): exercita, contra a porta da SEFAZ, a sequência —
 *
 * <ol>
 *   <li>transmissão normal recusada/indisponível (a indisponibilidade é o cenário
 *       do ensaio, provocada pelo dublê nos testes e pela janela real na
 *       homologação — ver roteiro);</li>
 *   <li>emissão em contingência com motivo registrado e tipo de emissão de
 *       contingência, SOMENTE em {@code tpAmb=2} (a trava
 *       {@link HomologacaoFiscalGuard} roda antes de tudo);</li>
 *   <li>retransmissão do documento de contingência após o retorno do serviço.</li>
 * </ol>
 *
 * <p><b>Limite honesto:</b> nesta entrega a porta {@link SefazHomologacaoPort} não
 * tem implementação — o ensaio EXECUTÁVEL aqui roda contra dublês (testes). O
 * ensaio real, contra a homologação da SEFAZ com o e-CNPJ da Ziip/TGP, é aceite
 * externo da P3.7 e segue o roteiro entregue (ver RELATORIO-P3.7.md). Nenhum
 * resultado de ensaio real é presumido por esta classe.
 */
public final class EnsaioContingencia {

    /** Desfecho de uma tentativa de transmissão à homologação da SEFAZ. */
    public enum DesfechoTransmissao { AUTORIZADO, INDISPONIVEL, REJEITADO }

    /** Porta de transmissão à homologação da SEFAZ — sem implementação neste pacote. */
    public interface SefazHomologacaoPort {
        DesfechoTransmissao transmitir(String accessKey, boolean emContingencia);
    }

    /**
     * @param accessKey          chave de acesso do documento do ensaio
     * @param motivoContingencia motivo registrado da contingência (obrigatório)
     * @param contexto           contexto da homologação (trava aplicada antes do ensaio)
     */
    public record ComandoEnsaio(String accessKey, String motivoContingencia,
                                HomologacaoFiscalGuard.ContextoHomologacao contexto) {
    }

    /**
     * @param registroJson registro do ensaio serializado com {@link Json} do core
     */
    public record ResultadoEnsaio(String accessKey, Instant iniciadoEm, Instant concluidoEm,
                                  boolean emitidoEmContingencia, boolean retransmitidoAposRetorno,
                                  String registroJson) {
    }

    private final Clock clock;
    private final SefazHomologacaoPort sefaz;

    public EnsaioContingencia(Clock clock, SefazHomologacaoPort sefaz) {
        this.clock = Objects.requireNonNull(clock, "Clock é obrigatório (relógio injetável)");
        this.sefaz = Objects.requireNonNull(sefaz, "Porta da SEFAZ de homologação é obrigatória");
    }

    public ResultadoEnsaio executar(ComandoEnsaio cmd) {
        Objects.requireNonNull(cmd, "Comando do ensaio de contingência é obrigatório");
        // Nada de contingência fora da homologação tpAmb=2 com o e-CNPJ da Ziip/TGP.
        HomologacaoFiscalGuard.exigirHomologacao(cmd.contexto());
        if (cmd.contexto().contexto().ambiente() != NfageProductionGuard.Ambiente.HOMOLOGATION) {
            throw new IllegalStateException("Ensaio de contingência é da homologação (tpAmb=2)");
        }
        if (cmd.accessKey() == null || !cmd.accessKey().matches("\\d{44}")) {
            throw new IllegalArgumentException("Ensaio exige a chave de acesso de 44 posições do documento");
        }
        if (cmd.motivoContingencia() == null || cmd.motivoContingencia().isBlank()) {
            throw new IllegalArgumentException("Ensaio de contingência exige o motivo da contingência");
        }

        Instant inicio = clock.instant();

        // 1) O cenário do ensaio é a indisponibilidade: se a transmissão normal
        // autorizar de primeira, não houve contingência a ensaiar — erro explícito.
        DesfechoTransmissao normal = sefaz.transmitir(cmd.accessKey(), false);
        if (normal != DesfechoTransmissao.INDISPONIVEL) {
            throw new IllegalStateException(
                    "Ensaio de contingência exige o serviço indisponível na tentativa normal (recebido: "
                            + normal + ") — sem indisponibilidade, não há contingência ensaiada");
        }

        // 2) Emissão em contingência (motivo registrado no registro do ensaio).
        DesfechoTransmissao contingencia = sefaz.transmitir(cmd.accessKey(), true);
        if (contingencia == DesfechoTransmissao.REJEITADO) {
            throw new IllegalStateException("Documento de contingência rejeitado na homologação durante o ensaio");
        }

        // 3) Retorno do serviço: retransmissão do documento emitido em contingência.
        DesfechoTransmissao retorno = sefaz.transmitir(cmd.accessKey(), false);
        if (retorno != DesfechoTransmissao.AUTORIZADO) {
            throw new IllegalStateException(
                    "Retransmissão após o retorno não autorizou o documento de contingência (recebido: "
                            + retorno + ")");
        }

        Instant fim = clock.instant();
        Map<String, Object> registro = new LinkedHashMap<>();
        registro.put("accessKey", cmd.accessKey());
        registro.put("tpAmb", 2);
        registro.put("motivoContingencia", cmd.motivoContingencia());
        registro.put("tentativaNormal", normal.name());
        registro.put("emissaoContingencia", contingencia.name());
        registro.put("retransmissaoAposRetorno", retorno.name());
        registro.put("iniciadoEm", inicio.toString());
        registro.put("concluidoEm", fim.toString());
        // A etapa ENSAIO_CONTINGENCIA do ciclo é registrada por quem opera, a
        // partir deste resultado (registro manual declarado no roteiro — o antigo
        // etapasSatisfeitas(), inutilizado, foi removido na limpeza D8).
        return new ResultadoEnsaio(cmd.accessKey(), inicio, fim, true, true, Json.write(registro));
    }
}
