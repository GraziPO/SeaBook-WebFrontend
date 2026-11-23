package seabook.frontend.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import seabook.frontend.config.BackendMessages;
import seabook.frontend.dto.users.AccountRequestView;
import seabook.frontend.dto.users.MyAccount;
import seabook.frontend.service.BackendService;

import java.util.List;

/**
 * CONFIGURAÇÕES / MINHA CONTA (todos os perfis): os dados que o sistema guarda sobre a pessoa
 * (cadastro, perfil, situação, último aceite dos Termos) e, para o aluno, os pedidos de conta
 * e o pedido de desativação (que, aprovado pelo root, anonimiza a conta).
 * A troca de senha continua no modal global (AccountController, POST /conta/senha).
 */
@Controller
public class MyAccountController {

    static final String PAGE = "/conta";

    private final BackendService backendService;

    public MyAccountController(BackendService backendService) {
        this.backendService = backendService;
    }

    @GetMapping(PAGE)
    public String page(Model model) {
        MyAccount conta = backendService.myAccount();
        List<AccountRequestView> pedidos = conta != null && conta.student() ? myRequests() : List.of();

        model.addAttribute("conta", conta);
        model.addAttribute("pedidos", pedidos);
        model.addAttribute("pedidoPendente", pedidos.stream().filter(AccountRequestView::pending).findFirst().orElse(null));
        return "pages/minha-conta";
    }

    @PostMapping(PAGE + "/desativacao")
    public String requestDeactivation(@RequestParam(required = false) String reason,
                                      @RequestParam(required = false) String confirm,
                                      RedirectAttributes flash) {
        if (!"true".equals(confirm)) {
            flash.addFlashAttribute("erro", "Marque a confirmação para enviar o pedido de desativação.");
            return "redirect:" + PAGE;
        }
        try {
            backendService.requestDeactivation(reason);
            flash.addFlashAttribute("info", "Pedido de desativação enviado. Você pode acompanhar a análise nesta tela.");
        } catch (RestClientException ex) {
            // ex.: livro ainda emprestado, pedido já em análise
            flash.addFlashAttribute("erro", BackendMessages.forForm(ex, "Não foi possível enviar o pedido de desativação."));
        }
        return "redirect:" + PAGE;
    }

    @PostMapping(PAGE + "/desativacao/cancelar")
    public String cancelDeactivation(RedirectAttributes flash) {
        try {
            backendService.cancelDeactivation();
            flash.addFlashAttribute("info", "Pedido de desativação cancelado. Sua conta continua ativa.");
        } catch (RestClientException ex) {
            flash.addFlashAttribute("erro", BackendMessages.forForm(ex, "Não foi possível cancelar o pedido."));
        }
        return "redirect:" + PAGE;
    }

    /** Sem os pedidos a tela ainda é útil: um erro aqui só esconde o histórico (sessão expirada continua indo ao login). */
    private List<AccountRequestView> myRequests() {
        try {
            return backendService.myAccountRequests();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 401) throw ex;
            return List.of();
        }
    }
}
