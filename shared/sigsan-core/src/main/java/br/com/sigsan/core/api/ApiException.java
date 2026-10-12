package br.com.sigsan.core.api;

import java.util.List;

/**
 * Erro de negócio que vira resposta HTTP em problem+json (RFC 9457) no tratador global da aplicação.
 *
 * <p>O formato dos campos extras é o que o frontend lê ({@code frontend/src/services/registry/errors.ts}):
 * {@code errors[]} com {@code field/code/message} e {@code warnings[]} com {@code code/message/confirmToken}.
 * A mensagem vai ao usuário: em português, sem dado pessoal e sem detalhe interno.
 */
public abstract class ApiException extends RuntimeException {

    /** Erro de validação de um campo. {@code field} usa o caminho do corpo (ex.: {@code customers[0].customerId}). */
    public record FieldError(String field, String code, String message) {
    }

    /** Aviso que o usuário pode confirmar reenviando o {@code confirmToken}. */
    public record Warning(String code, String message, String confirmToken) {
    }

    private final int status;
    private final String code;

    protected ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() {
        return status;
    }

    /** Sufixo do {@code type} ({@code urn:sigsan:problema:<code>}). */
    public String code() {
        return code;
    }

    public List<FieldError> errors() {
        return List.of();
    }

    public List<Warning> warnings() {
        return List.of();
    }

    /** 404: o recurso não existe para este tenant (inclusive quando existe em outro tenant). */
    public static class NotFound extends ApiException {
        public NotFound(String message) {
            super(404, "nao-encontrado", message);
        }
    }

    /** 409: versão desatualizada ou estado que impede a operação. */
    public static class Conflict extends ApiException {
        public Conflict(String code, String message) {
            super(409, code, message);
        }
    }

    /** 422: campos inválidos e/ou avisos que exigem confirmação. */
    public static class Validation extends ApiException {
        private final List<FieldError> errors;
        private final List<Warning> warnings;

        public Validation(String message, List<FieldError> errors, List<Warning> warnings) {
            super(422, warnings.isEmpty() || !errors.isEmpty() ? "validacao" : "confirmacao-necessaria", message);
            this.errors = List.copyOf(errors);
            this.warnings = List.copyOf(warnings);
        }

        public static Validation of(FieldError... errors) {
            return new Validation("Há campos inválidos.", List.of(errors), List.of());
        }

        @Override
        public List<FieldError> errors() {
            return errors;
        }

        @Override
        public List<Warning> warnings() {
            return warnings;
        }
    }

    /** 400: requisição malformada que o cliente não deveria ter montado (ex.: documento na URL). */
    public static class BadRequest extends ApiException {
        public BadRequest(String code, String message) {
            super(400, code, message);
        }
    }
}
