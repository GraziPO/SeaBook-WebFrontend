package seabook.frontend.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import seabook.frontend.config.RestClientConfig;
import seabook.frontend.dto.users.MyAccount;
import seabook.frontend.support.StubHttpServer;

import static org.junit.jupiter.api.Assertions.*;

/** Rotas da tela "Minha conta" no BackendService. */
class MyAccountBackendTest {

    private StubHttpServer server;
    private BackendService service;

    @BeforeEach
    void setUp() throws Exception {
        server = new StubHttpServer();
        service = new BackendService(new RestClientConfig().backendRestClient(server.url()));
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    @DisplayName("dados do usuário logado: GET /api/usuarios/me")
    void myAccount() {
        server.on("/api/usuarios/me", 200, "{\"username\":\"ana.lima\",\"email\":\"ana@escola.com\",\"roleCode\":\"students\","
                + "\"active\":true,\"ra\":\"2026001\",\"lastActivity\":\"2026-11-20\",\"termsAcceptedAt\":\"2026-11-02T10:00:00\"}");
        MyAccount a = service.myAccount();
        assertEquals("GET", server.last().method());
        assertEquals("ana@escola.com", a.email());
        assertTrue(a.student());
        assertEquals(20, a.lastActivity().getDayOfMonth());
    }

    @Test
    @DisplayName("pedidos do aluno: listar, pedir desativação (motivo aparado) e cancelar")
    void requests() {
        server.on("/api/usuarios/me/pedidos", 200, "[{\"requestId\":1,\"type\":\"DEACTIVATE\",\"status\":\"PENDING\"}]")
                .on("/api/usuarios/me/desativacao", 201, "{}");
        assertTrue(service.myAccountRequests().get(0).pending());

        service.requestDeactivation("  vou mudar de escola  ");
        assertEquals("POST", server.last().method());
        assertTrue(server.last().body().contains("\"reason\":\"vou mudar de escola\""));

        service.requestDeactivation(" ");
        assertFalse(server.last().body().contains("\"reason\":\""));

        service.cancelDeactivation();
        assertEquals("DELETE", server.last().method());
        assertEquals("/api/usuarios/me/desativacao", server.last().pathAndQuery());
    }

    @Test
    @DisplayName("lista de pedidos vazia no corpo vira lista vazia")
    void emptyRequests() {
        server.on("/api/usuarios/me/pedidos", 200, "[]");
        assertTrue(service.myAccountRequests().isEmpty());
    }
}
