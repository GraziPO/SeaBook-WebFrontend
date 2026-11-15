package seabook.frontend.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import seabook.frontend.service.BackendService;

/**
 * O Thymeleaf 3.1 (Spring Boot 3+) não expõe mais ${session} nos templates.
 * Este advice coloca no model de toda página quem está logado e o que o perfil pode ver.
 */
@ControllerAdvice
public class SessionModelAdvice {

    /** Chave da sessão onde fica guardado se o módulo de vendas está ligado (lido uma vez por sessão). */
    public static final String SALES_ENABLED = "salesEnabled";

    private final BackendService backendService;

    public SessionModelAdvice(BackendService backendService) {
        this.backendService = backendService;
    }

    @ModelAttribute
    public void addCurrentUser(HttpServletRequest request, Model model) {
        HttpSession session = request.getSession(false);
        String username = session == null ? null : (String) session.getAttribute("username");
        String role = session == null ? null : (String) session.getAttribute("role");

        model.addAttribute("currentUser", username);
        model.addAttribute("currentRoleLabel", RoleAccess.label(role));
        model.addAttribute("nav", RoleAccess.of(role, salesEnabled(session)));
        model.addAttribute("roleLabels", RoleAccess.LABELS);

        // endereço atual (com ?page=...): os formulários dos modais voltam para ele depois de salvar
        String query = request.getQueryString();
        model.addAttribute("currentUri", request.getRequestURI() + (query == null ? "" : "?" + query));
    }

    /**
     * Vendas ligadas? Consulta o backend só na primeira página da sessão (e de novo quando o root
     * salva as configurações). Sem login, não consulta: o visitante não vê o menu de vendas mesmo.
     */
    private boolean salesEnabled(HttpSession session) {
        if (session == null || session.getAttribute("jwt") == null) return true;
        Object cached = session.getAttribute(SALES_ENABLED);
        if (cached instanceof Boolean b) return b;
        boolean enabled = backendService.salesEnabled();
        session.setAttribute(SALES_ENABLED, enabled);
        return enabled;
    }
}
