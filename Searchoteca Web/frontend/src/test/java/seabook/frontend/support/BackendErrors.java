package seabook.frontend.support;

import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Erros do backend iguais aos de produção: a exceção sai de uma chamada HTTP de verdade
 * (servidor local), então o corpo {"message": "..."} pode ser lido pelo BackendMessages.
 */
public final class BackendErrors {

    private static StubHttpServer server;

    private BackendErrors() {}

    private static synchronized StubHttpServer server() {
        if (server == null) {
            try {
                server = new StubHttpServer();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return server;
    }

    /** Resposta HTTP de erro com a mensagem do backend no corpo. */
    public static RestClientResponseException status(int status, String message) {
        String path = "/erro/" + status + "/" + Math.abs((message == null ? "" : message).hashCode());
        String body = message == null ? "" : "{\"status\":" + status + ",\"message\":\"" + message.replace("\"", "\\\"") + "\"}";
        server().on(path, status, body);
        try {
            RestClient.create(server().url()).get().uri(path).retrieve().toBodilessEntity();
        } catch (RestClientResponseException ex) {
            return ex;
        }
        throw new IllegalStateException("o servidor de teste deveria ter respondido " + status);
    }

    /** Resposta de erro sem mensagem (o frontend usa o texto padrão). */
    public static RestClientResponseException status(int status) {
        return status(status, null);
    }

    /** Backend fora do ar. */
    public static ResourceAccessException offline() {
        return new ResourceAccessException("Connection refused");
    }
}
