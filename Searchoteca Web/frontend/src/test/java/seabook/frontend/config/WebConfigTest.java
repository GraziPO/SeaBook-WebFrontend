package seabook.frontend.config;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import seabook.frontend.service.BackendService;
import seabook.frontend.support.BackendErrors;
import seabook.frontend.support.StubHttpServer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Login obrigatório, token no cabeçalho, dados do usuário nas páginas e tratamento dos erros do backend. */
class WebConfigTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static MockHttpServletRequest loggedRequest(String role) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/acervo/livros");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("jwt", "token-123");
        session.setAttribute("username", "maria");
        session.setAttribute("role", role);
        request.setSession(session);
        return request;
    }

    // ------------------------------------------------------------------ login obrigatório

    @Test
    @DisplayName("com sessão: passa; sem sessão ou sem token: vai para o login")
    void loginRequired() throws Exception {
        LoginRequiredInterceptor interceptor = new LoginRequiredInterceptor();
        assertTrue(interceptor.preHandle(loggedRequest("root"), new MockHttpServletResponse(), null));

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(new MockHttpServletRequest("GET", "/usuarios"), response, null));
        assertEquals("/login", response.getRedirectedUrl());

        MockHttpServletRequest semToken = new MockHttpServletRequest("GET", "/usuarios");
        semToken.setSession(new MockHttpSession());
        assertFalse(interceptor.preHandle(semToken, new MockHttpServletResponse(), null));
    }

    @Test
    @DisplayName("registro do interceptor com as rotas públicas liberadas")
    void webMvcConfig() {
        assertDoesNotThrow(() -> new WebMvcConfig().addInterceptors(new InterceptorRegistry()));
    }

    // ------------------------------------------------------------------ token nas chamadas

    @Test
    @DisplayName("chamadas ao backend levam o Bearer da sessão; sem sessão, vão sem token")
    void bearerToken() throws Exception {
        try (StubHttpServer server = new StubHttpServer().on("/api/configuracoes", 200, "{}")) {
            RestClient client = new RestClientConfig().backendRestClient(server.url());

            client.get().uri("/api/configuracoes").retrieve().toBodilessEntity();
            assertNull(server.last().authorization());   // fora de uma requisição web

            MockHttpServletRequest semSessao = new MockHttpServletRequest();
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(semSessao));
            client.get().uri("/api/configuracoes").retrieve().toBodilessEntity();
            assertNull(server.last().authorization());

            semSessao.setSession(new MockHttpSession());   // sessão sem login
            client.get().uri("/api/configuracoes").retrieve().toBodilessEntity();
            assertNull(server.last().authorization());

            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(loggedRequest("root")));
            client.get().uri("/api/configuracoes").retrieve().toBodilessEntity();
            assertEquals("Bearer token-123", server.last().authorization());
        }
    }

    // ------------------------------------------------------------------ dados da sessão nas páginas

    @Test
    @DisplayName("página de quem está logado: usuário, perfil, menu e endereço atual; vendas consultadas uma vez")
    void sessionModel() {
        BackendService backend = mock(BackendService.class);
        when(backend.salesEnabled()).thenReturn(false);
        SessionModelAdvice advice = new SessionModelAdvice(backend);

        MockHttpServletRequest request = loggedRequest("loan_op");
        request.setQueryString("page=2");
        ExtendedModelMap model = new ExtendedModelMap();
        advice.addCurrentUser(request, model);

        assertEquals("maria", model.get("currentUser"));
        assertEquals("Operador de empréstimo", model.get("currentRoleLabel"));
        assertFalse(((RoleAccess) model.get("nav")).sales());
        assertEquals("/acervo/livros?page=2", model.get("currentUri"));

        advice.addCurrentUser(request, new ExtendedModelMap());   // já guardado na sessão
        verify(backend, times(1)).salesEnabled();
    }

    @Test
    @DisplayName("visitante (sem login): menu vazio e sem consultar o backend")
    void visitor() {
        BackendService backend = mock(BackendService.class);
        ExtendedModelMap model = new ExtendedModelMap();
        new SessionModelAdvice(backend).addCurrentUser(new MockHttpServletRequest("GET", "/acervo/consulta"), model);
        assertNull(model.get("currentUser"));
        assertEquals("/acervo/consulta", model.get("currentUri"));
        verifyNoInteractions(backend);
    }

    // ------------------------------------------------------------------ erros do backend

    private static HttpServletRequest requestWithFlash(MockHttpServletRequest request) {
        request.setAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE, new FlashMap());
        return request;
    }

    @Test
    @DisplayName("401: encerra a sessão e volta ao login com aviso")
    void expiredSession() {
        MockHttpServletRequest request = loggedRequest("root");
        MockHttpSession session = (MockHttpSession) request.getSession();
        ModelAndView mv = new BackendErrorAdvice().handleBackendError(BackendErrors.status(401), requestWithFlash(request));
        assertEquals("redirect:/login", mv.getViewName());
        assertTrue(session.isInvalid());
        FlashMap flash = (FlashMap) request.getAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE);
        assertEquals("Sua sessão expirou. Entre novamente.", flash.get("erro"));
    }

    @Test
    @DisplayName("403/404/409/400/500: página de erro com o status e a mensagem (do backend ou padrão)")
    void errorPage() {
        BackendErrorAdvice advice = new BackendErrorAdvice();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/x");

        ModelAndView mv = advice.handleBackendError(BackendErrors.status(409, "Livro tem exemplares."), request);
        assertEquals("erro", mv.getViewName());
        assertEquals(409, mv.getModel().get("status"));
        assertEquals("Livro tem exemplares.", mv.getModel().get("mensagem"));
        assertEquals(409, mv.getStatus().value());

        assertEquals("Você não tem permissão para esta ação.",
                advice.handleBackendError(BackendErrors.status(403), request).getModel().get("mensagem"));
        assertEquals("Registro não encontrado.",
                advice.handleBackendError(BackendErrors.status(404), request).getModel().get("mensagem"));
        assertEquals("Operação não permitida no estado atual dos dados.",
                advice.handleBackendError(BackendErrors.status(409), request).getModel().get("mensagem"));
        assertEquals("Dados inválidos. Confira o formulário.",
                advice.handleBackendError(BackendErrors.status(400), request).getModel().get("mensagem"));
        assertEquals("Ocorreu um erro no servidor. Tente novamente.",
                advice.handleBackendError(BackendErrors.status(500), request).getModel().get("mensagem"));
    }

    @Test
    @DisplayName("401 sem sessão aberta também volta ao login")
    void expiredWithoutSession() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        assertEquals("redirect:/login",
                new BackendErrorAdvice().handleBackendError(BackendErrors.status(401), requestWithFlash(request)).getViewName());
    }

    @Test
    @DisplayName("backend fora do ar: página 503")
    void offline() {
        ModelAndView mv = new BackendErrorAdvice().handleBackendOffline(BackendErrors.offline(), new MockHttpServletRequest());
        assertEquals(503, mv.getModel().get("status"));
        assertTrue(((String) mv.getModel().get("mensagem")).startsWith("Não foi possível conectar"));
    }
}
