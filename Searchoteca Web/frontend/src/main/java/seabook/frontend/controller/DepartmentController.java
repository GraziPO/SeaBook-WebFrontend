package seabook.frontend.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import seabook.frontend.config.BackendMessages;
import seabook.frontend.dto.ModalReopen;
import seabook.frontend.model.Department;
import seabook.frontend.model.Location;
import seabook.frontend.service.BackendService;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * ACERVO / DEPARTAMENTOS: tela única de departamentos e localizações (pages/departamentos.html).
 * Uma página por departamento, na ordem do código (?d=CODIGO; sem ?d, o primeiro).
 * A página mostra as localizações dele; "Anterior" e "Próximo" passam de um departamento para o outro.
 * Cadastrar e editar departamento usam o mesmo modal; se o backend recusar, o modal reabre com o erro.
 */
@Controller
public class DepartmentController {

    private static final String LIST = "/acervo/departamentos";

    private final BackendService backendService;

    public DepartmentController(BackendService backendService) {
        this.backendService = backendService;
    }

    @GetMapping(LIST)
    public String list(@RequestParam(name = "d", required = false) String code, Model model) {
        List<Department> departs = backendService.listDepart().stream()
                .sorted(Comparator.comparing(Department::getDepartCode, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
        model.addAttribute("departamentos", departs);
        if (departs.isEmpty()) {
            model.addAttribute("departamento", null);
            model.addAttribute("locais", List.of());
            return "pages/departamentos";
        }

        int index = 0;
        if (code != null && !code.isBlank()) {
            for (int i = 0; i < departs.size(); i++) {
                if (code.trim().equalsIgnoreCase(departs.get(i).getDepartCode())) {
                    index = i;
                    break;
                }
            }
        }
        Department current = departs.get(index);
        List<Location> locais = backendService.listLocal().stream()
                .filter(l -> Objects.equals(l.getDepartCode(), current.getDepartCode()))
                .sorted(Comparator.comparing(Location::getLocalCode, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();

        model.addAttribute("departamento", current);
        model.addAttribute("locais", locais);
        model.addAttribute("anterior", index > 0 ? departs.get(index - 1).getDepartCode() : null);
        model.addAttribute("proximo", index < departs.size() - 1 ? departs.get(index + 1).getDepartCode() : null);
        return "pages/departamentos";
    }

    /** Endereço antigo do detalhe: vai para a página do departamento. */
    @GetMapping(LIST + "/{code}")
    public String detail(@PathVariable String code) {
        return "redirect:" + Pages.url(LIST, "d", code);
    }

    @PostMapping(LIST)
    public String create(@ModelAttribute Department department,
                         @RequestParam(required = false) String returnTo,
                         RedirectAttributes flash) {
        try {
            backendService.createDepart(department);
            flash.addFlashAttribute("info", "Departamento " + department.getDepartCode() + " cadastrado.");
            // abre direto a página do departamento novo
            return "redirect:" + Pages.url(LIST, "d", department.getDepartCode());
        } catch (RestClientException ex) {
            flash.addFlashAttribute("erroModal", BackendMessages.forForm(ex, "Não foi possível cadastrar o departamento."));
            flash.addFlashAttribute("reabrirModal", reopen(department).withAction(LIST, "Cadastrar departamento", "Cadastrar"));
            return Redirects.back(returnTo, LIST);
        }
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
            flash.addFlashAttribute("reabrirModal", reopen(department)
                    .withAction(LIST + "/" + code + "/editar", "Editar departamento", "Salvar")
                    .withReadonly("departCode"));
        }
        return Redirects.back(returnTo, Pages.url(LIST, "d", code));
    }

    @PostMapping(LIST + "/{code}/excluir")
    public String delete(@PathVariable String code,
                         @RequestParam(required = false) String returnTo,
                         RedirectAttributes flash) {
        try {
            backendService.deleteDepart(code);
            flash.addFlashAttribute("info", "Departamento " + code + " excluído.");
            return "redirect:" + LIST;   // a página dele não existe mais: volta para o primeiro
        } catch (RestClientException ex) {
            flash.addFlashAttribute("erro", BackendMessages.forForm(ex, "Não foi possível excluir o departamento."));
            return Redirects.back(returnTo, Pages.url(LIST, "d", code));
        }
    }

    private static ModalReopen reopen(Department d) {
        return ModalReopen.of("modal-departamento", ModalReopen.fields(
                "departCode", d.getDepartCode(),
                "departName", d.getDepartName(),
                "departDesc", d.getDepartDesc()));
    }
}
