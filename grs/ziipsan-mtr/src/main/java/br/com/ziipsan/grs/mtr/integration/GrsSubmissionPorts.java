package br.com.ziipsan.grs.mtr.integration;

// ED-104 (produtor, sessão 6d762d43-e1a9-4984-b50d-571ba0643b52) — E7/E8.
import java.util.List;
import java.util.Optional;

/**
 * Portas de submissão aos sistemas oficiais (ED-104, E7/E8).
 *
 * <p><b>Regra inviolável da trilha:</b> submissões reais ao SINIR/IMA são actos externos e NÃO
 * se simulam. Sem adapter registado, a porta recusa ruidosamente
 * ({@link SubmissionRefusedException}) e o documento fica NOT_SUBMITTED — nenhum estado
 * "sincronizado/submetido" falso é produzido por este pacote. Nenhum adapter real é entregue:
 * o leiaute oficial do WS federal não é público e o do IMA só está parcialmente confirmado —
 * [SEM FONTE], pendência P-ED104-01. A chamada ao SINIR removida na P0.25 NÃO é reposta.
 *
 * <p>A montagem de payload aqui é só a projecção interna dos dados do documento (para revisão
 * assistida pelo utilizador no sistema oficial); não é leiaute oficial de WS.
 */
public final class GrsSubmissionPorts {

    private GrsSubmissionPorts() {
    }

    /** Recusa ruidosa de submissão sem adapter — nunca uma simulação silenciosa. */
    public static class SubmissionRefusedException extends IllegalStateException {
        public SubmissionRefusedException(String message) {
            super(message);
        }
    }

    /** Resultado que SÓ um adapter real pode produzir: protocolo devolvido pelo sistema emissor. */
    public record SubmissionReceipt(String systemCode, String officialNumber, String protocol) {
        public SubmissionReceipt {
            if (systemCode == null || officialNumber == null || officialNumber.isBlank()
                    || protocol == null || protocol.isBlank()) {
                throw new IllegalArgumentException("recibo de submissão exige sistema, número oficial e protocolo");
            }
        }
    }

    /** Porta do Sistema MTR do IMA/SC (Portaria IMA 009/2026). */
    public interface ImaSubmissionPort {
        SubmissionReceipt submitMtr(SubmissionPayload payload);

        SubmissionReceipt submitDmr(SubmissionPayload payload);
    }

    /** Porta do MTR nacional / SINIR (Portaria MMA 280/2020, ADR-0004). */
    public interface SinirSyncPort {
        SubmissionReceipt syncMtr(SubmissionPayload payload);
    }

    /** Projecão interna dos dados a submeter (não é leiaute oficial — [SEM FONTE], P-ED104-01). */
    public record SubmissionPayload(String internalNumber, String documentKind,
                                    java.util.Map<String, Object> fields) {
    }

    /**
     * Registo de adapters: vazio por omissão. Os serviços de submissão consultam-no e recusam
     * quando a porta não tem adapter — é esse o comportamento entregue e testado.
     */
    public static final class AdapterRegistry {
        private ImaSubmissionPort ima;
        private SinirSyncPort sinir;

        public void registerIma(ImaSubmissionPort port) {
            this.ima = port;
        }

        public void registerSinir(SinirSyncPort port) {
            this.sinir = port;
        }

        public Optional<ImaSubmissionPort> ima() {
            return Optional.ofNullable(ima);
        }

        public Optional<SinirSyncPort> sinir() {
            return Optional.ofNullable(sinir);
        }
    }

    /**
     * Regra interestadual de SC (Portaria IMA 009/2026, art. 6º, via levantamento): fluxo com
     * origem e destino em UFs diferentes exige MTR DUPLO — sistema de ambos os estados, ou
     * IMA + SINIR. Fluxo interno a SC: só IMA. A função devolve os sistemas exigidos; não emite
     * nada.
     */
    public static List<String> requiredSystemsForFlow(String originUf, String destinationUf) {
        if (originUf == null || destinationUf == null) {
            throw new IllegalArgumentException("UF de origem e de destino são obrigatórias");
        }
        String o = originUf.trim().toUpperCase(java.util.Locale.ROOT);
        String d = destinationUf.trim().toUpperCase(java.util.Locale.ROOT);
        if (o.equals(d)) {
            return o.equals("SC") ? List.of("IMA") : List.of("SINIR");
        }
        // interestadual: sistema de ambos os estados, ou estadual de origem + SINIR
        if (o.equals("SC")) {
            return List.of("IMA", "SINIR_OR_DESTINATION_STATE");
        }
        if (d.equals("SC")) {
            return List.of("ORIGIN_STATE_OR_SINIR", "IMA");
        }
        return List.of("ORIGIN_STATE_OR_SINIR", "DESTINATION_STATE_OR_SINIR");
    }
}
