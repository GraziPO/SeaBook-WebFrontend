package searchoteca.frontend.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import searchoteca.frontend.config.BackendMessages;
import searchoteca.frontend.model.Department;
import searchoteca.frontend.service.BackendService;

/**
 * ACERVO / DEPARTAMENTOS: uma tela só (pages/departamentos.html).
 * Incluir e editar são o mesmo modal; se o backend recusar, a tela volta com o modal aberto,
 * o que foi digitado e a mensagem do erro dentro dele.
 */
@Controller
public class DepartmentController {

    private static final String LIST = "/acervo/departamentos";

    private final BackendService backendService;

    public DepartmentController(BackendService backendService) {
        this.backendService = backendService;
    }

    @GetMapping(LIST)
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("pagina", backendService.listDepartPage(page, 20));
        return "pages/departamentos";
    }

    @PostMapping(LIST)
    public String create(@ModelAttribute Department department,
                         @RequestParam(required = false) String returnTo,
                         RedirectAttributes flash) {
        try {
            backendService.createDepart(department);
            flash.addFlashAttribute("info", "Departamento " + department.getDepartCode() + " incluído.");
        } catch (RestClientException ex) {
            flash.addFlashAttribute("erroModal", BackendMessages.forForm(ex, "Não foi possível incluir o departamento."));
            flash.addFlashAttribute("reabrir", new Reopen(false, LIST, "Incluir departamento", "Incluir", department));
        }
        return Redirects.back(returnTo, LIST);
    }

    @PostMapping(LIST + "/{code}/editar")
    public String update(@PathVariable String code,
                         @ModelAttribute Department department,
                         @RequestParam(required = false) String returnTo,
                         RedirectAttributes flash) {
        department.setDepartCode(code);   // o código não muda na edição
        try {
            backendService.updateDepart(code, department);
            flash.addFlashAttribute("info", "Departamento " + code + " atualizado.");
        } catch (RestClientException ex) {
            flash.addFlashAttribute("erroModal", BackendMessages.forForm(ex, "Não foi possível salvar o departamento."));
            flash.addFlashAttribute("reabrir",
                    new Reopen(true, LIST + "/" + code + "/editar", "Editar departamento", "Salvar", department));
        }
        return Redirects.back(returnTo, LIST);
    }

    @PostMapping(LIST + "/{code}/excluir")
    public String delete(@PathVariable String code,
                         @RequestParam(required = false) String returnTo,
                         RedirectAttributes flash) {
        try {
            backendService.deleteDepart(code);
            flash.addFlashAttribute("info", "Departamento " + code + " excluído.");
        } catch (RestClientException ex) {
            flash.addFlashAttribute("erro", BackendMessages.forForm(ex, "Não foi possível excluir o departamento."));
        }
        return Redirects.back(returnTo, LIST);
    }

    /** O que a tela precisa para reabrir o modal depois de um erro. */
    public record Reopen(boolean editing, String action, String title, String submitLabel, Department department) {}
}
