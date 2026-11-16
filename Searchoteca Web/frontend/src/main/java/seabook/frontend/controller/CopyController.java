package seabook.frontend.controller;

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
import seabook.frontend.config.BackendMessages;
import seabook.frontend.dto.ModalReopen;
import seabook.frontend.dto.archive.BatchMoveRequest;
import seabook.frontend.dto.archive.BatchMoveResult;
import seabook.frontend.dto.archive.CopyLabel;
import seabook.frontend.dto.archive.CopyLookup;
import seabook.frontend.dto.archive.CopyRequest;
import seabook.frontend.dto.archive.LabelSummary;
import seabook.frontend.model.Department;
import seabook.frontend.model.Location;
import seabook.frontend.service.BackendService;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * EXEMPLARES: nova entrada, movimentação em lote e reimpressão, cada uma num modal da tela de Livros
 * (a entrada pela coluna "Exemplares" de cada livro; as outras duas pelos botões do topo).
 * As três terminam na mesma tela: a LISTA dos exemplares (exemplar, código, local, situação).
 * As etiquetas não aparecem na tela; existem na página só para a impressão (botão "Imprimir etiquetas").
 * A tela é aberta por endereço (?modo=entrada&codigos=...), então recarregar a página não perde a lista.
 */
@Controller
public class CopyController {

    private static final String PAGE = "/acervo/exemplares";
    private static final String BOOKS = "/acervo/livros";
    private static final String LABELS = PAGE + "/etiquetas";

    /** Situação do exemplar (CopyStatus do backend) em português. */
    private static final java.util.Map<String, String> STATUS_LABELS = java.util.Map.of(
            "AVAILABLE", "Disponível",
            "LOANED", "Emprestado",
            "RESERVED", "Reservado",
            "SOLD", "Vendido",
            "DONATED", "Doado",
            "WRITTEN_OFF", "Baixado");

    private final BackendService backendService;
    private final CopyCodes copyCodes;

    public CopyController(BackendService backendService, CopyCodes copyCodes) {
        this.backendService = backendService;
        this.copyCodes = copyCodes;
    }

    /** A tela Exemplares saiu do menu: entrada, movimentação e reimpressão ficam na tela de Livros. */
    @GetMapping(PAGE)
    public String page() {
        return "redirect:" + BOOKS;
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
                         @RequestParam(required = false) String returnTo,
                         RedirectAttributes flash) {
        try {
            // o backend pede o departamento junto; ele vem da localização escolhida
            Location local = backendService.getLocal(localCode);
            String departCode = local == null ? null : local.getDepartCode();
            List<CopyLabel> labels = backendService.createCopies(
                    new CopyRequest(isbn.trim(), departCode, localCode, quantity));
            flash.addFlashAttribute("info", labels.size() + (labels.size() == 1
                    ? " exemplar cadastrado." : " exemplares cadastrados.") + " Os códigos estão na lista abaixo.");
            return listRedirect("entrada", labels.stream().map(CopyLabel::code).toList());
        } catch (RestClientException ex) {
            flash.addFlashAttribute("erroModal", BackendMessages.forForm(ex, "Não foi possível cadastrar os exemplares."));
            flash.addFlashAttribute("reabrirModal", ModalReopen.of("modal-exemplares", ModalReopen.fields(
                            "isbn", isbn, "quantity", String.valueOf(quantity), "localCode", localCode))
                    .withMessage(bookName(isbn), "ISBN " + isbn));
            return Redirects.back(returnTo, BOOKS);
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
            List<String> moved = result.labels() == null ? List.of()
                    : result.labels().stream().map(CopyLabel::code).toList();
            if (moved.isEmpty()) return "redirect:" + BOOKS;
            return listRedirect("movimentacao", moved);
        } catch (IllegalArgumentException ex) {
            reopenMove(flash, ex.getMessage(), localCode, codes);
        } catch (RestClientException ex) {
            reopenMove(flash, BackendMessages.forForm(ex, "Não foi possível movimentar os exemplares."), localCode, codes);
        }
        return "redirect:" + BOOKS;
    }

    private static void reopenMove(RedirectAttributes flash, String message, String localCode, List<String> codes) {
        flash.addFlashAttribute("erroModal", message);
        flash.addFlashAttribute("reabrirModal", ModalReopen.of("modal-movimentar", ModalReopen.fields(
                "localCode", localCode, "copyCodes", FormParsing.join(codes))));
    }

    // ---------------- lista de exemplares + impressão (entrada, movimentação e reimpressão) ----------------

