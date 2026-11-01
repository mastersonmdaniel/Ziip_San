package br.com.ziipsan.app.web;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.platform.security.ProblemResponses;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.List;

/**
 * Todo erro da API sai em problem+json (RFC 9457), com {@code errors[]} e {@code warnings[]} no formato
 * que o frontend lê. Nenhuma resposta carrega stack trace, SQL ou mensagem interna: o detalhe vai
 * para o log, junto do identificador da requisição.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApi(ApiException ex) {
        ProblemDetail pd = problem(ex.status(), ex.code(), titleFor(ex.status()), ex.getMessage());
        if (!ex.errors().isEmpty()) {
            pd.setProperty("errors", ex.errors());
        }
        if (!ex.warnings().isEmpty()) {
            pd.setProperty("warnings", ex.warnings());
        }
        return respond(pd);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex) {
        return respond(problem(409, "stale-version", "Conflito",
                "O registro foi alterado por outra pessoa. Recarregue e tente de novo."));
    }

    /**
     * Restrição do banco (índice único, chave estrangeira) que a validação da aplicação não pegou, em
     * geral por corrida entre duas gravações. 409 genérico: o nome da restrição e o valor ficam no log.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Restrição do banco violada na API: {}", ex.getMostSpecificCause().getClass().getSimpleName());
        return respond(problem(409, "data-conflict", "Conflito",
                "A gravação conflita com outro registro (duplicidade ou referência inválida). Recarregue e tente de novo."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        return respond(problem(403, "access-denied", "Acesso negado", "O usuário não tem permissão para esta operação."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Erro não tratado na API", ex);
        return respond(problem(500, "internal-error", "Erro interno",
                "Ocorreu um erro inesperado. Informe o identificador da requisição ao suporte."));
    }

    /** Bean Validation do corpo: 422 com um item em errors[] por campo. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        List<ApiException.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiException.FieldError(fe.getField(), fe.getCode(), fe.getDefaultMessage()))
                .toList();
        ProblemDetail pd = problem(422, "validation", "Dados inválidos", "Há campos inválidos.");
        pd.setProperty("errors", errors);
        return ResponseEntity.status(422).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(pd);
    }

    /** Erros do próprio Spring MVC (404 de rota, 405, corpo ilegível...): mesmo formato, sem detalhe interno. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        int status = statusCode.value();
        ProblemDetail pd = problem(status, "invalid-request", titleFor(status),
                status == 404 ? "Recurso não encontrado." : "A requisição não pôde ser atendida.");
        return ResponseEntity.status(status).headers(headers).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(pd);
    }

    private static ProblemDetail problem(int status, String code, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status), detail);
        pd.setType(URI.create(ProblemResponses.TYPE_PREFIX + code));
        pd.setTitle(title);
        return pd;
    }

    private static ResponseEntity<ProblemDetail> respond(ProblemDetail pd) {
        return ResponseEntity.status(pd.getStatus()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(pd);
    }

    // P0.33: titleFor trata explicitamente 401, 413 e 429 (antes caiam no default).
    private static String titleFor(int status) {
        return switch (status) {
            case 400 -> "Requisição inválida";
            case 401 -> "Não autenticado";
            case 403 -> "Acesso negado";
            case 404 -> "Não encontrado";
            case 405 -> "Método não permitido";
            case 409 -> "Conflito";
            case 413 -> "Conteúdo muito grande";
            case 422 -> "Dados inválidos";
            case 429 -> "Muitas requisições";
            default -> status >= 500 ? "Erro interno" : "Requisição inválida";
        };
    }
}
