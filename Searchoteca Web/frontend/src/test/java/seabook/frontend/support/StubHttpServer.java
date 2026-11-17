package seabook.frontend.support;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servidor HTTP local (o do próprio JDK) que faz o papel do backend nos testes dos services:
 * cada caminho responde com o status, o corpo JSON e os cabeçalhos configurados,
 * e cada requisição recebida fica guardada para conferência (método, URL, token e corpo).
 */
public final class StubHttpServer implements AutoCloseable {

    public record Reply(int status, String body, Map<String, String> headers) {}

    public record Received(String method, String pathAndQuery, String authorization, String body) {}

    private final HttpServer server;
    private final Map<String, Reply> replies = new ConcurrentHashMap<>();
    public final List<Received> received = new ArrayList<>();

    public StubHttpServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getRawPath();
            String query = exchange.getRequestURI().getRawQuery();
            byte[] in;
            try (InputStream is = exchange.getRequestBody()) {
                in = is.readAllBytes();
            }
            synchronized (received) {
                received.add(new Received(exchange.getRequestMethod(), path + (query == null ? "" : "?" + query),
                        exchange.getRequestHeaders().getFirst("Authorization"), new String(in, StandardCharsets.UTF_8)));
            }
            Reply reply = replies.getOrDefault(path, new Reply(404, "", Map.of()));
            byte[] out = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            reply.headers().forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
            exchange.sendResponseHeaders(reply.status(), out.length == 0 ? -1 : out.length);
            if (out.length > 0) {
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(out);
                }
            }
            exchange.close();
        });
        server.start();
    }

    public StubHttpServer on(String path, int status, String body) {
        replies.put(path, new Reply(status, body, Map.of()));
        return this;
    }

    public StubHttpServer on(String path, int status, String body, String headerName, String headerValue) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(headerName, headerValue);
        replies.put(path, new Reply(status, body, headers));
        return this;
    }

    /** Última requisição recebida. */
    public Received last() {
        synchronized (received) {
            return received.get(received.size() - 1);
        }
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