    /**
     * modo: "entrada", "movimentacao" ou "reimpressao" (o formulário de reimpressão chega sem modo).
     * codigos: os códigos das etiquetas, separados por vírgula ou um por linha.
     */
    @GetMapping(LABELS)
    public String labels(@RequestParam(required = false) String codigos,
                         @RequestParam(defaultValue = "reimpressao") String modo,
                         Model model,
                         RedirectAttributes flash) {
        String mode = Set.of("entrada", "movimentacao").contains(modo) ? modo : "reimpressao";
        model.addAttribute("modo", mode);
        model.addAttribute("statusLabels", STATUS_LABELS);
        model.addAttribute("titulo", switch (mode) {
            case "entrada" -> "Exemplares cadastrados";
            case "movimentacao" -> "Exemplares movimentados";
            default -> "Reimpressão de etiquetas";
        });
        model.addAttribute("crumb", switch (mode) {
            case "entrada" -> "ACERVO / EXEMPLARES / NOVA ENTRADA";
            case "movimentacao" -> "ACERVO / EXEMPLARES / MOVIMENTAÇÃO";
            default -> "ACERVO / EXEMPLARES / REIMPRESSÃO";
        });

        if (codigos == null || codigos.isBlank()) {
            model.addAttribute("exemplares", List.of());
            model.addAttribute("etiquetas", List.of());
            return "pages/etiquetas";
        }
        try {
            // cada código vira o exemplar completo (com a situação) e a etiqueta para imprimir
            List<CopyLookup> copies = copyCodes.resolve(codigos);
            if ("entrada".equals(mode)) {
                copies = copies.stream()
                        .sorted(Comparator.comparing(CopyLookup::copyIndex, Comparator.nullsLast(Comparator.naturalOrder())))
                        .toList();
            }
            List<CopyLabel> labels = backendService.findLabels(copies.stream().map(CopyLookup::copyId).toList());
            model.addAttribute("exemplares", copies);
            model.addAttribute("etiquetas", labels == null ? List.of() : labels);
            model.addAttribute("resumo", summary(mode, copies));
            return "pages/etiquetas";
        } catch (IllegalArgumentException ex) {
            return failure(mode, flash, ex.getMessage(), codigos);
        } catch (RestClientException ex) {
            return failure(mode, flash, BackendMessages.forForm(ex, "Não foi possível carregar os exemplares."), codigos);
        }
    }

    /** Entrada: livro + localização. Movimentação: localização de destino. Reimpressão: sem resumo. */
    private LabelSummary summary(String mode, List<CopyLookup> copies) {
        if (copies.isEmpty() || "reimpressao".equals(mode)) return null;
        CopyLookup first = copies.get(0);
        boolean sameBook = copies.stream().allMatch(c -> first.isbn() != null && first.isbn().equals(c.isbn()));
        String localName = null, departName = null;
        try {
            Location local = backendService.getLocal(first.localCode());
            if (local != null) localName = local.getLocalName();
            Department depart = backendService.getDepart(first.departCode());
            if (depart != null) departName = depart.getDepartName();
        } catch (RestClientException ex) {
            // sem os nomes, o resumo mostra só os códigos
        }
        return new LabelSummary(
                sameBook ? first.title() : null,
                sameBook ? first.author() : null,
                sameBook ? first.isbn() : null,
                first.localCode(), localName, first.departCode(), departName,
                copies.size());
    }

    /** Título do livro para o topo do modal reaberto; se não der para buscar, mostra só o ISBN. */
    private String bookName(String isbn) {
        try {
            var book = backendService.getBook(isbn);
            if (book != null && book.getTitle() != null) return book.getTitle();
        } catch (RestClientException ex) {
            // sem o título, o modal mostra só o ISBN
        }
        return "Livro";
    }

        private static String listRedirect(String mode, List<String> codes) {
        return "redirect:" + Pages.url(LABELS, "modo", mode, "codigos", String.join(",", codes));
    }

    private static String failure(String mode, RedirectAttributes flash, String message, String codigos) {
        if ("reimpressao".equals(mode)) {
            flash.addFlashAttribute("erroModal", message);
            flash.addFlashAttribute("reabrirModal", ModalReopen.of("modal-reimprimir", ModalReopen.fields(
                    "codigos", FormParsing.join(FormParsing.codes(codigos)))));
        } else {
            flash.addFlashAttribute("erro", message);
        }
        return "redirect:" + BOOKS;
    }
}
