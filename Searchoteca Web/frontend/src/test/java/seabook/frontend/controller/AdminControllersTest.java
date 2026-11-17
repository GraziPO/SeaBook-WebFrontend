package seabook.frontend.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import seabook.frontend.config.SessionModelAdvice;
import seabook.frontend.dto.ModalReopen;
import seabook.frontend.dto.dashboard.DashboardData;
import seabook.frontend.dto.settings.SettingDetail;
import seabook.frontend.dto.settings.SettingsSection;
import seabook.frontend.dto.settings.SettingsView;
import seabook.frontend.dto.users.AccountRequestView;
import seabook.frontend.service.BackendService;
import seabook.frontend.service.BackendService.AuditQuery;
import seabook.frontend.support.BackendErrors;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Usuários e pedidos de conta, troca da própria senha, dashboard, auditoria e configurações do root. */
class AdminControllersTest {

    private BackendService backend;
    private RedirectAttributesModelMap flash;
    private ExtendedModelMap model;

    @BeforeEach
    void setUp() {
        backend = mock(BackendService.class);
        flash = new RedirectAttributesModelMap();
        model = new ExtendedModelMap();
    }

    private Object flash(String key) {
        return flash.getFlashAttributes().get(key);
    }

    private static AccountRequestView pending(long id) {
        return new AccountRequestView(id, 2L, "maria", "Maria", "RA1", "REACTIVATE", "PENDING",
                null, null, null, null, null, "INACTIVITY");
    }

    // ------------------------------------------------------------------ troca da própria senha

    @Test
    @DisplayName("troca de senha: confirmação diferente, senha atual errada e sucesso (pede novo login)")
    void changePassword() {
        AccountController c = new AccountController(backend);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);

        assertEquals("redirect:/usuarios", c.changePassword("a", "NovaSenha1", "Outra1234", "/usuarios", request, flash));
        assertEquals("As duas senhas novas não são iguais.", flash("erroSenha"));
        assertEquals("modal-senha", flash("abrirModal"));
        verifyNoInteractions(backend);

        doThrow(BackendErrors.status(409, "Senha atual incorreta")).when(backend).changeOwnPassword("errada", "NovaSenha1");
        assertEquals("redirect:/", c.changePassword("errada", "NovaSenha1", "NovaSenha1", null, request, flash));
        assertEquals("Senha atual incorreta", flash("erroSenha"));

