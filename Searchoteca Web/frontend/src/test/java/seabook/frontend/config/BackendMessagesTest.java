package seabook.frontend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import seabook.frontend.support.BackendErrors;

import static org.junit.jupiter.api.Assertions.*;

/** A mensagem que aparece no formulário vem do backend; se não houver, usa o texto padrão da tela. */
class BackendMessagesTest {

    @Test
    @DisplayName("usa a mensagem do corpo do erro")
    void backendMessage() {
        assertEquals("ISBN já cadastrado.", BackendMessages.of(BackendErrors.status(409, "ISBN já cadastrado."), "padrão"));
    }

    @Test
    @DisplayName("corpo vazio ou mensagem em branco: texto padrão")
    void fallback() {
        assertEquals("padrão", BackendMessages.of(BackendErrors.status(500), "padrão"));
        assertEquals("padrão", BackendMessages.of(BackendErrors.status(400, " "), "padrão"));
        assertEquals("padrão", BackendMessages.of(new RestClientException("x"), "padrão"));
    }

    @Test
    @DisplayName("backend fora do ar: avisa que não conectou")
    void offline() {
        assertTrue(BackendMessages.of(BackendErrors.offline(), "padrão").startsWith("Não foi possível conectar"));
    }

    @Test
    @DisplayName("formulário: 401 sobe (vai para o login); os outros viram mensagem")
    void forForm() {
        RestClientResponseException expired = BackendErrors.status(401, "expirou");
        assertSame(expired, assertThrows(RestClientResponseException.class, () -> BackendMessages.forForm(expired, "x")));
        assertEquals("sem permissão", BackendMessages.forForm(BackendErrors.status(403, "sem permissão"), "x"));
    }
}
