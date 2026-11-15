package searchoteca.frontend.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import searchoteca.frontend.config.BackendMessages;
import searchoteca.frontend.service.BackendService;

/**
 * Alterar senha: agora é um modal do layout (botão no topo, em qualquer tela).
 * Depois da troca, o backend invalida o token antigo (RF18): a sessão é encerrada e a pessoa entra de novo.
 */
@Controller
public class AccountController {

    private final BackendService backendService;

    public AccountController(BackendService backendService) {
        this.backendService = backendService;
    }

    @PostMapping("/conta/senha")
    public String changePassword(@RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 @RequestParam(required = false) String returnTo,
                                 HttpServletRequest request,
                                 RedirectAttributes flash) {
        if (!newPassword.equals(confirmPassword)) {
            return failed(flash, "As duas senhas novas não são iguais.", returnTo);
        }
        try {
            backendService.changeOwnPassword(currentPassword, newPassword);
        } catch (RestClientException ex) {
            // senha atual errada volta 409 (e não 401): fica na tela, com o modal aberto
            return failed(flash, BackendMessages.forForm(ex, "Não foi possível alterar a senha."), returnTo);
        }

        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        flash.addFlashAttribute("info", "Senha alterada. Entre novamente com a senha nova.");
        return "redirect:/login";
    }

    private static String failed(RedirectAttributes flash, String message, String returnTo) {
        flash.addFlashAttribute("erroSenha", message);
        flash.addFlashAttribute("abrirModal", "modal-senha");
        return Redirects.back(returnTo, "/");
    }
}
