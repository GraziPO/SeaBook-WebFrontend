package seabook.frontend.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import seabook.frontend.config.RestClientConfig;
import seabook.frontend.service.AuthService;
import seabook.frontend.support.BackendErrors;
import seabook.frontend.support.StubHttpServer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Pedido de reativação de conta desativada, feito da tela de login (sem sessão). */
class ReactivationTest {

    private StubHttpServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = new StubHttpServer();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    @DisplayName("AuthService: POST /api/auth/reativacao com usuário aparado e motivo vazio omitido")
    void service() {
        server.on("/api/auth/reativacao", 202, "{\"message\":\"ok\"}");
        AuthService auth = new AuthService(new RestClientConfig().backendRestClient(server.url()));

        auth.requestReactivation("  ana.lima ", "Senha1234", "  voltei a estudar ");
        assertEquals("POST", server.last().method());
        assertTrue(server.last().body().contains("\"username\":\"ana.lima\""));
        assertTrue(server.last().body().contains("\"reason\":\"voltei a estudar\""));

        auth.requestReactivation("ana.lima", "Senha1234", "   ");
        assertFalse(server.last().body().contains("\"reason\":\""));
    }

    @Test
    @DisplayName("controller: formulário, mensagem neutra no sucesso e erro de conexão volta ao formulário")
    void controller() {
        AuthService auth = mock(AuthService.class);
        LoginController c = new LoginController(auth);
        assertEquals("auth/reativacao", c.reactivationForm());

        RedirectAttributesModelMap flash = new RedirectAttributesModelMap();
        assertEquals("redirect:/login", c.requestReactivation("ana.lima", "Senha1234", "motivo", flash));
        verify(auth).requestReactivation("ana.lima", "Senha1234", "motivo");
        assertTrue(String.valueOf(flash.getFlashAttributes().get("info")).startsWith("Se os dados estiverem corretos"));

        doThrow(BackendErrors.offline()).when(auth).requestReactivation(any(), any(), any());
        RedirectAttributesModelMap other = new RedirectAttributesModelMap();
        assertEquals("redirect:/reativacao", c.requestReactivation("ana.lima", "x", null, other));
        assertNotNull(other.getFlashAttributes().get("erro"));
        assertEquals("ana.lima", other.getFlashAttributes().get("username"));
    }
}
