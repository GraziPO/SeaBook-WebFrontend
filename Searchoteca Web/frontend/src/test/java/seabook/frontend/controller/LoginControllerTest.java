package seabook.frontend.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import seabook.frontend.dto.AuthResult;
import seabook.frontend.dto.LegalDocument;
import seabook.frontend.service.AuthService;
import seabook.frontend.support.BackendErrors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Login em etapas (senha -> termos -> MFA -> troca de senha) e recuperação de senha. */
class LoginControllerTest {

    private AuthService auth;
    private LoginController controller;
    private MockHttpSession session;
    private RedirectAttributesModelMap flash;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        auth = mock(AuthService.class);
        controller = new LoginController(auth);
        session = new MockHttpSession();
        flash = new RedirectAttributesModelMap();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    private static AuthResult full(String role) {
        AuthResult r = new AuthResult();
        r.setToken("jwt");
        r.setUsername("maria");
        r.setRole_code(role);
        return r;
    }

    private Object flash(String key) {
        return flash.getFlashAttributes().get(key);
    }

    // ------------------------------------------------------------------ página inicial

    @Test
    @DisplayName("raiz e tela de login: logado vai para a página do perfil; visitante vai para o login")
    void rootAndLoginForm() {
        assertEquals("redirect:/login", controller.root(session));
        assertEquals("auth/login", controller.loginForm(session));
        session.setAttribute("jwt", "x");
        session.setAttribute("role", "loan_op");
        assertEquals("redirect:/acervo/consulta", controller.root(session));
        assertEquals("redirect:/", controller.loginForm(session));
    }

    // ------------------------------------------------------------------ etapa 1

    @Test
    @DisplayName("senha certa, MFA desligado: abre a sessão")
    void loginFull() {
        when(auth.login("maria", "Senha123", null)).thenReturn(full("students"));
        assertEquals("redirect:/acervo/consulta", controller.login("maria", "Senha123", null, session, request, response, flash));
        assertEquals("jwt", session.getAttribute("jwt"));
        assertEquals("students", session.getAttribute("role"));
    }

    @Test
    @DisplayName("cada desafio leva à sua etapa e guarda o token provisório")
    void challenges() {
        AuthResult terms = new AuthResult();
        terms.setTermsToken("t");
        when(auth.login("a", "x", null)).thenReturn(terms);
        assertEquals("redirect:/termos", controller.login("a", "x", null, session, request, response, flash));
        assertEquals("t", session.getAttribute("pendingTermsToken"));

        AuthResult mfa = new AuthResult();
        mfa.setMfaToken("m");
        mfa.setEmail("m***a@x.com");
        when(auth.login("b", "x", null)).thenReturn(mfa);
        assertEquals("redirect:/mfa", controller.login("b", "x", null, session, request, response, flash));
        assertNull(session.getAttribute("pendingTermsToken"));   // login novo limpa a etapa anterior
        assertEquals("m***a@x.com", session.getAttribute("pendingEmail"));

        AuthResult change = new AuthResult();
        change.setPasswordChangeToken("p");
        when(auth.login("c", "x", null)).thenReturn(change);
        assertEquals("redirect:/primeiro-acesso", controller.login("c", "x", null, session, request, response, flash));
        assertEquals("p", session.getAttribute("pendingPasswordChangeToken"));
    }

    @Test
    @DisplayName("resposta vazia ou estranha do servidor: volta ao login com aviso")
    void unexpected() {
        when(auth.login("a", "x", null)).thenReturn(null);
        assertEquals("redirect:/login", controller.login("a", "x", null, session, request, response, flash));
        assertEquals("O servidor não respondeu ao login. Tente novamente.", flash("erro"));

        when(auth.login("b", "x", null)).thenReturn(new AuthResult());
        assertEquals("redirect:/login", controller.login("b", "x", null, session, request, response, flash));
        assertEquals("Não foi possível entrar. Tente novamente.", flash("erro"));
    }

