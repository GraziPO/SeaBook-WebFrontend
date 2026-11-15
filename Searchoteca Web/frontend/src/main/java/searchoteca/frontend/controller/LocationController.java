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
import searchoteca.frontend.dto.ModalReopen;
import searchoteca.frontend.model.Location;
import searchoteca.frontend.service.BackendService;

/** ACERVO / LOCALIZAÇÕES: mesmo modelo de Departamentos (lista + modal de incluir/editar). */
@Controller
public class LocationController {

    private static final String LIST = "/acervo/localizacoes";

    private final BackendService backendService;

    public LocationController(BackendService backendService) {
        this.backendService = backendService;
    }

    @GetMapping(LIST)
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("pagina", backendService.listLocalPage(page, BackendService.PAGE_SIZE));
        model.addAttribute("departamentos", backendService.listDepart());
        return "pages/localizacoes";
    }

    @PostMapping(LIST)
    public String create(@ModelAttribute Location local,
                         @RequestParam(required = false) String returnTo,
                         RedirectAttributes flash) {
        try {
            backendService.createLocal(local);
            flash.addFlashAttribute("info", "Localização " + local.getLocalCode() + " incluída.");
        } catch (RestClientException ex) {
            flash.addFlashAttribute("erroModal", BackendMessages.forForm(ex, "Não foi possível incluir a localização."));
            flash.addFlashAttribute("reabrirModal", reopen(local).withAction(LIST, "Incluir localização", "Incluir"));
        }
        return Redirects.back(returnTo, LIST);
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
            flash.addFlashAttribute("reabrirModal", reopen(local)
                    .withAction(LIST + "/" + code + "/editar", "Editar localização", "Salvar")
                    .withReadonly("localCode"));
        }
        return Redirects.back(returnTo, LIST);
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
        return Redirects.back(returnTo, LIST);
    }

    private static ModalReopen reopen(Location l) {
        return ModalReopen.of("modal-localizacao", ModalReopen.fields(
                "localCode", l.getLocalCode(),
                "localName", l.getLocalName(),
                "departCode", l.getDepartCode(),
                "localDesc", l.getLocalDesc()));
    }
}
