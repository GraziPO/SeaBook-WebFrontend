package seabook.frontend.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import seabook.frontend.config.BackendMessages;
import seabook.frontend.dto.ModalReopen;
import seabook.frontend.dto.users.AccountRequestView;
import seabook.frontend.dto.users.StudentRequest;
import seabook.frontend.model.User;
import seabook.frontend.service.BackendService;

import java.util.List;

/**
 * CONFIGURAÇÕES / USUÁRIOS (root e admin_users): abas Operadores, Alunos e Pedidos de conta.
 * A senha inicial é automática (o próprio usuário, para operador; o RA, para aluno) e é trocada no 1º acesso.
 * Nos pedidos, o admin_users só consegue decidir reativação de conta desativada por inatividade;
 * desativação e anonimização são do root (a regra fica no backend, que responde 403 com a explicação).
 */
@Controller
public class UserController {

    private static final String PAGE = "/usuarios";
    private static final String REQUESTS = PAGE + "/pedidos";

    private final BackendService backendService;

    public UserController(BackendService backendService) {
        this.backendService = backendService;
    }

    @GetMapping(PAGE)
    public String list(@RequestParam(defaultValue = "operadores") String aba,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        String tab = "alunos".equals(aba) ? "alunos" : "operadores";
        model.addAttribute("aba", tab);
        model.addAttribute("pagina", backendService.listUsersPage(tab, page));
        model.addAttribute("paginaUrl", Pages.url(PAGE, "aba", "alunos".equals(tab) ? "alunos" : null));
        model.addAttribute("pedidosPendentes", pendingCount());
        return "pages/usuarios";
    }

    @GetMapping(REQUESTS)
    public String requests(@RequestParam(defaultValue = "PENDING") String status, Model model) {
        String filter = "TODOS".equals(status) ? null : "PENDING";
        List<AccountRequestView> pedidos = backendService.listAccountRequests(filter);
        model.addAttribute("aba", "pedidos");
        model.addAttribute("filtroPedidos", filter == null ? "TODOS" : "PENDING");
        model.addAttribute("pedidos", pedidos == null ? List.of() : pedidos);
        model.addAttribute("pedidosPendentes", filter == null ? pendingCount() : (pedidos == null ? 0 : pedidos.size()));
        return "pages/usuarios";
    }

    // ---------------- operadores ----------------

    @PostMapping(PAGE)
    public String createOperator(@RequestParam String username,
                                 @RequestParam String completeName,
                                 @RequestParam String email,
                                 @RequestParam String roleCode,
                                 @RequestParam(required = false) String returnTo,
                                 RedirectAttributes flash) {
        User user = user(username, completeName, email, roleCode);
        try {
            backendService.createUser(user);
            flash.addFlashAttribute("info", "Usuário " + user.getUsername()
                    + " cadastrado. A senha inicial é o próprio usuário, e a troca é obrigatória no 1º acesso.");
        } catch (RestClientException ex) {
            flash.addFlashAttribute("erroModal", BackendMessages.forForm(ex, "Não foi possível cadastrar o usuário."));
            flash.addFlashAttribute("reabrirModal", reopenOperator(user).withAction(PAGE, "Novo operador", "Cadastrar"));
        }
        return Redirects.back(returnTo, PAGE);
    }

    /** Edição de operador (com perfil) e de aluno (sem perfil: o backend mantém o atual). */
    @PostMapping(PAGE + "/{username}/editar")
    public String update(@PathVariable String username,
                         @RequestParam String completeName,
                         @RequestParam String email,
                         @RequestParam(required = false) String roleCode,
                         @RequestParam(required = false) String returnTo,
                         RedirectAttributes flash) {
        User user = user(username, completeName, email, roleCode);
        try {
            backendService.updateUser(username, user);
            flash.addFlashAttribute("info", "Usuário " + username + " atualizado.");
        } catch (RestClientException ex) {
            flash.addFlashAttribute("erroModal", BackendMessages.forForm(ex, "Não foi possível salvar o usuário."));
            boolean student = roleCode == null || roleCode.isBlank();
            ModalReopen reopen = student
                    ? ModalReopen.of("modal-aluno-editar", ModalReopen.fields(
                            "username", username, "completeName", completeName, "email", email))
                    : reopenOperator(user);
            flash.addFlashAttribute("reabrirModal", reopen
                    .withAction(PAGE + "/" + username + "/editar", student ? "Editar aluno" : "Editar usuário", "Salvar")
                    .withReadonly("username"));
        }
        return Redirects.back(returnTo, PAGE);
    }