    @Test
    @DisplayName("senha errada ou conta bloqueada: mensagem do backend; sem mensagem, texto padrão")
    void loginErrors() {
        when(auth.login("maria", "x", null)).thenThrow(BackendErrors.status(429, "Conta bloqueada por 15 minutos"));
        assertEquals("redirect:/login", controller.login("maria", "x", null, session, request, response, flash));
        assertEquals("Conta bloqueada por 15 minutos", flash("erro"));

        when(auth.login("joao", "x", null)).thenThrow(BackendErrors.status(401));
        controller.login("joao", "x", null, session, request, response, flash);
        assertEquals("Usuário ou senha inválidos.", flash("erro"));
    }

    // ------------------------------------------------------------------ etapa 2: termos

    @Test
    @DisplayName("tela dos termos: sem etapa pendente volta ao login; com etapa, mostra as versões")
    void termsForm() {
        ExtendedModelMap model = new ExtendedModelMap();
        assertEquals("redirect:/login", controller.termsForm(session, model, flash));

        session.setAttribute("pendingTermsToken", "t");
        when(auth.getTerms()).thenReturn(new LegalDocument("Termos", "2.0", "# x"));
        when(auth.getPrivacyPolicy()).thenReturn(new LegalDocument("Política", "2.1", "# y"));
        assertEquals("auth/termos", controller.termsForm(session, model, flash));
        assertEquals("2.0", session.getAttribute("shownTermsVersion"));
        assertEquals("2.1", session.getAttribute("shownPrivacyVersion"));

        when(auth.getTerms()).thenThrow(BackendErrors.offline());
        assertEquals("redirect:/login", controller.termsForm(session, model, flash));
    }

    @Test
    @DisplayName("aceite: precisa marcar a caixa; aceito segue para o MFA com as versões mostradas")
    void acceptTerms() {
        assertEquals("redirect:/login", controller.acceptTerms("on", session, flash));

        session.setAttribute("pendingTermsToken", "t");
        session.setAttribute("shownTermsVersion", "2.0");
        session.setAttribute("shownPrivacyVersion", "2.0");
        assertEquals("redirect:/termos", controller.acceptTerms(null, session, flash));
        assertTrue(((String) flash("erro")).startsWith("Para continuar"));

        AuthResult mfa = new AuthResult();
        mfa.setMfaToken("m");
        when(auth.acceptTerms("t", "2.0", "2.0")).thenReturn(mfa);
        assertEquals("redirect:/mfa", controller.acceptTerms("on", session, flash));
        assertNull(session.getAttribute("pendingTermsToken"));
    }

    @Test
    @DisplayName("aceite recusado: versão nova (409) reabre os termos; token vencido volta ao login; queda de rede fica nos termos")
    void acceptTermsErrors() {
        session.setAttribute("pendingTermsToken", "t");
        when(auth.acceptTerms(any(), any(), any())).thenThrow(BackendErrors.status(409, "Os documentos mudaram"));
        assertEquals("redirect:/termos", controller.acceptTerms("on", session, flash));
        assertEquals("Os documentos mudaram", flash("erro"));

        reset(auth);
        when(auth.acceptTerms(any(), any(), any())).thenThrow(BackendErrors.status(401));
        assertEquals("redirect:/login", controller.acceptTerms("on", session, flash));
        assertNull(session.getAttribute("pendingTermsToken"));

        session.setAttribute("pendingTermsToken", "t");
        reset(auth);
        when(auth.acceptTerms(any(), any(), any())).thenThrow(BackendErrors.offline());
        assertEquals("redirect:/termos", controller.acceptTerms("on", session, flash));
    }

    @Test
    @DisplayName("documentos públicos (link da tela de login)")
    void publicDocuments() {
        ExtendedModelMap model = new ExtendedModelMap();
        when(auth.getTerms()).thenReturn(new LegalDocument("Termos", "2.0", "x"));
        assertEquals("auth/documento_legal", controller.publicTerms(model, flash));
        when(auth.getPrivacyPolicy()).thenThrow(BackendErrors.status(500));
        assertEquals("redirect:/login", controller.publicPrivacy(model, flash));
        assertEquals("Documento indisponível no momento.", flash("erro"));
    }

