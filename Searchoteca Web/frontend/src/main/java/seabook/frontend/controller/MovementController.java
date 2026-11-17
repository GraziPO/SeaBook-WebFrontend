package seabook.frontend.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import seabook.frontend.config.BackendMessages;
import seabook.frontend.dto.archive.BatchMoveRequest;
import seabook.frontend.dto.archive.BatchMoveResult;
import seabook.frontend.dto.archive.CopyLabel;
import seabook.frontend.dto.archive.CopyLookup;
import seabook.frontend.dto.archive.LocationBookStock;
import seabook.frontend.dto.archive.MovedLine;
import seabook.frontend.dto.archive.QuantityMoveRequest;
import seabook.frontend.dto.archive.QuantityMoveResult;
import seabook.frontend.model.Department;
import seabook.frontend.model.Location;
import seabook.frontend.service.BackendService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ACERVO / MOVIMENTAÇÕES. A tela inicial tem duas opções:
 *
 *  - Movimentação avulsa: o operador lê UM exemplar, escolhe a nova localização e confirma.
 *  - Movimentação em lote: busca a localização de origem, abre "Movimentar conteúdo" e informa,
 *    por livro, QUANTOS exemplares saem (no máximo o que está na estante). O backend escolhe QUAIS
 *    e devolve os números ("Dom Casmurro — cópias 4 e 3 movimentadas").
 *
 * As duas terminam na lista de exemplares movimentados (com os códigos novos e a impressão das etiquetas),
 * a mesma tela da entrada e da reimpressão (CopyController, modo=movimentacao).
 */
@Controller
public class MovementController {

    private static final String PAGE = "/acervo/movimentacoes";
    private static final String SINGLE = PAGE + "/avulsa";
    private static final String BATCH = PAGE + "/lote";
    private static final String LABELS = "/acervo/exemplares/etiquetas";

    private final BackendService backendService;

    public MovementController(BackendService backendService) {
        this.backendService = backendService;
    }

    /** As duas opções: avulsa e em lote. */
    @GetMapping(PAGE)
    public String home() {
        return "pages/movimentacoes";
    }

    // ---------------- movimentação avulsa ----------------

    @GetMapping(SINGLE)
    public String single(Model model) {
        model.addAttribute("localizacoes", locations());
        return "pages/movimentacao-avulsa";
    }

    /**
     * copyCode: código da etiqueta lida; localCode: a nova localização.
     * O exemplar é consultado antes para guardar de onde ele saiu e dar a mensagem certa.
     */
    @PostMapping(SINGLE)
    public String moveSingle(@RequestParam(defaultValue = "") String copyCode,
                             @RequestParam(defaultValue = "") String localCode,
                             RedirectAttributes flash) {
        String code = copyCode.trim().toUpperCase();
        try {
            if (code.isEmpty()) throw new IllegalArgumentException("Leia a etiqueta do exemplar.");
            if (localCode.isBlank()) throw new IllegalArgumentException("Escolha a nova localização.");

            CopyLookup copy = lookup(code);
            if (localCode.equals(copy.localCode())) {
                throw new IllegalArgumentException("Esse exemplar já está em " + localCode + ".");
            }
            if (!"AVAILABLE".equals(copy.status())) {
                throw new IllegalArgumentException("Só exemplar disponível na estante pode ser movimentado"
                        + " (este está " + statusText(copy.status()) + ").");
            }

            BatchMoveResult result = backendService.moveCopies(new BatchMoveRequest(localCode, List.of(code), 1));
            List<String> moved = result.labels() == null ? List.of()
                    : result.labels().stream().map(CopyLabel::code).toList();
            if (moved.isEmpty()) throw new IllegalArgumentException("Esse exemplar já está em " + localCode + ".");

            flash.addFlashAttribute("info", copy.title() + " — " + MovedLine.describe(List.of(copy.copyIndex()))
                    + " de " + copy.localCode() + " para " + result.localCode() + ".");
            return "redirect:" + Pages.url(LABELS, "modo", "movimentacao", "codigos", String.join(",", moved),
                    "de", copy.localCode(), "tipo", "avulsa");
        } catch (IllegalArgumentException ex) {
            failSingle(flash, ex.getMessage(), code, localCode);
        } catch (RestClientException ex) {
            failSingle(flash, BackendMessages.forForm(ex, "Não foi possível movimentar o exemplar."), code, localCode);
        }
        return "redirect:" + SINGLE;
    }

