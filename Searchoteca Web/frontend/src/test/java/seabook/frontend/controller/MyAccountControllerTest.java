package seabook.frontend.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import seabook.frontend.dto.users.AccountRequestView;
import seabook.frontend.dto.users.MyAccount;
import seabook.frontend.service.BackendService;
import seabook.frontend.support.BackendErrors;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Minha conta: dados do usuário logado e pedido de desativação do aluno. */
class MyAccountControllerTest {

    private BackendService backend;
    private MyAccountController controller;
    private ExtendedModelMap model;
    private RedirectAttributesModelMap flash;

    @BeforeEach
    void setUp() {
        backend = mock(BackendService.class);
        controller = new MyAccountController(backend);
        model = new ExtendedModelMap();
        flash = new RedirectAttributesModelMap();
    }

    private static MyAccount account(String role) {
        return new MyAccount("ana.lima", "Ana Lima", "ana@escola.com", role, null, true, false,
                null, "students".equals(role) ? "2026001" : null, null, null, "2.0", "2.0", LocalDateTime.now());
    }

    private static AccountRequestView request(String status) {
        return new AccountRequestView(1L, 5L, "ana.lima", "Ana Lima", "2026001", "DEACTIVATE", status,
                "mudança de escola", LocalDateTime.now(), null, null, null, null);
    }

    private Object flash(String key) {
        return flash.getFlashAttributes().get(key);
    }

    @Test
    @DisplayName("operador: mostra os dados e não consulta pedidos de conta")
    void operatorPage() {
        when(backend.myAccount()).thenReturn(account("loan_op"));
        assertEquals("pages/minha-conta", controller.page(model));
        assertEquals("Operador de empréstimo", ((MyAccount) model.get("conta")).roleLabel());
        assertEquals(List.of(), model.get("pedidos"));
        assertNull(model.get("pedidoPendente"));
        verify(backend, never()).myAccountRequests();
    }

    @Test
    @DisplayName("aluno: lista os pedidos e destaca o que está em análise")
    void studentPage() {
        when(backend.myAccount()).thenReturn(account("students"));
        when(backend.myAccountRequests()).thenReturn(List.of(request("REJECTED"), request("PENDING")));
        controller.page(model);
        assertEquals(2, ((List<?>) model.get("pedidos")).size());
        assertEquals("PENDING", ((AccountRequestView) model.get("pedidoPendente")).status());
    }

    @Test
    @DisplayName("aluno: erro ao listar pedidos só esconde o histórico; sessão expirada continua subindo")
    void studentRequestsFail() {
        when(backend.myAccount()).thenReturn(account("students"));
        doThrow(BackendErrors.status(403, "sem permissão")).when(backend).myAccountRequests();
        controller.page(model);
        assertEquals(List.of(), model.get("pedidos"));

        doThrow(BackendErrors.status(401, "expirou")).when(backend).myAccountRequests();
        assertThrows(RestClientResponseException.class, () -> controller.page(new ExtendedModelMap()));
    }

    @Test
    @DisplayName("pedido de desativação: exige a confirmação, envia o motivo e mostra o erro do backend")
    void requestDeactivation() {
        assertEquals("redirect:/conta", controller.requestDeactivation("motivo", null, flash));
        assertNotNull(flash("erro"));
        verifyNoInteractions(backend);

        assertEquals("redirect:/conta", controller.requestDeactivation("motivo", "true", flash));
        verify(backend).requestDeactivation("motivo");
        assertNotNull(flash("info"));

        doThrow(BackendErrors.status(409, "Devolva os livros emprestados antes de pedir a desativação da conta."))
                .when(backend).requestDeactivation(any());
        RedirectAttributesModelMap other = new RedirectAttributesModelMap();
        controller.requestDeactivation(null, "true", other);
        assertEquals("Devolva os livros emprestados antes de pedir a desativação da conta.",
                other.getFlashAttributes().get("erro"));
    }

    @Test
    @DisplayName("cancelar pedido: sucesso e erro")
    void cancelDeactivation() {
        assertEquals("redirect:/conta", controller.cancelDeactivation(flash));
        verify(backend).cancelDeactivation();
        assertNotNull(flash("info"));

        doThrow(BackendErrors.status(404, "Não há pedido de desativação em aberto.")).when(backend).cancelDeactivation();
        RedirectAttributesModelMap other = new RedirectAttributesModelMap();
        controller.cancelDeactivation(other);
        assertEquals("Não há pedido de desativação em aberto.", other.getFlashAttributes().get("erro"));
    }

    @Test
    @DisplayName("MyAccount: perfil, situação e termos")
    void dtoLogic() {
        MyAccount a = account("students");
        assertTrue(a.student());
        assertTrue(a.isActive());
        assertTrue(a.termsAccepted());
        MyAccount off = new MyAccount("x", "X", "x@x.com", "root", null, false, false, null, null, null, null, null, null, null);
        assertFalse(off.student());
        assertFalse(off.isActive());
        assertFalse(off.termsAccepted());
        assertEquals("Root (acesso total)", off.roleLabel());
    }
}