    // ------------------------------------------------------------------ etapa 3: MFA

    @Test
    @DisplayName("MFA: tela, código certo, código errado e reenvio")
    void mfa() {
        ExtendedModelMap model = new ExtendedModelMap();
        assertEquals("redirect:/login", controller.mfaForm(session, model));
        assertEquals("redirect:/login", controller.verifyMfa("1", session, request, response, flash));
        assertEquals("redirect:/login", controller.resendMfa(session, flash));

        session.setAttribute("pendingMfaToken", "m");
        session.setAttribute("pendingEmail", "m***a@x.com");
        assertEquals("auth/mfa", controller.mfaForm(session, model));
        assertEquals("m***a@x.com", model.get("email"));

        when(auth.verifyMfa("m", "000000")).thenThrow(BackendErrors.status(401, "Código inválido. Restam 4 tentativas."));
        assertEquals("redirect:/mfa", controller.verifyMfa("000000", session, request, response, flash));
        assertEquals("Código inválido. Restam 4 tentativas.", flash("erro"));

        AuthResult resent = new AuthResult();
        resent.setMfaToken("m2");
        resent.setEmail("novo@x.com");
        when(auth.resendMfa("m")).thenReturn(resent);
        assertEquals("redirect:/mfa", controller.resendMfa(session, flash));
        assertEquals("m2", session.getAttribute("pendingMfaToken"));
        assertEquals("Um novo código foi enviado.", flash("info"));

        when(auth.verifyMfa("m2", "123456")).thenReturn(full("root"));
        assertEquals("redirect:/acervo/consulta", controller.verifyMfa("123456", session, request, response, flash));
        assertNull(session.getAttribute("pendingMfaToken"));
        assertEquals("root", session.getAttribute("role"));
    }

    @Test
    @DisplayName("reenvio falhou ou veio sem dados novos")
    void resendEdgeCases() {
        session.setAttribute("pendingMfaToken", "m");
        when(auth.resendMfa("m")).thenReturn(new AuthResult());
        controller.resendMfa(session, flash);
        assertEquals("m", session.getAttribute("pendingMfaToken"));

        when(auth.resendMfa("m")).thenThrow(BackendErrors.status(429));
        controller.resendMfa(session, flash);
        assertTrue(((String) flash("erro")).startsWith("Não foi possível reenviar"));
    }

    // ------------------------------------------------------------------ etapa 4: primeiro acesso

    @Test
    @DisplayName("primeiro acesso: senhas diferentes, senha fraca, token vencido e sucesso")
    void firstAccess() {
        ExtendedModelMap model = new ExtendedModelMap();
        assertEquals("redirect:/login", controller.firstAccessForm(session, model));
        assertEquals("redirect:/login", controller.firstAccess("a", "a", session, request, response, flash));

        session.setAttribute("pendingPasswordChangeToken", "p");
        assertEquals("auth/primeiro_acesso", controller.firstAccessForm(session, model));
        assertNotNull(model.get("dica"));

        assertEquals("redirect:/primeiro-acesso", controller.firstAccess("Senha123", "Senha124", session, request, response, flash));
        assertEquals("As duas senhas não são iguais.", flash("erro"));

        when(auth.completeFirstAccess("p", "fraca")).thenThrow(BackendErrors.status(409, "A senha deve ter ao menos 8 caracteres."));
        assertEquals("redirect:/primeiro-acesso", controller.firstAccess("fraca", "fraca", session, request, response, flash));
        assertEquals("A senha deve ter ao menos 8 caracteres.", flash("erro"));

        when(auth.completeFirstAccess("p", "Offline12")).thenThrow(BackendErrors.offline());
        assertEquals("redirect:/primeiro-acesso", controller.firstAccess("Offline12", "Offline12", session, request, response, flash));

        when(auth.completeFirstAccess("p", "NovaSenha1")).thenReturn(full("students"));
        assertEquals("redirect:/acervo/consulta", controller.firstAccess("NovaSenha1", "NovaSenha1", session, request, response, flash));
        assertNull(session.getAttribute("pendingPasswordChangeToken"));
    }

