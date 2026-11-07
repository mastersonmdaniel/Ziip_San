package br.com.ziipsan.core.support;

import br.com.ziipsan.core.api.ApiException;

import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * P4.8: notas do tenant — CSAT por chamado encerrado e NPS por pesquisa
 * periódica (ADR-0020).
 *
 * <p><b>CSAT não é duplicado.</b> A tabela {@code support.csat_response} e
 * a escrita são da P2.15 ({@link SupportSlaService#submitCsat}, nota 1–5,
 * uma por chamado, janela de 7 dias após {@code RESOLVED}). O que a P4.8
 * acrescenta é o gatilho do aceite — "nota registrada por chamado
 * ENCERRADO": a coleta desta tarefa só aceita chamado em {@code CLOSED}
 * (o encerramento é o fim do ciclo {@code RESOLVED → CLOSED} da P1.23) e
 * delega a gravação à porta, cuja implementação de produção é o
 * {@code submitCsat} da P2.15. Chamado apenas {@code RESOLVED} continua
 * avaliável pelo fluxo da P2.15; esta porta não o substitui.
 *
 * <p><b>NPS é novo</b> e não tem tabela: NÃO há migration reservada à
 * P4.8 no plano v3.0 §3, logo nenhuma tabela foi criada nem inventada —
 * a persistência é a porta {@link NpsStore}, e a fiação JDBC depende de
 * reserva de migration pelo dono/plano (pendência declarada no
 * relatório). A regra, completa aqui: nota 0–10 (escala NPS), uma
 * resposta por tenant por período da pesquisa, classificação padrão NPS
 * (9–10 promotor, 7–8 passivo, 0–6 detrator) e índice
 * {@code %promotores − %detratores}. A resposta é DO TENANT (organização),
 * sem identificador de pessoa — coerente com a telemetria agregada da
 * mesma tarefa (PLT-42).
 */
public final class TenantSatisfaction {

    /** Porta de gravação do CSAT de chamado encerrado (produção: P2.15). */
    public interface CsatRecorder {
        void recordCsat(UUID ticketId, int rating, String comment);
    }

    /** Porta de persistência do NPS periódico (fiação pendente de migration reservada). */
    public interface NpsStore {
        boolean existsResponse(UUID tenantId, YearMonth period);

        void save(NpsResponse response);

        List<NpsResponse> responsesOfPeriod(YearMonth period);
    }

    /** Resposta NPS de um tenant num período de pesquisa. */
    public record NpsResponse(UUID tenantId, YearMonth period, int score, String comment) {
        public NpsResponse {
            Objects.requireNonNull(tenantId, "tenantId é obrigatório");
            Objects.requireNonNull(period, "period é obrigatório");
            if (score < 0 || score > 10) {
                throw new IllegalArgumentException("Nota NPS fora da escala 0–10: " + score);
            }
            if (comment != null && comment.length() > 1000) {
                throw new IllegalArgumentException("Comentário NPS de até 1000 caracteres");
            }
        }

        public Band band() {
            return Band.of(score);
        }
    }

    /** Faixas da escala NPS. */
    public enum Band {
        PROMOTER, PASSIVE, DETRACTOR;

        public static Band of(int score) {
            if (score >= 9) {
                return PROMOTER;
            }
            if (score >= 7) {
                return PASSIVE;
            }
            return DETRACTOR;
        }
    }

    /** Agregado NPS de um período (todos os tenants que responderam). */
    public record NpsAggregate(YearMonth period, int responses, int promoters, int passives,
                               int detractors, Double npsScore) {
    }

    private final CsatRecorder csatRecorder;
    private final NpsStore npsStore;

    public TenantSatisfaction(CsatRecorder csatRecorder, NpsStore npsStore) {
        this.csatRecorder = Objects.requireNonNull(csatRecorder, "csatRecorder é obrigatório");
        this.npsStore = Objects.requireNonNull(npsStore, "npsStore é obrigatório");
    }

    /**
     * Registra o CSAT de um chamado ENCERRADO ({@code CLOSED}). Qualquer
     * outro estado é conflito: a nota desta tarefa nasce do encerramento.
     */
    public void recordClosedTicketCsat(UUID ticketId, String ticketStatus, int rating, String comment) {
        Objects.requireNonNull(ticketId, "ticketId é obrigatório");
        if (!"CLOSED".equals(ticketStatus)) {
            throw new ApiException.Conflict("data-conflict",
                    "A nota da P4.8 é por chamado encerrado (CLOSED); estado atual: " + ticketStatus + ".");
        }
        if (rating < 1 || rating > 5) {
            throw ApiException.Validation.of(new ApiException.FieldError("rating", "invalid",
                    "A nota CSAT vai de 1 a 5."));
        }
        if (comment != null && comment.length() > 1000) {
            throw ApiException.Validation.of(new ApiException.FieldError("comment", "invalid",
                    "Comentário de até 1000 caracteres."));
        }
        csatRecorder.recordCsat(ticketId, rating, comment);
    }

    /** Registra a resposta NPS do tenant no período; a segunda no mesmo período é conflito. */
    public NpsResponse recordNps(NpsResponse response) {
        Objects.requireNonNull(response, "response é obrigatória");
        if (npsStore.existsResponse(response.tenantId(), response.period())) {
            throw new ApiException.Conflict("data-conflict",
                    "Este tenant já respondeu à pesquisa NPS deste período.");
        }
        npsStore.save(response);
        return response;
    }

    /** Índice NPS do período: %promotores − %detratores; sem respostas, {@code npsScore} nulo. */
    public NpsAggregate aggregate(YearMonth period) {
        Objects.requireNonNull(period, "period é obrigatório");
        List<NpsResponse> all = npsStore.responsesOfPeriod(period);
        int promoters = 0;
        int passives = 0;
        int detractors = 0;
        for (NpsResponse r : all) {
            switch (r.band()) {
                case PROMOTER -> promoters++;
                case PASSIVE -> passives++;
                case DETRACTOR -> detractors++;
            }
        }
        Double score = all.isEmpty() ? null
                : (promoters * 100.0 / all.size()) - (detractors * 100.0 / all.size());
        return new NpsAggregate(period, all.size(), promoters, passives, detractors, score);
    }
}
