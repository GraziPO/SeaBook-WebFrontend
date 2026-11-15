package searchoteca.frontend.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import searchoteca.frontend.config.BackendMessages;
import searchoteca.frontend.dto.circulation.ExitRequest;
import searchoteca.frontend.dto.circulation.ReceiptSnapshot;
import searchoteca.frontend.service.BackendService;
import searchoteca.frontend.service.CirculationService;

/**
 * VENDAS (aba própria, só com o módulo ligado): um "caixa" com a lista de leitura na própria tela.
 * O preço vem do cadastro do livro; o cupom sai no formato da impressora térmica (components/receipt :: saleReceipt).
 */
@Controller
public class SalesController {

    private static final String PAGE = "/vendas";

    private final CirculationService circulationService;
    private final BackendService backendService;
    private final CopyCodes copyCodes;

    public SalesController(CirculationService circulationService, BackendService backendService, CopyCodes copyCodes) {
        this.circulationService = circulationService;
        this.backendService = backendService;
        this.copyCodes = copyCodes;
    }

    @GetMapping(PAGE)
    public String page(Model model, RedirectAttributes flash) {
        if (!backendService.salesEnabled()) {
            flash.addFlashAttribute("erro", "O módulo de vendas está desligado. O root pode ligá-lo em Configurações > Organização.");
            return "redirect:/saidas";
        }
        return "pages/vendas";
    }

    @PostMapping(PAGE)
    public String sell(@RequestParam(defaultValue = "") String copyIds,
                       @RequestParam(required = false) String notes,
                       RedirectAttributes flash) {
        try {
            ReceiptSnapshot receipt = circulationService.registerExit(new ExitRequest("SALE",
                    copyCodes.ids(copyIds), notes == null || notes.isBlank() ? null : notes.trim()));
            flash.addFlashAttribute("comprovante", receipt);
            flash.addFlashAttribute("info", "Venda registrada. Cupom " + receipt.code() + ".");
        } catch (IllegalArgumentException ex) {
            keep(flash, ex.getMessage(), copyIds, notes);
        } catch (RestClientException ex) {
            keep(flash, BackendMessages.forForm(ex, "Não foi possível registrar a venda."), copyIds, notes);
        }
        return "redirect:" + PAGE;
    }

    /** A tela volta com a mesma lista lida (data-scan-restore) e a observação. */
    private static void keep(RedirectAttributes flash, String message, String copyIds, String notes) {
        flash.addFlashAttribute("erroVenda", message);
        flash.addFlashAttribute("vendaCodigos", FormParsing.join(FormParsing.codes(copyIds)));
        flash.addFlashAttribute("vendaObs", notes);
    }
}
