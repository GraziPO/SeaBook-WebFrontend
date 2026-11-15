package searchoteca.frontend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import searchoteca.frontend.config.BackendMessages;
import searchoteca.frontend.dto.ModalReopen;
import searchoteca.frontend.dto.archive.BatchMoveRequest;
import searchoteca.frontend.dto.archive.BatchMoveResult;
import searchoteca.frontend.dto.archive.CopyLabel;
import searchoteca.frontend.dto.archive.CopyLookup;
import searchoteca.frontend.dto.archive.CopyRequest;
import searchoteca.frontend.model.Location;
import searchoteca.frontend.service.BackendService;

import java.util.List;

/**
 * ACERVO / EXEMPLARES: nova entrada, movimentação em lote e reimpressão, cada uma num modal.
 * As três terminam na tela de etiquetas (fila única vertical, para a impressora térmica).
 */
@Controller
public class CopyController {

    private static final String PAGE = "/acervo/exemplares";
    private static final String LABELS = PAGE + "/etiquetas";

    private final BackendService backendService;
    private final CopyCodes copyCodes;

    public CopyController(BackendService backendService, CopyCodes copyCodes) {
        this.backendService = backendService;
        this.copyCodes = copyCodes;
    }

    @GetMapping(PAGE)
    public String page(Model model) {
        model.addAttribute("localizacoes", backendService.listLocal());
        return "pages/exemplares";
    }

    /**
     * Lista de leitura (js/scan.js): GET /exemplares/consulta?codigo=X.
     * 200 com o exemplar, 404 se a etiqueta não existir; outros erros vão como vieram.
     */
    @GetMapping("/exemplares/consulta")
    @ResponseBody
    public ResponseEntity<CopyLookup> lookup(@RequestParam String codigo) {
        if (codigo == null || codigo.isBlank()) return ResponseEntity.notFound().build();
        try {
            return ResponseEntity.ok(backendService.lookupCopy(codigo));
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 401) throw ex;
            return ResponseEntity.status(ex.getStatusCode()).build();
        }
    }

    // ---------------- nova entrada ----------------

    @PostMapping(PAGE)
    public String create(@RequestParam String isbn,
                         @RequestParam(defaultValue = "1") int quantity,
                         @RequestParam String localCode,
                         RedirectAttributes flash) {
        try {
            // o backend pede o departamento junto; ele vem da localização escolhida
            Location local = backendService.getLocal(localCode);
            String departCode = local == null ? null : local.getDepartCode();
            List<CopyLabel> labels = backendService.createCopies(
                    new CopyRequest(isbn.trim(), departCode, localCode, quantity));
            flash.addFlashAttribute("etiquetas", labels);
            flash.addFlashAttribute("titulo", "Etiquetas da nova entrada");
            flash.addFlashAttribute("info", labels.size() + (labels.size() == 1
                    ? " exemplar cadastrado." : " exemplares cadastrados.") + " Confira o tamanho e imprima as etiquetas.");
            return "redirect:" + LABELS;
        } catch (RestClientException ex) {
            flash.addFlashAttribute("erroModal", BackendMessages.forForm(ex, "Não foi possível cadastrar os exemplares."));
            flash.addFlashAttribute("reabrirModal", ModalReopen.of("modal-entrada", ModalReopen.fields(
                    "isbn", isbn, "quantity", String.valueOf(quantity), "localCode", localCode)));
            return "redirect:" + PAGE;
        }
    }

    // ---------------- movimentação em lote ----------------

    @PostMapping(PAGE + "/movimentacao")
    public String move(@RequestParam String localCode,
                       @RequestParam(defaultValue = "") String copyCodes,
                       RedirectAttributes flash) {
        List<String> codes = FormParsing.codes(copyCodes);
        try {
            if (codes.isEmpty()) throw new IllegalArgumentException("Leia ao menos um exemplar.");
            BatchMoveResult result = backendService.moveCopies(new BatchMoveRequest(localCode, codes, codes.size()));
            StringBuilder msg = new StringBuilder(result.moved() + (result.moved() == 1
                    ? " exemplar movido para " : " exemplares movidos para ") + result.localCode() + ".");
            if (result.alreadyInLocation() != null && !result.alreadyInLocation().isEmpty()) {
                msg.append(" Já estavam nessa localização: ").append(String.join(", ", result.alreadyInLocation())).append('.');
            }
            flash.addFlashAttribute("info", msg.toString());
            flash.addFlashAttribute("etiquetas", result.labels() == null ? List.of() : result.labels());
            flash.addFlashAttribute("titulo", "Etiquetas da movimentação");
            return "redirect:" + LABELS;
        } catch (IllegalArgumentException ex) {
            reopenMove(flash, ex.getMessage(), localCode, codes);
        } catch (RestClientException ex) {
            reopenMove(flash, BackendMessages.forForm(ex, "Não foi possível movimentar os exemplares."), localCode, codes);
        }
        return "redirect:" + PAGE;
    }

    private static void reopenMove(RedirectAttributes flash, String message, String localCode, List<String> codes) {
        flash.addFlashAttribute("erroModal", message);
        flash.addFlashAttribute("reabrirModal", ModalReopen.of("modal-movimentar", ModalReopen.fields(
                "localCode", localCode, "copyCodes", FormParsing.join(codes))));
    }

    // ---------------- etiquetas (entrada, movimentação e reimpressão) ----------------

    @GetMapping(LABELS)
    public String labels(@RequestParam(required = false) String codigos, Model model, RedirectAttributes flash) {
        if (!model.containsAttribute("etiquetas")) {
            if (codigos == null || codigos.isBlank()) {
                model.addAttribute("etiquetas", List.of());
            } else {
                try {
                    model.addAttribute("etiquetas", backendService.findLabels(copyCodes.ids(codigos)));
                } catch (IllegalArgumentException ex) {
                    return reopenReprint(flash, ex.getMessage(), codigos);
                } catch (RestClientException ex) {
                    return reopenReprint(flash, BackendMessages.forForm(ex, "Não foi possível gerar as etiquetas."), codigos);
                }
            }
        }
        if (!model.containsAttribute("titulo")) model.addAttribute("titulo", "Reimpressão de etiquetas");
        return "pages/etiquetas";
    }

    private static String reopenReprint(RedirectAttributes flash, String message, String codigos) {
        flash.addFlashAttribute("erroModal", message);
        flash.addFlashAttribute("reabrirModal", ModalReopen.of("modal-reimprimir", ModalReopen.fields(
                "codigos", FormParsing.join(FormParsing.codes(codigos)))));
        return "redirect:" + PAGE;
    }
}