    @Test
    @DisplayName("primeiro acesso com token vencido (401): volta ao login")
    void firstAccessExpired() {
        session.setAttribute("pendingPasswordChangeToken", "p");
        when(auth.completeFirstAccess("p", "NovaSenha1")).thenThrow(BackendErrors.status(401));
        assertEquals("redirect:/login", controller.firstAccess("NovaSenha1", "NovaSenha1", session, request, response, flash));
        assertNull(session.getAttribute("pendingPasswordChangeToken"));
    }

    // ------------------------------------------------------------------ esqueci a senha / sair

    @Test
    @DisplayName("esqueci a senha: pede o código (resposta neutra) ou mostra o erro")
    void forgotPassword() {
        assertEquals("auth/esqueci_senha", controller.forgotPasswordForm());
        assertEquals("redirect:/redefinir-senha", controller.forgotPassword("maria", flash));
        assertEquals("maria", flash("login"));
        doThrow(BackendErrors.offline()).when(auth).requestPasswordReset("joao");
        assertEquals("redirect:/esqueci-senha", controller.forgotPassword("joao", flash));
    }

    @Test
    @DisplayName("redefinir: confirmação diferente, código errado e sucesso")
    void resetPassword() {
        ExtendedModelMap model = new ExtendedModelMap();
        assertEquals("auth/redefinir_senha", controller.resetPasswordForm(model));

        assertEquals("redirect:/redefinir-senha", controller.resetPassword("maria", "1", "NovaSenha1", "Outra1234", flash));
        assertEquals("As duas senhas não são iguais.", flash("erro"));
        verify(auth, never()).resetPassword(any(), any(), any());

        doThrow(BackendErrors.status(400, "Código inválido ou expirado")).when(auth).resetPassword("maria", "000", "NovaSenha1");
        assertEquals("redirect:/redefinir-senha", controller.resetPassword("maria", "000", "NovaSenha1", "NovaSenha1", flash));
        assertEquals("Código inválido ou expirado", flash("erro"));

        assertEquals("redirect:/login", controller.resetPassword("maria", "123456", "NovaSenha1", "NovaSenha1", flash));
        assertEquals("Senha redefinida. Entre com a nova senha.", flash("info"));
    }