    private CopyLookup lookup(String code) {
        try {
            CopyLookup copy = backendService.lookupCopy(code);
            if (copy == null) throw new IllegalArgumentException("Exemplar \"" + code + "\" não encontrado. Confira a etiqueta.");
            return copy;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new IllegalArgumentException("Exemplar \"" + code + "\" não encontrado. Confira a etiqueta.");
            }
            throw ex;
        }
    }

    private static void failSingle(RedirectAttributes flash, String message, String code, String localCode) {
        flash.addFlashAttribute("erro", message);
        flash.addFlashAttribute("codigoAnterior", code);
        flash.addFlashAttribute("destinoAnterior", localCode);
    }

    // ---------------- movimentação em lote: busca da localização ----------------

    @GetMapping(BATCH)
    public String batch(@RequestParam(required = false) String busca,
                        @RequestParam(defaultValue = "0") int page,
                        Model model) {
        String q = busca == null ? "" : busca.trim();
        model.addAttribute("busca", q);
        model.addAttribute("pagina", backendService.locationShelves(q, page));
        model.addAttribute("paginaUrl", Pages.url(BATCH, "busca", q));
        return "pages/movimentacao-lote";
    }

    // ---------------- movimentação em lote: "Movimentar conteúdo" ----------------

    @GetMapping(BATCH + "/{code}")
    public String content(@PathVariable String code, Model model) {
        Location origin = backendService.getLocal(code);
        model.addAttribute("origem", origin);
        model.addAttribute("departamento", departName(origin.getDepartCode()));

        // só os livros com exemplar na estante: emprestado ou reservado não está ali para ser movido
        List<LocationBookStock> books = backendService.localBooksStock(code).stream()
                .filter(b -> b.available() > 0)
                .toList();
        model.addAttribute("livros", books);
        model.addAttribute("naEstante", books.stream().mapToLong(LocationBookStock::available).sum());
        model.addAttribute("destinos", locations().stream()
                .filter(l -> !code.equals(l.getLocalCode()))
                .toList());
        return "pages/movimentar-conteudo";
    }

    /**
     * isbn e quantity chegam em pares, na ordem das linhas da tabela (quantidade vazia = 0).
     * O backend confere de novo o limite da estante e move tudo ou nada.
     */
    @PostMapping(BATCH + "/{code}")
    public String moveContent(@PathVariable String code,
                              @RequestParam(defaultValue = "") String localCode,
                              @RequestParam(name = "isbn", required = false) List<String> isbns,
                              @RequestParam(name = "quantity", required = false) List<String> quantities,
                              RedirectAttributes flash) {
        Map<String, Integer> typed = new LinkedHashMap<>();
        try {
            List<QuantityMoveRequest.Item> items = new ArrayList<>();
            List<String> isbnList = isbns == null ? List.of() : isbns;
            for (int i = 0; i < isbnList.size(); i++) {
                String raw = quantities != null && i < quantities.size() ? quantities.get(i) : "";
                int qty = parseQuantity(raw);
                typed.put(isbnList.get(i), qty);
                if (qty > 0) items.add(new QuantityMoveRequest.Item(isbnList.get(i), qty));
            }
            if (localCode.isBlank()) throw new IllegalArgumentException("Escolha a nova localização.");
            if (items.isEmpty()) throw new IllegalArgumentException("Informe a quantidade de ao menos um livro.");

            QuantityMoveResult result = backendService.moveByQuantity(new QuantityMoveRequest(code, localCode, items));
            List<String> moved = result.labels() == null ? List.of()
                    : result.labels().stream().map(CopyLabel::code).toList();

            flash.addFlashAttribute("info", result.moved() + (result.moved() == 1
                    ? " exemplar movimentado de " : " exemplares movimentados de ")
                    + code + " para " + result.toLocalCode() + ". Os códigos novos estão na lista abaixo.");
            if (moved.isEmpty()) return "redirect:" + BATCH;
            return "redirect:" + Pages.url(LABELS, "modo", "movimentacao", "codigos", String.join(",", moved),
                    "de", code, "tipo", "lote");
        } catch (IllegalArgumentException ex) {
            failBatch(flash, ex.getMessage(), localCode, typed);
        } catch (RestClientException ex) {
            failBatch(flash, BackendMessages.forForm(ex, "Não foi possível movimentar os exemplares."), localCode, typed);
        }
        return "redirect:" + BATCH + "/" + code;
    }

    private static int parseQuantity(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < 0) throw new IllegalArgumentException("A quantidade não pode ser negativa.");
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Quantidade inválida: \"" + raw.trim() + "\".");
        }
    }

    /** Volta para a tela com o destino e as quantidades que a pessoa tinha digitado. */
    private static void failBatch(RedirectAttributes flash, String message, String localCode, Map<String, Integer> typed) {
        flash.addFlashAttribute("erro", message);
        flash.addFlashAttribute("destinoAnterior", localCode);
        flash.addFlashAttribute("quantidadesAnteriores", typed);
    }

    // ---------------- apoio ----------------

    private List<Location> locations() {
        try {
            return backendService.listLocal();
        } catch (RestClientException ex) {
            return List.of();
        }
    }

    private String departName(String departCode) {
        if (departCode == null) return null;
        try {
            Department d = backendService.getDepart(departCode);
            return d == null ? null : d.getDepartName();
        } catch (RestClientException ex) {
            return null;
        }
    }

    private static String statusText(String status) {
        if (status == null) return "fora da estante";
        return switch (status) {
            case "LOANED" -> "emprestado";
            case "RESERVED" -> "reservado";
            case "SOLD" -> "vendido";
            case "DONATED" -> "doado";
            case "WRITTEN_OFF" -> "baixado";
            default -> status;
        };
    }
}
