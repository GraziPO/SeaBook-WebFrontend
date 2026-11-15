package seabook.frontend.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import seabook.frontend.config.BackendMessages;
import seabook.frontend.dto.ModalReopen;
import seabook.frontend.dto.circulation.ExitRequest;
import seabook.frontend.dto.circulation.ExitTypes;
import seabook.frontend.dto.circulation.ExitTypes.ExitOption;
import seabook.frontend.dto.circulation.ReceiptSnapshot;
import seabook.frontend.service.CirculationService;

import java.util.List;

/**
 * SAÍDAS: doação e baixa (a venda tem aba própria, no SalesController).
 * Cada cartão abre o mesmo modal com a lista de leitura; o comprovante abre em modal, pronto para imprimir.
 * SAÍDAS / COMPROVANTES: consulta de um comprovante pelo código.
 */
@Controller
public class ExitController {

    private static final String PAGE = "/saidas";
    private static final String RECEIPTS = PAGE + "/comprovantes";

    private final CirculationService circulationService;
    private final CopyCodes copyCodes;

    public ExitController(CirculationService circulationService, CopyCodes copyCodes) {
        this.circulationService = circulationService;
        this.copyCodes = copyCodes;
    }

    @GetMapping(PAGE)
    public String page(Model model) {
        model.addAttribute("tipos", options());
        return "pages/saidas";
    }

    @PostMapping(PAGE)
    public String register(@RequestParam String type,
                           @RequestParam(defaultValue = "") String copyIds,
                           @RequestParam(required = false) String notes,
                           RedirectAttributes flash) {
        if ("SALE".equals(type)) return "redirect:/vendas";   // venda tem tela própria
        ExitOption option = ExitTypes.option(type);
        try {
            ReceiptSnapshot receipt = circulationService.registerExit(
                    new ExitRequest(option.key(), copyCodes.ids(copyIds), notes == null || notes.isBlank() ? null : notes.trim()));
            flash.addFlashAttribute("comprovante", receipt);
            flash.addFlashAttribute("info", option.label() + " registrada. Comprovante " + receipt.code() + ".");
        } catch (IllegalArgumentException ex) {
            reopen(flash, ex.getMessage(), option, copyIds, notes);
        } catch (RestClientException ex) {
            reopen(flash, BackendMessages.forForm(ex, "Não foi possível registrar a saída."), option, copyIds, notes);
        }
        return "redirect:" + PAGE;
    }

    @GetMapping(RECEIPTS)
    public String receipts(@RequestParam(required = false) String codigo, Model model) {
        model.addAttribute("codigo", codigo);
        if (codigo != null && !codigo.isBlank()) {
            try {
                model.addAttribute("comprovante", circulationService.findReceipt(codigo));
            } catch (RestClientException ex) {
                model.addAttribute("erro", BackendMessages.forForm(ex, "Comprovante " + codigo.trim() + " não encontrado."));
            }
        }
        return "pages/comprovantes";
    }

    /** Endereços antigos continuam funcionando. */
    @GetMapping({"/saidas/nova", "/comprovantes"})
    public String oldAddresses(@RequestParam(required = false) String codigo) {
        return codigo == null || codigo.isBlank()
                ? "redirect:" + PAGE
                : "redirect:" + Pages.url(RECEIPTS, "codigo", codigo);
    }

    /** Doação e baixa. */
    private static List<ExitOption> options() {
        return ExitTypes.OPTIONS.stream().filter(o -> !"SALE".equals(o.key())).toList();
    }

    private static void reopen(RedirectAttributes flash, String message, ExitOption option, String copyIds, String notes) {
        flash.addFlashAttribute("erroModal", message);
        String label = option.label().toLowerCase();
        flash.addFlashAttribute("reabrirModal", ModalReopen.of("modal-saida", ModalReopen.fields(
                        "type", option.key(),
                        "copyIds", FormParsing.join(FormParsing.codes(copyIds)),
                        "notes", notes))
                .withAction(null, "Registrar " + label, "Confirmar saída")
                .withRequired(option.notesRequired() ? "notes" : "", option.hint()));
    }
}
