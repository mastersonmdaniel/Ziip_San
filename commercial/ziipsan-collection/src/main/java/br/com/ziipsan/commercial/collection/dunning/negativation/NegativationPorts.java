package br.com.ziipsan.commercial.collection.dunning.negativation;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Portas da ED-30 (padrao de {@code DunningPorts} da P4.1): as superficies
 * vizinhas sao consumidas por interfaces, sem assinatura inventada. Porta
 * nao ligada = a operacao que a exige recusa explicitamente.
 *
 * <p><b>[SEM FONTE] — layout de arquivo:</b> NENHUMA fonte disponivel
 * (spec da ED-30, v2.15, pacotes locais) traz o layout do arquivo de
 * remessa/retorno de biro ou de central de protesto. Por isso a geracao e
 * a leitura do arquivo vivem ATRAS da porta {@link BureauFilePort}: esta
 * entrega entrega as entradas estruturadas e recebe a referencia do
 * arquivo gerado e os resultados estruturados do retorno; o FORMATO
 * (posicoes, campos, codigos de ocorrencia) esta PENDENTE da fonte do
 * canal contratado pelo tenant e NENHUM campo foi inventado aqui.
 */
public final class NegativationPorts {

    private NegativationPorts() {}

    /**
     * Situacao do titular registrada na P1.41 (CAD-65 consumido de la):
     * informa se o titular da unidade esta registrado como FALECIDO.
     * Nao ha pacote local da P1.41 acessivel nesta entrega; a integracao
     * liga o adaptador ao cadastro. Sem a porta ligada, a avaliacao
     * recusa (nunca presume "vivo").
     */
    public interface HolderSituationPort {
        HolderSituation holderSituation(UUID tenantId, UUID unitId, UUID customerId);

        /** situationCode e o codigo registrado na P1.41; deceased e a leitura dele para o portao. */
        record HolderSituation(boolean deceased, String situationCode, String situationRef) {
            public HolderSituation {
                if (situationCode == null || situationCode.isBlank())
                    throw new IllegalArgumentException("situacao do titular (P1.41) exige o codigo registrado");
            }
        }
    }

    /**
     * Carta de aviso previo com entrega registrada: a prova de entrega e
     * consultada por esta porta (a entrega registrada em si e do canal de
     * notificacao do tenant). Sem registro de entrega, nao ha prova — e a
     * inclusao e recusada pela elegibilidade.
     */
    public interface PriorNoticePort {
        /** Entrega registrada do aviso previo da unidade; vazio = sem prova de entrega. */
        java.util.Optional<DeliveryProof> deliveryProof(UUID tenantId, UUID unitId, String noticeBy);

        record DeliveryProof(String noticeRef, LocalDate deliveredOn, String proofRef) {
            public DeliveryProof {
                if (noticeRef == null || noticeRef.isBlank() || deliveredOn == null
                        || proofRef == null || proofRef.isBlank())
                    throw new IllegalArgumentException("prova de entrega exige referencia do aviso, data e prova");
            }
        }
    }

    /**
     * Arquivo de remessa/retorno de um canal de {@code neg.channels}.
     * [SEM FONTE] o layout: a porta recebe as entradas estruturadas desta
     * entrega e devolve a referencia do arquivo; o retorno e devolvido
     * ja lido em resultados estruturados, com o codigo/motivo EXATAMENTE
     * como o canal os retornou (nunca traduzidos para codigos inventados).
     */
    public interface BureauFilePort {
        GeneratedFile generateRemittance(UUID tenantId, String channel, String purpose, List<FileEntry> entries);

        List<ReturnResult> importReturn(UUID tenantId, String channel, String fileRef);

        record FileEntry(UUID listingId, UUID unitId, UUID customerId, java.math.BigDecimal amount, LocalDate dueDate) {
            public FileEntry {
                if (listingId == null || unitId == null || customerId == null
                        || amount == null || amount.signum() <= 0 || dueDate == null)
                    throw new IllegalArgumentException("entrada de remessa exige inclusao, unidade, titular, valor positivo e vencimento");
            }
        }

        record GeneratedFile(String channel, String fileRef, int entryCount, LocalDate generatedOn) {
            public GeneratedFile {
                if (channel == null || channel.isBlank() || fileRef == null || fileRef.isBlank()
                        || entryCount < 0 || generatedOn == null)
                    throw new IllegalArgumentException("arquivo gerado exige canal, referencia, contagem e data");
            }
        }

        /** Resultado de UMA entrada no retorno: aceita ou nao, com o motivo do canal, verbatim. */
        record ReturnResult(UUID listingId, boolean accepted, String channelCode, String channelMessage) {
            public ReturnResult {
                if (listingId == null) throw new IllegalArgumentException("resultado do retorno exige a inclusao");
                if (!accepted && (channelMessage == null || channelMessage.isBlank()))
                    throw new IllegalArgumentException("rejeicao do retorno exige o motivo devolvido pelo canal");
            }
        }
    }
}