    @Test
    @DisplayName("sair encerra a sessão (com ou sem sessão aberta)")
    void logout() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        assertEquals("redirect:/login", controller.logout(request));
        assertTrue(session.isInvalid());
        assertEquals("redirect:/login", controller.logout(new MockHttpServletRequest()));
    }

    // ------------------------------------------------------------------ MFA lembrado (cookie de 30 min)

    private static AuthResult fullWithTrust(String role) {
        AuthResult r = full(role);
        r.setMfaTrustToken("confianca");
        r.setMfaTrustExpiresInSeconds(1800L);
        return r;
    }

    private static AuthResult mfaChallenge() {
        AuthResult r = new AuthResult();
        r.setMfaToken("m");
        r.setEmail("m***a@x.com");
        return r;
    }

    @Test
    @DisplayName("código confirmado: grava o cookie HttpOnly que lembra o navegador por 30 min")
    void verifyStoresTrustCookie() {
        session.setAttribute("pendingMfaToken", "m");
        when(auth.verifyMfa("m", "123456")).thenReturn(fullWithTrust("students"));

        assertEquals("redirect:/acervo/consulta", controller.verifyMfa("123456", session, request, response, flash));

        String cookie = response.getHeader("Set-Cookie");
        assertNotNull(cookie);
        assertTrue(cookie.startsWith(LoginController.MFA_TRUST_COOKIE + "=confianca"));
        assertTrue(cookie.contains("Max-Age=1800"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Lax"));
        assertTrue(cookie.contains("Path=/"));
        assertFalse(cookie.contains("Secure"));               // http local
    }

    @Test
    @DisplayName("em https (Railway) o cookie sai marcado como Secure")
    void trustCookieSecureOnHttps() {
        request.setSecure(true);
        session.setAttribute("pendingMfaToken", "m");
        when(auth.verifyMfa("m", "123456")).thenReturn(fullWithTrust("students"));
        controller.verifyMfa("123456", session, request, response, flash);
        assertTrue(response.getHeader("Set-Cookie").contains("Secure"));
    }

    @Test
    @DisplayName("código errado ou resposta sem token de confiança: nenhum cookie é gravado")
    void noCookieWithoutTrustToken() {
        session.setAttribute("pendingMfaToken", "m");
        when(auth.verifyMfa("m", "000000")).thenThrow(BackendErrors.status(401, "Código inválido."));
        controller.verifyMfa("000000", session, request, response, flash);
        assertNull(response.getHeader("Set-Cookie"));

        session.setAttribute("pendingMfaToken", "m");
        when(auth.verifyMfa("m", "123456")).thenReturn(full("students"));
        controller.verifyMfa("123456", session, request, response, flash);
        assertNull(response.getHeader("Set-Cookie"));
    }

    @Test
    @DisplayName("login com o cookie válido: o backend pula o MFA e a sessão abre direto")
    void loginWithTrustCookieSkipsMfa() {
        when(auth.login("maria", "Senha123", "confianca")).thenReturn(full("students"));

        assertEquals("redirect:/acervo/consulta",
                controller.login("maria", "Senha123", "confianca", session, request, response, flash));

        assertEquals("jwt", session.getAttribute("jwt"));
        assertNull(session.getAttribute("pendingMfaToken"));
        assertNull(response.getHeader("Set-Cookie"));         // cookie continua o mesmo (não renova)
    }

    @Test
    @DisplayName("login com cookie que não vale mais: pede o código e apaga o cookie")
    void loginWithStaleTrustCookieAsksMfaAndClearsIt() {
        when(auth.login("maria", "Senha123", "vencido")).thenReturn(mfaChallenge());

        assertEquals("redirect:/mfa", controller.login("maria", "Senha123", "vencido", session, request, response, flash));

        String cookie = response.getHeader("Set-Cookie");
        assertTrue(cookie.startsWith(LoginController.MFA_TRUST_COOKIE + "="));
        assertTrue(cookie.contains("Max-Age=0"));
    }

    @Test
    @DisplayName("login sem cookie que cai no MFA: não mexe em cookie nenhum")
    void loginWithoutCookieAsksMfa() {
        when(auth.login("maria", "Senha123", null)).thenReturn(mfaChallenge());
        assertEquals("redirect:/mfa", controller.login("maria", "Senha123", null, session, request, response, flash));
        assertNull(response.getHeader("Set-Cookie"));
    }

    @Test
    @DisplayName("primeiro acesso concluído: grava o cookie novo (a senha trocada invalidou o antigo)")
    void firstAccessStoresTrustCookie() {
        session.setAttribute("pendingPasswordChangeToken", "p");
        when(auth.completeFirstAccess("p", "NovaSenha1")).thenReturn(fullWithTrust("students"));
        controller.firstAccess("NovaSenha1", "NovaSenha1", session, request, response, flash);
        assertTrue(response.getHeader("Set-Cookie").startsWith(LoginController.MFA_TRUST_COOKIE + "=confianca"));
    }

    @Test
    @DisplayName("sair não apaga o cookie: entrar de novo em até 30 min não pede o código")
    void logoutKeepsTrustCookie() {
        MockHttpServletRequest withSession = new MockHttpServletRequest();
        withSession.setSession(session);
        controller.logout(withSession);
        assertTrue(session.isInvalid());
    }
}