        assertEquals("redirect:/login", c.changePassword("certa", "NovaSenha1", "NovaSenha1", null, request, flash));
        assertTrue(session.isInvalid());
        assertEquals("redirect:/login",
                c.changePassword("certa", "NovaSenha1", "NovaSenha1", null, new MockHttpServletRequest(), flash));
    }

    // ------------------------------------------------------------------ usuários

    @Test
    @DisplayName("lista de usuários por aba com o número de pedidos pendentes")
    void userList() {
        UserController c = new UserController(backend);
        when(backend.listAccountRequests("PENDING")).thenReturn(List.of(pending(1), pending(2)));
        assertEquals("pages/usuarios", c.list("alunos", 0, model));
        verify(backend).listUsersPage("alunos", 0);
        assertEquals("/usuarios?aba=alunos", model.get("paginaUrl"));
        assertEquals(2, model.get("pedidosPendentes"));

        c.list("qualquer", 0, model);
        assertEquals("operadores", model.get("aba"));
        assertEquals("/usuarios", model.get("paginaUrl"));
    }

    @Test
    @DisplayName("contador de pendentes: falha vira 0; resposta nula vira 0; sessão expirada sobe")
    void pendingCount() {
        UserController c = new UserController(backend);
        when(backend.listAccountRequests("PENDING")).thenThrow(BackendErrors.status(403));
        c.list("operadores", 0, model);
        assertEquals(0, model.get("pedidosPendentes"));

        reset(backend);
        when(backend.listAccountRequests("PENDING")).thenThrow(BackendErrors.offline());
        c.list("operadores", 0, model);
        assertEquals(0, model.get("pedidosPendentes"));

        reset(backend);
        when(backend.listAccountRequests("PENDING")).thenReturn(null);
        c.list("operadores", 0, model);
        assertEquals(0, model.get("pedidosPendentes"));

        reset(backend);
        when(backend.listAccountRequests("PENDING")).thenThrow(BackendErrors.status(401));
        assertThrows(RestClientResponseException.class, () -> c.list("operadores", 0, model));
    }

    @Test
    @DisplayName("aba de pedidos: pendentes ou todos")
    void requests() {
        UserController c = new UserController(backend);
        when(backend.listAccountRequests("PENDING")).thenReturn(List.of(pending(1)));
        c.requests("PENDING", model);
        assertEquals("PENDING", model.get("filtroPedidos"));
        assertEquals(1, model.get("pedidosPendentes"));

        when(backend.listAccountRequests(null)).thenReturn(null);
        c.requests("TODOS", model);
        assertEquals("TODOS", model.get("filtroPedidos"));
        assertEquals(List.of(), model.get("pedidos"));
        assertEquals(1, model.get("pedidosPendentes"));

        when(backend.listAccountRequests("PENDING")).thenReturn(null);
        c.requests("PENDING", model);
        assertEquals(0, model.get("pedidosPendentes"));
    }

    @Test
    @DisplayName("novo operador: campos aparados; recusa reabre o modal com o que foi digitado")
    void createOperator() {
        UserController c = new UserController(backend);
        c.createOperator(" joao ", " João ", " j@x.com ", "loan_op", null, flash);
        verify(backend).createUser(argThat(u -> u.getUsername().equals("joao") && u.getRole_code().equals("loan_op")));
        assertTrue(((String) flash("info")).startsWith("Usuário joao cadastrado."));

        when(backend.createUser(any())).thenThrow(BackendErrors.status(403, "Somente o root pode criar usuários root."));
        c.createOperator("chefe", "Chefe", "c@x.com", "root", null, flash);
        assertEquals("Somente o root pode criar usuários root.", flash("erroModal"));
        ModalReopen reopen = (ModalReopen) flash("reabrirModal");
        assertEquals("modal-operador", reopen.modal());
        assertEquals("root", reopen.fields().get("roleCode"));
    }

    @Test
    @DisplayName("edição: operador e aluno (sem perfil) reabrem modais diferentes quando recusada")
    void updateUser() {
        UserController c = new UserController(backend);
        c.update("joao", "João", "j@x.com", "loan_op", null, flash);
        assertEquals("Usuário joao atualizado.", flash("info"));

        when(backend.updateUser(eq("joao"), any())).thenThrow(BackendErrors.status(409, "E-mail já usado"));
        c.update("joao", "João", "j@x.com", "loan_op", null, flash);
        assertEquals("modal-operador", ((ModalReopen) flash("reabrirModal")).modal());
        assertEquals("Editar usuário", ((ModalReopen) flash("reabrirModal")).title());

        c.update("joao", "João", "j@x.com", " ", null, flash);
        ModalReopen student = (ModalReopen) flash("reabrirModal");
        assertEquals("modal-aluno-editar", student.modal());
        assertEquals("Editar aluno", student.title());
        assertEquals("username", student.readonly());
    }

    @Test
    @DisplayName("ativar/desativar: sucesso e regra do backend (admin só reativa inatividade)")
    void toggle() {
        UserController c = new UserController(backend);
        c.deactivate("maria", null, flash);
        assertEquals("Conta de maria desativada.", flash("info"));
        c.activate("maria", null, flash);
        assertEquals("Conta de maria reativada.", flash("info"));

        doThrow(BackendErrors.status(403, "Esta conta precisa ser reativada pelo root.")).when(backend).activateUser("ana");
        c.activate("ana", null, flash);
        assertEquals("Esta conta precisa ser reativada pelo root.", flash("erro"));
        doThrow(BackendErrors.status(500)).when(backend).deactivateUser("ana");
        c.deactivate("ana", null, flash);
        assertEquals("Não foi possível desativar a conta.", flash("erro"));
    }

    @Test
    @DisplayName("cadastro de aluno: telefone opcional; RA vazio recusado pelo backend reabre o modal")
    void createStudent() {
        UserController c = new UserController(backend);
        assertEquals("redirect:/usuarios?aba=alunos", c.createStudent(" maria ", " Maria ", " m@x.com ", " RA1 ", " ", null, flash));
        verify(backend).createStudent(argThat(s -> s.ra().equals("RA1") && s.phone() == null && s.password() == null));

        c.createStudent("ana", "Ana", "a@x.com", "RA2", " 11999 ", null, flash);
        verify(backend).createStudent(argThat(s -> "11999".equals(s.phone())));

        doThrow(BackendErrors.status(409, "Informe o RA do aluno.")).when(backend).createStudent(argThat(s -> s.ra().isEmpty()));
        assertEquals("redirect:/usuarios", c.createStudent("x", "X", "x@x.com", " ", null, null, flash));
        assertEquals("Informe o RA do aluno.", flash("erroModal"));
        assertEquals("modal-aluno", ((ModalReopen) flash("reabrirModal")).modal());
    }

    @Test
    @DisplayName("aprovar/recusar pedido: sucesso e pedido já decidido")
    void decisions() {
        UserController c = new UserController(backend);
        assertEquals("redirect:/usuarios/pedidos", c.approve(1L, "ok", null, flash));
        assertTrue(((String) flash("info")).startsWith("Pedido aprovado."));
        c.reject(1L, null, null, flash);
        assertEquals("Pedido recusado.", flash("info"));

        doThrow(BackendErrors.status(409, "Este pedido já foi decidido (APPROVED).")).when(backend).approveAccountRequest(2L, null);
        c.approve(2L, null, null, flash);
        assertEquals("Este pedido já foi decidido (APPROVED).", flash("erro"));
        doThrow(BackendErrors.status(409, "x")).when(backend).rejectAccountRequest(2L, null);
        c.reject(2L, null, null, flash);
        assertEquals("x", flash("erro"));
    }

    // ------------------------------------------------------------------ dashboard e auditoria

    @Test
    @DisplayName("dashboard: período fora da lista vira 30; resposta vazia usa zeros")
    void dashboard() {
        ReportController c = new ReportController(backend);
        when(backend.settingsOrDefaults()).thenReturn(SettingsView.defaults());
        when(backend.dashboard(7)).thenReturn(DashboardData.empty(7));
        assertEquals("pages/dashboard", c.dashboard(7, model));
        assertEquals(7, model.get("period"));

        when(backend.dashboard(30)).thenReturn(null);
        c.dashboard(45, model);
        assertEquals(30, model.get("period"));
        assertNotNull(model.get("k"));
        assertNotNull(model.get("seg"));
        assertEquals(180, model.get("diasInatividade"));

        when(backend.dashboard(90)).thenReturn(new DashboardData(90, null, null, null, null, null, null, null, null));
        c.dashboard(90, model);
        assertNotNull(model.get("k"));
        assertTrue(((seabook.frontend.dto.dashboard.DashboardCharts.LineChart) model.get("line")).empty());
    }

    @Test
    @DisplayName("auditoria: limite entre 1 e 500, link de exportação com os filtros, erro mostrado na tela")
    void auditLogs() {
        ReportController c = new ReportController(backend);
        c.logs("root", "LOGIN_SUCCESS", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 16), 9999, model);
        AuditQuery q = (AuditQuery) model.get("filtro");
        assertEquals(500, q.limite());
        assertEquals("/relatorios/auditoria/exportar?usuario=root&acao=LOGIN_SUCCESS&de=2026-11-01&ate=2026-11-16&limite=500",
                model.get("exportUrl"));

        when(backend.listAudit(any())).thenThrow(BackendErrors.status(403, "Acesso negado"));
        c.logs(null, null, null, null, 0, model);
        assertEquals(1, ((AuditQuery) model.get("filtro")).limite());
        assertEquals(List.of(), model.get("logs"));
        assertEquals("Acesso negado", model.get("erro"));
    }

    @Test
    @DisplayName("exportação CSV: repassa o nome do arquivo (ou usa o padrão); erro vira texto com o status; 401 sobe")
    void export() {
        ReportController c = new ReportController(backend);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"auditoria-2026.csv\"");
        when(backend.exportAudit(any())).thenReturn(new ResponseEntity<>("id;acao".getBytes(), headers, 200));
        ResponseEntity<byte[]> r = c.export(null, null, null, null, 500);
        assertTrue(r.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("auditoria-2026.csv"));
        assertEquals("text/csv;charset=UTF-8", r.getHeaders().getContentType().toString());

        when(backend.exportAudit(any())).thenReturn(ResponseEntity.ok("x".getBytes()));
        assertTrue(c.export(null, null, null, null, 10).getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)
                .contains("auditoria.csv"));

        reset(backend);
        when(backend.exportAudit(any())).thenThrow(BackendErrors.status(403, "Sem permissão"));
        ResponseEntity<byte[]> denied = c.export(null, null, null, null, 10);
        assertEquals(403, denied.getStatusCode().value());
        assertEquals("Sem permissão", new String(denied.getBody(), StandardCharsets.UTF_8));

        reset(backend);
        when(backend.exportAudit(any())).thenThrow(BackendErrors.status(401));
        assertThrows(RestClientResponseException.class, () -> c.export(null, null, null, null, 10));
    }

    // ------------------------------------------------------------------ configurações

    private static SettingDetail detail(String key) {
        return new SettingDetail(key, key, "INTEGER", 1, 1, 1, 10, null, null);
    }

    @Test
    @DisplayName("configurações: agrupadas por seção na ordem certa; chave desconhecida vai para 'Outras'")
    void sections() {
        List<SettingsSection> sections = SettingsController.sections(List.of(
                detail("loan.period-days"), detail("sales.enabled"), detail("nova.chave"), detail("students.inactivity-days")));
        assertEquals(List.of("Vendas", "Empréstimos", "Alunos", "Outras"), sections.stream().map(SettingsSection::title).toList());
        assertTrue(SettingsController.sections(null).isEmpty());

        when(backend.settingDetails()).thenReturn(List.of(detail("waitlist.max-size")));
        assertEquals("pages/configuracoes", new SettingsController(backend).page(model));
        assertEquals(1, ((List<?>) model.get("secoes")).size());
    }

    @Test
    @DisplayName("salvar: caixas marcadas/desmarcadas, números, campos vazios ignorados; vendas atualizadas na sessão")
    void saveSettings() {
        SettingsController c = new SettingsController(backend);
        MockHttpSession session = new MockHttpSession();
        when(backend.updateSettings(any())).thenReturn(new SettingsView(false, 21, 5, true, 3, 90));

        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("_sales.enabled", "on");          // caixa desmarcada: só o campo oculto chega
        form.add("loan.block-overdue", "on");      // caixa marcada
        form.add("_loan.block-overdue", "on");
        form.add("loan.period-days", " 21 ");
        form.add("waitlist.max-size", "");

        assertEquals("redirect:/configuracoes", c.save(form, session, flash));
        verify(backend).updateSettings(argThat(r -> Boolean.FALSE.equals(r.salesEnabled()) && Boolean.TRUE.equals(r.blockOverdueLoans())
                && r.loanPeriodDays() == 21 && r.waitlistMaxSize() == null && r.maxOpenLoansPerStudent() == null));
        assertEquals(false, session.getAttribute(SessionModelAdvice.SALES_ENABLED));
        assertTrue(((String) flash("info")).startsWith("Configurações salvas."));
    }

    @Test
    @DisplayName("salvar: número inválido não chega ao backend; limite recusado pelo backend aparece")
    void saveSettingsErrors() {
        SettingsController c = new SettingsController(backend);
        MockHttpSession session = new MockHttpSession();
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("loan.period-days", "quinze");
        c.save(form, session, flash);
        assertEquals("Valor inválido em \"loan.period-days\": use só números.", flash("erro"));
        verify(backend, never()).updateSettings(any());

        when(backend.updateSettings(any())).thenThrow(BackendErrors.status(409, "Prazo do empréstimo (dias): informe um valor entre 1 e 90."));
        form.set("loan.period-days", "91");
        c.save(form, session, flash);
        assertEquals("Prazo do empréstimo (dias): informe um valor entre 1 e 90.", flash("erro"));
        assertNull(session.getAttribute(SessionModelAdvice.SALES_ENABLED));

        reset(backend);
        when(backend.updateSettings(any())).thenReturn(null);
        form.set("loan.period-days", "20");
        c.save(form, session, flash);
        assertNull(session.getAttribute(SessionModelAdvice.SALES_ENABLED));
    }
}