    @PostMapping(PAGE + "/{username}/desativar")
    public String deactivate(@PathVariable String username,
                             @RequestParam(required = false) String returnTo,
                             RedirectAttributes flash) {
        try {
            backendService.deactivateUser(username);
            flash.addFlashAttribute("info", "Conta de " + username + " desativada.");
        } catch (RestClientException ex) {
            flash.addFlashAttribute("erro", BackendMessages.forForm(ex, "Não foi possível desativar a conta."));
        }
        return Redirects.back(returnTo, PAGE);
    }

    @PostMapping(PAGE + "/{username}/ativar")
    public String activate(@PathVariable String username,
                           @RequestParam(required = false) String returnTo,
                           RedirectAttributes flash) {
        try {
            backendService.activateUser(username);
            flash.addFlashAttribute("info", "Conta de " + username + " reativada.");
        } catch (RestClientException ex) {
            flash.addFlashAttribute("erro", BackendMessages.forForm(ex, "Não foi possível reativar a conta."));
        }
        return Redirects.back(returnTo, PAGE);
    }

    // ---------------- alunos ----------------

    @PostMapping("/alunos")
    public String createStudent(@RequestParam String username,
                                @RequestParam String completeName,
                                @RequestParam String email,
                                @RequestParam String ra,
                                @RequestParam(required = false) String phone,
                                @RequestParam(required = false) String returnTo,
                                RedirectAttributes flash) {
        try {
            // senha null: o backend usa o RA como senha inicial e exige a troca no 1º acesso
            backendService.createStudent(new StudentRequest(username.trim(), completeName.trim(), email.trim(),
                    null, ra.trim(), phone == null || phone.isBlank() ? null : phone.trim()));
            flash.addFlashAttribute("info", "Aluno " + completeName.trim() + " cadastrado. A senha inicial é o RA, e a troca é obrigatória no 1º acesso.");
            return Redirects.back(returnTo, PAGE + "?aba=alunos");
        } catch (RestClientException ex) {
            flash.addFlashAttribute("erroModal", BackendMessages.forForm(ex, "Não foi possível cadastrar o aluno."));
            flash.addFlashAttribute("reabrirModal", ModalReopen.of("modal-aluno", ModalReopen.fields(
                    "username", username, "completeName", completeName, "email", email, "ra", ra, "phone", phone)));
            return Redirects.back(returnTo, PAGE);
        }
    }

    // ---------------- pedidos de conta ----------------

    @PostMapping(REQUESTS + "/{id}/aprovar")
    public String approve(@PathVariable Long id,
                          @RequestParam(required = false) String note,
                          @RequestParam(required = false) String returnTo,
                          RedirectAttributes flash) {
        try {
            backendService.approveAccountRequest(id, note);
            flash.addFlashAttribute("info", "Pedido aprovado. O aluno recebe o aviso por e-mail.");
        } catch (RestClientException ex) {
            flash.addFlashAttribute("erro", BackendMessages.forForm(ex, "Não foi possível aprovar o pedido."));
        }
        return Redirects.back(returnTo, REQUESTS);
    }

    @PostMapping(REQUESTS + "/{id}/recusar")
    public String reject(@PathVariable Long id,
                         @RequestParam(required = false) String note,
                         @RequestParam(required = false) String returnTo,
                         RedirectAttributes flash) {
        try {
            backendService.rejectAccountRequest(id, note);
            flash.addFlashAttribute("info", "Pedido recusado.");
        } catch (RestClientException ex) {
            flash.addFlashAttribute("erro", BackendMessages.forForm(ex, "Não foi possível recusar o pedido."));
        }
        return Redirects.back(returnTo, REQUESTS);
    }

    /** Número da aba "Pedidos de conta". Se a leitura falhar, a aba só fica sem o número. */
    private int pendingCount() {
        try {
            List<AccountRequestView> pending = backendService.listAccountRequests("PENDING");
            return pending == null ? 0 : pending.size();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 401) throw ex;
            return 0;
        } catch (RestClientException ex) {
            return 0;
        }
    }

    private static User user(String username, String completeName, String email, String roleCode) {
        User user = new User();
        user.setUsername(username == null ? null : username.trim());
        user.setCompleteName(completeName == null ? null : completeName.trim());
        user.setEmail(email == null ? null : email.trim());
        user.setRole_code(roleCode == null || roleCode.isBlank() ? null : roleCode);
        return user;
    }

    private static ModalReopen reopenOperator(User u) {
        return ModalReopen.of("modal-operador", ModalReopen.fields(
                "username", u.getUsername(),
                "completeName", u.getCompleteName(),
                "email", u.getEmail(),
                "roleCode", u.getRole_code()));
    }
}
