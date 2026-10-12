package br.com.sigsan.commercial.registry.application;

import br.com.sigsan.core.api.ApiException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Acumula erros e avisos de uma gravação e lança uma única resposta 422 com tudo de uma vez: o
 * usuário corrige os campos numa volta só, em vez de descobrir um erro por envio.
 *
 * <p>Aviso confirmável: o {@code confirmToken} é derivado do código e da mensagem (que carrega os
 * números do caso, ex.: o total de economias). Reenviar o mesmo token aceita aquele aviso; se o dado
 * mudar, a mensagem muda, o token muda e o aviso volta. Sem estado no servidor.
 */
public final class Checks {

    private final List<ApiException.FieldError> errors = new ArrayList<>();
    private final List<ApiException.Warning> warnings = new ArrayList<>();
    private final Set<String> confirmed;

    public Checks(Collection<String> confirmTokens) {
        this.confirmed = confirmTokens == null ? Set.of() : Set.copyOf(confirmTokens);
    }

    public Checks() {
        this(null);
    }

    public Checks error(String field, String code, String message) {
        errors.add(new ApiException.FieldError(field, code, message));
        return this;
    }

    public Checks warning(String code, String message) {
        String token = token(code, message);
        if (!confirmed.contains(token)) {
            warnings.add(new ApiException.Warning(code, message, token));
        }
        return this;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public void throwIfAny() {
        if (errors.isEmpty() && warnings.isEmpty()) {
            return;
        }
        String message = errors.isEmpty() ? "Confirme os avisos para continuar." : "Há campos inválidos.";
        throw new ApiException.Validation(message, errors, warnings);
    }

    static String token(String code, String message) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((code + "|" + message).getBytes(StandardCharsets.UTF_8));
            return code + "." + HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
