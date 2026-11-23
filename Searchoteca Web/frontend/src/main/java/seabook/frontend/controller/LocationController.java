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
import seabook.frontend.dto.archive.LocationBookStock;
import seabook.frontend.model.Department;
import seabook.frontend.model.Location;
import seabook.frontend.service.BackendService;

import java.util.List;

/**
 * Localizações: a lista fica na tela de Departamentos (uma página por departamento).
 * Aqui ficam incluir, editar, excluir e o corpo do modal "Detalhes" (livros e exemplares disponíveis).
 * Depois de salvar, volta para a página do departamento de onde veio (returnTo).
 */
@Controller
public class LocationController {

    private static final String LIST = "/acervo/localizacoes";
    private static final String DEPARTMENTS = "/acervo/departamentos";

    private final BackendService backendService;

    public LocationController(BackendService backendService) {
        this.backendService = backendService;
    }

    /** Endereço antigo: a lista agora é a tela de Departamentos. */
    @GetMapping(LIST)
    public String list() {
        return "redirect:" + DEPARTMENTS;
    }

    /** Endereço antigo do detalhe: vai para a página do departamento da localização. */
    @GetMapping(LIST + "/{code}")
    public String detailPage(@PathVariable String code) {
        try {
            Location local = backendService.publicLocal(code);
            if (local != null && local.getDepartCode() != null) {
                return "redirect:" + Pages.url(DEPARTMENTS, "d", local.getDepartCode());
            }
        } catch (RestClientException ex) {
            // localização inexistente: cai na tela de departamentos
        }
        return "redirect:" + DEPARTMENTS;
    }

    /** Corpo do modal "Detalhes" (carregado pelo js/app.js): livro, ISBN e exemplares disponíveis. */
    @GetMapping(LIST + "/{code}/detalhes")
    public String details(@PathVariable String code, Model model) {
        // leitura pela rota pública: o modal abre também para visitante e aluno (só consulta)
        Location local = backendService.publicLocal(code);
        Department depart = null;
        try {
            if (local != null && local.getDepartCode() != null) depart = backendService.publicDepart(local.getDepartCode());
        } catch (RestClientException ex) {
            // sem o nome do departamento, o modal mostra só o código
        }
        List<LocationBookStock> livros = backendService.publicLocalBooks(code);
        model.addAttribute("local", local);
        model.addAttribute("departamento", depart);
        model.addAttribute("livros", livros == null ? List.of() : livros);
        return "pages/localizacao-parcial";
    }

    @PostMapping(LIST)
    public String create(@ModelAttribute Location local,
                         @RequestParam(required = false) String returnTo,
                         RedirectAttributes flash) {
        String back = Pages.url(DEPARTMENTS, "d", local.getDepartCode());
        try {
            backendService.createLocal(local);
            flash.addFlashAttribute("info", "Localização " + local.getLocalCode() + " adicionada.");
        } catch (RestClientException ex) {
            flash.addFlashAttribute("erroModal", BackendMessages.forForm(ex, "Não foi possível adicionar a localização."));
            flash.addFlashAttribute("reabrirModal", ModalReopen.of("modal-localizacao-nova", ModalReopen.fields(
                            "localCode", local.getLocalCode(),
                            "localName", local.getLocalName(),
                            "departCode", local.getDepartCode(),
                            "localDesc", local.getLocalDesc()))
                    .withMessage(departLabel(local.getDepartCode()), null));
        }
        return Redirects.back(returnTo, back);
    }

    @PostMapping(LIST + "/{code}/editar")
    public String update(@PathVariable String code,
                         @ModelAttribute Location local,
                         @RequestParam(required = false) String returnTo,
                         RedirectAttributes flash) {
        local.setLocalCode(code);   // o código não muda na edição
        try {
            backendService.updateLocal(code, local);
            flash.addFlashAttribute("info", "Localização " + code + " atualizada.");
        } catch (RestClientException ex) {
            flash.addFlashAttribute("erroModal", BackendMessages.forForm(ex, "Não foi possível salvar a localização."));
            flash.addFlashAttribute("reabrirModal", ModalReopen.of("modal-localizacao", ModalReopen.fields(
                            "localCode", local.getLocalCode(),
                            "localName", local.getLocalName(),
                            "departCode", local.getDepartCode(),
                            "localDesc", local.getLocalDesc()))
                    .withAction(LIST + "/" + code + "/editar", "Editar localização", null)
                    .withReadonly("localCode"));
        }
        return Redirects.back(returnTo, DEPARTMENTS);
    }

    @PostMapping(LIST + "/{code}/excluir")
    public String delete(@PathVariable String code,
                         @RequestParam(required = false) String returnTo,
                         RedirectAttributes flash) {
        try {
            backendService.deleteLocal(code);
            flash.addFlashAttribute("info", "Localização " + code + " excluída.");
        } catch (RestClientException ex) {
            flash.addFlashAttribute("erro", BackendMessages.forForm(ex, "Não foi possível excluir a localização."));
        }
        return Redirects.back(returnTo, DEPARTMENTS);
    }

    /** "D2 — Literatura" para o modal reaberto; se não der para buscar, só o código. */
    private String departLabel(String departCode) {
        if (departCode == null) return null;
        try {
            Department d = backendService.getDepart(departCode);
            if (d != null && d.getDepartName() != null) return departCode + " — " + d.getDepartName();
        } catch (RestClientException ex) {
            // só o código
        }
        return departCode;
    }
}
