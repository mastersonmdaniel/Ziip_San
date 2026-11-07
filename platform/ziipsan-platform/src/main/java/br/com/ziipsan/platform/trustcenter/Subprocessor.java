package br.com.ziipsan.platform.trustcenter;

import java.time.Instant;
import java.util.Objects;

/**
 * P3.38: suboperador publicado na lista do Trust Center
 * ({@code platform.subprocessor}, core {@code V10_6}).
 *
 * <p>A lista em si nasce das minutas da P3.9 (contrato SaaS/DPA) — esta
 * tarefa entrega a ESTRUTURA e as regras, não os nomes: nenhum
 * suboperador é semeado na migration nem fixado neste código.
 *
 * <p>Regras (recusa explícita, sem publicar pela metade):
 * <ul>
 *   <li><b>Estrangeiro sem cláusulas-padrão não é publicável</b> — a
 *       conferência da P3.9 (Res. CD/ANPD 19/2024, LGPD arts. 33 a 36:
 *       suboperador estrangeiro sem cláusula-padrão bloqueia a
 *       aprovação) vive em {@link #requirePublishable()} e no
 *       {@code CHECK} da {@code V10_6}. A linha pode existir como
 *       rascunho interno ({@code publicVisible = false}), mas não como
 *       item público da lista;</li>
 *   <li><b>Aviso de mudança</b> (P3.9): mudança anunciada carrega
 *       anúncio e vigência em par, vigência depois do anúncio; o Trust
 *       Center exibe a mudança desde o anúncio e ela só vale na
 *       vigência ({@link #changeEffectiveAt(Instant)}).</li>
 * </ul>
 *
 * @param country país do tratamento, ISO 3166-1 alpha-2 ({@code BR} =
 *                Brasil); qualquer outro valor é "fora do país" para a
 *                regra das cláusulas-padrão
 */
public record Subprocessor(
        String name,
        String service,
        String purpose,
        String country,
        boolean processesPersonalData,
        boolean standardClausesIncorporated,
        boolean publicVisible,
        ChangeStatus changeStatus,
        Instant announcedAt,
        Instant effectiveAt) {

    public enum ChangeStatus {
        ACTIVE, CHANGE_ANNOUNCED, REMOVED
    }

    public Subprocessor {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nome do suboperador é obrigatório");
        }
        if (service == null || service.isBlank()) {
            throw new IllegalArgumentException("Serviço do suboperador " + name + " é obrigatório");
        }
        if (purpose == null || purpose.isBlank()) {
            throw new IllegalArgumentException("Finalidade do suboperador " + name + " é obrigatória");
        }
        if (country == null || !country.matches("^[A-Z]{2}$")) {
            throw new IllegalArgumentException(
                    "País do suboperador " + name + " deve ser ISO alpha-2 maiúsculo: " + country);
        }
        Objects.requireNonNull(changeStatus, "change_status do suboperador é obrigatório");
        if ((announcedAt == null) != (effectiveAt == null)) {
            throw new IllegalArgumentException(
                    "Aviso de mudança nasce em par: announced_at e effective_at são ambos nulos ou ambos presentes");
        }
        if (changeStatus == ChangeStatus.CHANGE_ANNOUNCED && announcedAt == null) {
            throw new IllegalArgumentException(
                    "Mudança anunciada do suboperador " + name + " exige announced_at/effective_at");
        }
        if (effectiveAt != null && !effectiveAt.isAfter(announcedAt)) {
            throw new IllegalArgumentException("A vigência da mudança deve ser posterior ao anúncio");
        }
    }

    public boolean isForeign() {
        return !"BR".equals(country);
    }

    /**
     * Exige que o suboperador possa aparecer na lista pública: visível e,
     * se estrangeiro, com as cláusulas-padrão da ANPD incorporadas
     * (regra da P3.9). Rascunho interno invisível não é publicação e não
     * é bloqueado aqui — quem bloqueia a APROVAÇÃO da minuta é a P3.9.
     */
    public void requirePublishable() {
        if (!publicVisible) {
            throw new IllegalArgumentException(
                    "Suboperador " + name + " não está marcado como público: nada a publicar");
        }
        if (isForeign() && !standardClausesIncorporated) {
            throw new IllegalArgumentException(
                    "Suboperador estrangeiro sem cláusulas-padrão da ANPD não é publicável: "
                            + name + " (" + country + ") — Res. CD/ANPD 19/2024, conferência da P3.9");
        }
    }

    /** Aparece na lista pública agora (visível, publicável e não removido). */
    public boolean isPubliclyListed() {
        if (!publicVisible || changeStatus == ChangeStatus.REMOVED) {
            return false;
        }
        try {
            requirePublishable();
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** A mudança anunciada já vigora neste instante? */
    public boolean changeEffectiveAt(Instant now) {
        Objects.requireNonNull(now, "now é obrigatório");
        return changeStatus == ChangeStatus.CHANGE_ANNOUNCED && effectiveAt != null && !now.isBefore(effectiveAt);
    }
}
