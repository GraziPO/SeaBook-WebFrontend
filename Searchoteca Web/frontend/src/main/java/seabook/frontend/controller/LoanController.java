package seabook.frontend.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import seabook.frontend.config.BackendMessages;
import seabook.frontend.dto.ModalReopen;
import seabook.frontend.dto.archive.CopyLookup;
import seabook.frontend.dto.circulation.LoanRequest;
import seabook.frontend.dto.settings.SettingsView;
import seabook.frontend.service.BackendService;
import seabook.frontend.service.CirculationService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * EMPRÉSTIMOS (operador): lista com filtro + dois modais com a lista de leitura:
 *  - Novo empréstimo: RA do aluno + exemplares (disponível, ou reservado para ele pela fila)
 *  - Devolução: só exemplares emprestados; o backend devolve um exemplar por chamada
 */
@Controller
public class LoanController {

    private static final String PAGE = "/emprestimos";
    private static final Set<String> FILTERS = Set.of("OPEN", "OVERDUE", "CLOSED", "CANCELLED");

    private final CirculationService circulationService;
    private final BackendService backendService;
    private final CopyCodes copyCodes;

    public LoanController(CirculationService circulationService, BackendService backendService, CopyCodes copyCodes) {
        this.circulationService = circulationService;
        this.backendService = backendService;
        this.copyCodes = copyCodes;
    }

    @GetMapping(PAGE)
    public String list(@RequestParam(defaultValue = "") String status,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        String filter = FILTERS.contains(status) ? status : "";
        SettingsView settings = backendService.settingsOrDefaults();
        model.addAttribute("status", filter);
        model.addAttribute("pagina", circulationService.listLoans(filter, page));
        model.addAttribute("paginaUrl", Pages.url(PAGE, "status", filter));
        model.addAttribute("prazoDias", settings.loanPeriodDays());
        model.addAttribute("limiteLivros", settings.maxOpenLoansPerStudent());
        model.addAttribute("bloqueiaAtraso", settings.blockOverdueLoans());
        return "pages/emprestimos";
    }

    @PostMapping(PAGE)
    public String create(@RequestParam String ra,
                         @RequestParam(defaultValue = "") String items,
                         RedirectAttributes flash) {
        try {
            List<CopyLookup> copies = copyCodes.resolve(items);
            circulationService.createLoan(new LoanRequest(ra.trim(),
                    copies.stream().map(CopyLookup::copyId).toList()));
            flash.addFlashAttribute("info", "Empréstimo registrado para o RA " + ra.trim() + " ("
                    + copies.size() + (copies.size() == 1 ? " livro)." : " livros)."));
        } catch (IllegalArgumentException ex) {
            reopenLoan(flash, ex.getMessage(), ra, items);
        } catch (RestClientException ex) {
            reopenLoan(flash, BackendMessages.forForm(ex, "Não foi possível registrar o empréstimo."), ra, items);
        }
        return "redirect:" + PAGE;
    }

    /**
     * Devolve exemplar por exemplar. Se um falhar no meio, os anteriores já foram devolvidos:
     * a mensagem diz quais, e o modal reabre só com os que faltam.
     */
    @PostMapping(PAGE + "/devolucao")
    public String giveBack(@RequestParam(defaultValue = "") String copyIds, RedirectAttributes flash) {
        List<CopyLookup> copies;
        try {
            copies = copyCodes.resolve(copyIds);
        } catch (IllegalArgumentException ex) {
            reopenReturn(flash, ex.getMessage(), FormParsing.codes(copyIds));
            return "redirect:" + PAGE;
        } catch (RestClientException ex) {
            reopenReturn(flash, BackendMessages.forForm(ex, "Não foi possível registrar a devolução."), FormParsing.codes(copyIds));
            return "redirect:" + PAGE;
        }

        List<String> done = new ArrayList<>();
        for (int i = 0; i < copies.size(); i++) {
            CopyLookup copy = copies.get(i);
            try {
                circulationService.returnCopy(copy.copyId());
                done.add(copy.code());
            } catch (RestClientException ex) {
                List<String> pending = copies.subList(i, copies.size()).stream().map(CopyLookup::code).toList();
                String message = BackendMessages.forForm(ex, "Não foi possível devolver o exemplar " + copy.code() + ".");
                if (!done.isEmpty()) {
                    message = done.size() + (done.size() == 1 ? " exemplar devolvido" : " exemplares devolvidos")
                            + " antes do erro. " + copy.code() + ": " + message;
                }
                reopenReturn(flash, message, pending);
                return "redirect:" + PAGE;
            }
        }
        flash.addFlashAttribute("info", done.size() + (done.size() == 1
                ? " exemplar devolvido." : " exemplares devolvidos."));
        return "redirect:" + PAGE;
    }

    @PostMapping(PAGE + "/{id}/cancelar")
    public String cancel(@PathVariable Long id,
                         @RequestParam(required = false) String returnTo,
                         RedirectAttributes flash) {
        try {
            circulationService.cancelLoan(id);
            flash.addFlashAttribute("info", "Empréstimo " + id + " cancelado. Os exemplares voltaram para o acervo.");
        } catch (RestClientException ex) {
            flash.addFlashAttribute("erro", BackendMessages.forForm(ex, "Não foi possível cancelar o empréstimo."));
        }
        return Redirects.back(returnTo, PAGE);
    }

    private static void reopenLoan(RedirectAttributes flash, String message, String ra, String items) {
        flash.addFlashAttribute("erroModal", message);
        flash.addFlashAttribute("reabrirModal", ModalReopen.of("modal-emprestimo", ModalReopen.fields(
                "ra", ra, "items", FormParsing.join(FormParsing.codes(items)))));
    }

    private static void reopenReturn(RedirectAttributes flash, String message, List<String> codes) {
        flash.addFlashAttribute("erroModal", message);
        flash.addFlashAttribute("reabrirModal", ModalReopen.of("modal-devolucao", ModalReopen.fields(
                "copyIds", FormParsing.join(codes))));
    }
}
