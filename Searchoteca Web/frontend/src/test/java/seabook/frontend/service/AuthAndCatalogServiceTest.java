package seabook.frontend.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientResponseException;
import seabook.frontend.config.RestClientConfig;
import seabook.frontend.dto.AuthResult;
import seabook.frontend.dto.LegalDocument;
import seabook.frontend.dto.catalog.BookDetail;
import seabook.frontend.dto.catalog.BookSearchResult;
import seabook.frontend.dto.catalog.PageResponse;
import seabook.frontend.support.StubHttpServer;

import static org.junit.jupiter.api.Assertions.*;

/** AuthService e CatalogService falando com um "backend" local: rota, corpo enviado e leitura da resposta. */
class AuthAndCatalogServiceTest {

    private StubHttpServer server;
    private AuthService auth;
    private CatalogService catalog;

    @BeforeEach
    void setUp() throws Exception {
        server = new StubHttpServer();
        var client = new RestClientConfig().backendRestClient(server.url());
        auth = new AuthService(client);
        catalog = new CatalogService(client);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    @DisplayName("login: envia usuário e senha e entende o desafio do MFA")
    void login() {
        server.on("/api/auth/login", 200, "{\"mfaRequired\":true,\"mfaToken\":\"m1\",\"email\":\"m***a@x.com\",\"expiresInSeconds\":600,\"campoNovo\":1}");
        AuthResult r = auth.login("maria", "Senha123");
        assertTrue(r.isMfaChallenge());
        assertEquals("m***a@x.com", r.getEmail());
        assertEquals("POST", server.last().method());
        assertTrue(server.last().body().contains("\"username\":\"maria\""));
    }

    @Test
    @DisplayName("senha errada: o 401 do backend chega como exceção")
    void loginFails() {
        server.on("/api/auth/login", 401, "{\"message\":\"Usuário ou senha inválidos\"}");
        RestClientResponseException ex = assertThrows(RestClientResponseException.class, () -> auth.login("maria", "x"));
        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("termos, MFA, reenvio e primeiro acesso devolvem a próxima etapa")
    void steps() {
        server.on("/api/auth/termos/accept", 200, "{\"mfaToken\":\"m\"}")
                .on("/api/auth/mfa/verify", 200, "{\"token\":\"jwt\",\"username\":\"maria\",\"role_code\":\"students\"}")
                .on("/api/auth/mfa/resend", 200, "{\"mfaToken\":\"m2\"}")
                .on("/api/auth/senha/primeiro-acesso", 200, "{\"token\":\"jwt2\"}");
        assertEquals("m", auth.acceptTerms("t", "2.0", "2.0").getMfaToken());
        assertTrue(server.last().body().contains("\"termsVersion\":\"2.0\""));
        AuthResult full = auth.verifyMfa("m", "123456");
        assertTrue(full.isFullyAuthenticated());
        assertEquals("students", full.getRole_code());
        assertEquals("m2", auth.resendMfa("m").getMfaToken());
        assertEquals("jwt2", auth.completeFirstAccess("p", "NovaSenha1").getToken());
    }

    @Test
    @DisplayName("esqueci/redefinir senha e documentos legais")
    void passwordAndLegal() {
        server.on("/api/auth/senha/esqueci", 200, "{\"message\":\"ok\"}")
                .on("/api/auth/senha/redefinir", 204, "")
                .on("/api/legal/termos", 200, "{\"title\":\"Termos de Uso\",\"version\":\"2.0\",\"content\":\"# Termos\"}")
                .on("/api/legal/privacidade", 200, "{\"title\":\"Política\",\"version\":\"2.0\",\"content\":\"x\"}");
        auth.requestPasswordReset("maria");
        assertTrue(server.last().body().contains("\"login\":\"maria\""));
        auth.resetPassword("maria", "123456", "NovaSenha1");
        assertTrue(server.last().body().contains("\"code\":\"123456\""));
        LegalDocument terms = auth.getTerms();
        assertEquals("2.0", terms.version());
        assertEquals("Política", auth.getPrivacyPolicy().title());
    }

    @Test
    @DisplayName("consulta pública: busca com termo aparado e página mínima 0; detalhe do livro")
    void catalog() {
        server.on("/api/public/acervo", 200, "{\"content\":[{\"isbn\":\"111\",\"title\":\"Dom Casmurro\",\"totalCopies\":2,\"availableCopies\":1}],"
                        + "\"page\":0,\"size\":20,\"totalElements\":1,\"totalPages\":1}")
                .on("/api/public/acervo/111", 200, "{\"isbn\":\"111\",\"title\":\"Dom Casmurro\",\"locations\":"
                        + "[{\"localCode\":\"L1\",\"available\":2,\"total\":3}]}");
        PageResponse<BookSearchResult> page = catalog.search("  dom ", -3);
        assertEquals("Dom Casmurro", page.content().get(0).title());
        assertEquals("/api/public/acervo?q=dom&page=0&size=20", server.last().pathAndQuery());
        catalog.search(null, 1);
        assertEquals("/api/public/acervo?q=&page=1&size=20", server.last().pathAndQuery());
        BookDetail detail = catalog.detail("111");
        assertEquals(2, detail.totalAvailable());
    }
}
