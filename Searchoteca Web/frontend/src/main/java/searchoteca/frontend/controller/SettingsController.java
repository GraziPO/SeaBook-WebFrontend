package searchoteca.frontend.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import searchoteca.frontend.config.BackendMessages;
import searchoteca.frontend.config.SessionModelAdvice;
import searchoteca.frontend.dto.settings.SettingDetail;
import searchoteca.frontend.dto.settings.SettingsSection;
import searchoteca.frontend.dto.settings.SettingsUpdateRequest;
import searchoteca.frontend.dto.settings.SettingsView;
import searchoteca.frontend.service.BackendService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CONFIGURAÇÕES / ORGANIZAÇÃO (só root, settings:manage): GET /api/configuracoes/detalhes agrupado em seções.
 * Os campos do formulário têm o nome da chave do backend ("loan.period-days"...); aqui viram o
 * SettingsUpdateRequest. Valores de segurança (bloqueio de login, MFA, sessão) ficam só no application.yaml.
 */
@Controller
public class SettingsController {

    private static final String PAGE = "/configuracoes";

    /** Prefixo da chave -> título da seção, na ordem da tela. */
    private static final Map<String, String> SECTIONS = new LinkedHashMap<>();
    static {
        SECTIONS.put("sales.", "Vendas");
        SECTIONS.put("loan.", "Empréstimos");
        SECTIONS.put("waitlist.", "Fila de espera");
        SECTIONS.put("students.", "Alunos");
    }

    private final BackendService backendService;

    public SettingsController(BackendService backendService) {
        this.backendService = backendService;
    }

    @GetMapping(PAGE)
    public String page(Model model) {
        model.addAttribute("secoes", sections(backendService.settingDetails()));
        return "pages/configuracoes";
    }

    @PostMapping(PAGE)
    public String save(@RequestParam MultiValueMap<String, String> form,
                       HttpSession session,
                       RedirectAttributes flash) {
        try {
            SettingsUpdateRequest request = new SettingsUpdateRequest(
                    bool(form, "sales.enabled"),
                    integer(form, "loan.period-days"),
                    integer(form, "loan.max-open-per-student"),
                    bool(form, "loan.block-overdue"),
                    integer(form, "waitlist.max-size"),
                    integer(form, "students.inactivity-days"));
            SettingsView saved = backendService.updateSettings(request);
            if (saved != null) {
                // o menu de Vendas aparece/some na hora para quem salvou
                session.setAttribute(SessionModelAdvice.SALES_ENABLED, saved.salesEnabled());
            }
            flash.addFlashAttribute("info", "Configurações salvas. As mudanças já estão valendo.");
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("erro", ex.getMessage());
        } catch (RestClientException ex) {
            flash.addFlashAttribute("erro", BackendMessages.forForm(ex, "Não foi possível salvar as configurações."));
        }
        return "redirect:" + PAGE;
    }

    static List<SettingsSection> sections(List<SettingDetail> details) {
        List<SettingsSection> result = new ArrayList<>();
        if (details == null) return result;
        List<SettingDetail> used = new ArrayList<>();
        SECTIONS.forEach((prefix, title) -> {
            List<SettingDetail> items = details.stream().filter(d -> d.key() != null && d.key().startsWith(prefix)).toList();
            if (!items.isEmpty()) {
                result.add(new SettingsSection(title, items));
                used.addAll(items);
            }
        });
        // chave nova no backend que a tela ainda não conhece: aparece em "Outras"
        List<SettingDetail> others = details.stream().filter(d -> !used.contains(d)).toList();
        if (!others.isEmpty()) result.add(new SettingsSection("Outras", others));
        return result;
    }

    /** Liga/desliga: checkbox marcado envia "true"; desmarcado só envia o "_campo" escondido. */
    private static Boolean bool(MultiValueMap<String, String> form, String key) {
        if (form.containsKey(key)) return true;
        if (form.containsKey("_" + key)) return false;
        return null;
    }

    private static Integer integer(MultiValueMap<String, String> form, String key) {
        String value = form.getFirst(key);
        if (value == null || value.isBlank()) return null;
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor inválido em \"" + key + "\": use só números.");
        }
    }
}
