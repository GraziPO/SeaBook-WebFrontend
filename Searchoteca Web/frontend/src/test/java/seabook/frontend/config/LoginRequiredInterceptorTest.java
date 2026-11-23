package seabook.frontend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.junit.jupiter.api.Assertions.*;

/** A tela Localização abre sem login só para consulta (GET); o resto continua exigindo sessão. */
class LoginRequiredInterceptorTest {

    private final LoginRequiredInterceptor interceptor = new LoginRequiredInterceptor();

    private boolean anonymous(String method, String uri) throws Exception {
        return interceptor.preHandle(new MockHttpServletRequest(method, uri), new MockHttpServletResponse(), null);
    }

    @Test
    @DisplayName("sem login: GET da tela Localização, do modal Detalhes e dos endereços antigos passam")
    void publicReads() throws Exception {
        assertTrue(anonymous("GET", "/acervo/departamentos"));
        assertTrue(anonymous("GET", "/acervo/departamentos/"));
        assertTrue(anonymous("GET", "/acervo/departamentos/LITBR"));
        assertTrue(anonymous("GET", "/acervo/localizacoes"));
        assertTrue(anonymous("GET", "/acervo/localizacoes/LITBR-E01P1"));
        assertTrue(anonymous("GET", "/acervo/localizacoes/LITBR-E01P1/detalhes"));
    }

    @Test
    @DisplayName("sem login: cadastrar, editar e excluir (POST) vão para o login")
    void writesNeedLogin() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(new MockHttpServletRequest("POST", "/acervo/departamentos"), response, null));
        assertEquals("/login", response.getRedirectedUrl());
        assertFalse(anonymous("POST", "/acervo/departamentos/LITBR/editar"));
        assertFalse(anonymous("POST", "/acervo/departamentos/LITBR/excluir"));
        assertFalse(anonymous("POST", "/acervo/localizacoes"));
        assertFalse(anonymous("POST", "/acervo/localizacoes/L1/editar"));
    }

    @Test
    @DisplayName("sem login: outras telas do acervo continuam fechadas, mesmo com GET")
    void otherPagesStayClosed() throws Exception {
        assertFalse(anonymous("GET", "/acervo/livros"));
        assertFalse(anonymous("GET", "/acervo/movimentacoes"));
        assertFalse(anonymous("GET", "/acervo/localizacoes/L1/etiqueta"));
        assertFalse(anonymous("GET", "/acervo/departamentos-x"));
        assertFalse(anonymous("GET", "/usuarios"));
    }

    @Test
    @DisplayName("com login: tudo passa")
    void loggedIn() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/acervo/departamentos");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("jwt", "token");
        request.setSession(session);
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), null));
    }
}
