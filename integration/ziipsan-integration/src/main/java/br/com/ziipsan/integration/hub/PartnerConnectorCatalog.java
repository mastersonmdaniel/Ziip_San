package br.com.ziipsan.integration.hub;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * P3.37: catálogo de conectores de parceiros (GES-11, vindo da P2.26 —
 * o aceite da P2.26 remete o catálogo a ESTA tarefa). Cada entrada
 * declara nome, família, parceiro, versão do parceiro, capacidades e —
 * para PSP — a matriz de comportamento da P3.2, que o hub LÊ daqui
 * ({@link #pspMatrix()}): a matriz continua definida pela P3.2
 * ({@code PspPixPort.PspBehavior}/{@code pix_psp_profiles}); o
 * {@link PspBehaviorView} é a projeção dela no hub, sem o módulo
 * integration depender do módulo collection (o pom do integration não
 * o lista — verificado no espelho).
 *
 * <p>Entrada de PSP sem matriz é recusada no cadastro: comportamento de
 * PSP nunca é presumido (mesma regra da P3.2). Entrada duplicada
 * (nome + versão) também é recusada: o teste de contrato é por versão,
 * e duas entradas para a mesma versão tornariam a esperada ambígua.
 */
public final class PartnerConnectorCatalog {

    /**
     * Projeção, no hub, da matriz de comportamento por PSP da P3.2:
     * mesmos campos de {@code PspPixPort.PspBehavior} (reenvio, SLA do
     * callback, entrega de certificado, segredo adicional, mecanismo
     * equivalente de confirmação, capacidades JSR/aproximação).
     */
    public record PspBehaviorView(String psp, String callbackResendPolicy, int callbackSlaSeconds,
                                  String certificateDelivery, boolean supportsAdditionalSecret,
                                  Optional<String> equivalentConfirmation,
                                  boolean openFinanceJsr, boolean pixProximity) {
        public PspBehaviorView {
            if (psp == null || psp.isBlank()) {
                throw new IllegalArgumentException("PSP da matriz é obrigatório");
            }
            if (callbackResendPolicy == null || callbackResendPolicy.isBlank()) {
                throw new IllegalArgumentException("política de reenvio do callback é obrigatória (PSP " + psp + ")");
            }
            if (callbackSlaSeconds <= 0) {
                throw new IllegalArgumentException("SLA do callback deve ser > 0: " + callbackSlaSeconds);
            }
            if (certificateDelivery == null || certificateDelivery.isBlank()) {
                throw new IllegalArgumentException("entrega de certificado é obrigatória (PSP " + psp + ")");
            }
            if (equivalentConfirmation == null) {
                equivalentConfirmation = Optional.empty();
            }
        }

        /** Sem mecanismo equivalente, a baixa exige a confirmação por GET /pix/{e2eid} (regra da P3.2). */
        public boolean requiresGetPixConfirmation() {
            return equivalentConfirmation.isEmpty();
        }
    }

    /**
     * Entrada do catálogo. {@code expectedLayoutFingerprint} é a impressão
     * esperada do leiaute de homologação para o teste de contrato; nula =
     * contrato ainda não cadastrado (o teste sai NOT_RUN, nunca MATCH).
     */
    public record CatalogEntry(String name, ExternalConnector.Kind kind, String partner, String partnerVersion,
                               Set<String> capabilities, Optional<PspBehaviorView> pspBehavior,
                               String expectedLayoutFingerprint) {
        public CatalogEntry {
            IntegrationParameters.requireValidName(name);
            Objects.requireNonNull(kind, "família do conector é obrigatória no catálogo");
            if (partner == null || partner.isBlank()) {
                throw new IllegalArgumentException("parceiro é obrigatório no catálogo (" + name + ")");
            }
            if (partnerVersion == null || partnerVersion.isBlank()) {
                throw new IllegalArgumentException("versão do parceiro é obrigatória no catálogo (" + name + ")");
            }
            capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
            if (pspBehavior == null) {
                pspBehavior = Optional.empty();
            }
            if (kind == ExternalConnector.Kind.PSP && pspBehavior.isEmpty()) {
                throw new IllegalArgumentException("conector PSP " + name
                        + " exige a matriz de comportamento da P3.2 no catálogo: comportamento de PSP nunca é presumido");
            }
            if (kind != ExternalConnector.Kind.PSP && pspBehavior.isPresent()) {
                throw new IllegalArgumentException("matriz de PSP só cabe em conector PSP (" + name + ")");
            }
        }
    }

    private final Map<String, CatalogEntry> byNameAndVersion = new LinkedHashMap<>();

    public void register(CatalogEntry entry) {
        Objects.requireNonNull(entry, "entrada do catálogo é obrigatória");
        String key = entry.name() + "@" + entry.partnerVersion();
        if (byNameAndVersion.containsKey(key)) {
            throw new IllegalArgumentException("catálogo já tem entrada para " + key
                    + ": o teste de contrato é por versão e a esperada ficaria ambígua");
        }
        byNameAndVersion.put(key, entry);
    }

    public Optional<CatalogEntry> find(String name, String partnerVersion) {
        return Optional.ofNullable(byNameAndVersion.get(name + "@" + partnerVersion));
    }

    /** Entrada exigida: ausência é erro explícito (conector fora do catálogo não opera pelo hub). */
    public CatalogEntry require(String name, String partnerVersion) {
        return find(name, partnerVersion).orElseThrow(() -> new IllegalArgumentException(
                "conector fora do catálogo de parceiros: " + name + "@" + partnerVersion));
    }

    public List<CatalogEntry> entries() {
        return List.copyOf(byNameAndVersion.values());
    }

    /** A matriz de comportamento por PSP (da P3.2), como o hub a lê: uma linha por entrada PSP. */
    public List<PspBehaviorView> pspMatrix() {
        List<PspBehaviorView> out = new ArrayList<>();
        for (CatalogEntry entry : byNameAndVersion.values()) {
            entry.pspBehavior().ifPresent(out::add);
        }
        return List.copyOf(out);
    }
}
