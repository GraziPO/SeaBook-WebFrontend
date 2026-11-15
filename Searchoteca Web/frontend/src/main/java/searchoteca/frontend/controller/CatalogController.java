package searchoteca.frontend.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils;
import searchoteca.frontend.config.BackendMessages;
import searchoteca.frontend.dto.catalog.BookDetail;
import searchoteca.frontend.dto.waitlist.WaitlistSummary;
import searchoteca.frontend.service.CatalogService;
import searchoteca.frontend.service.CirculationService;

import java.nio.charset.StandardCharsets;

/**
 * ACERVO / CONSULTA: tela inicial de todos os perfis e também pública (sem login).
 * A lista mostra só título, autor, editora e gênero; livro sem exemplar disponível fica acinzentado
 * com "Indisponível no momento". Disponibilidade, onde encontrar e a fila de espera ficam no detalhe,
 * que abre em modal (?parcial=true devolve só o pedaço do componente) ou em página própria.
 */
@Controller
public class CatalogController {

    private static final String PAGE = "/acervo/consulta";

    private final CatalogService catalogService;
    private final CirculationService circulationService;

    public CatalogController(CatalogService catalogService, CirculationService circulationService) {
        this.catalogService = catalogService;
        this.circulationService = circulationService;
    }

    @GetMapping(PAGE)
    public String search(@RequestParam(required = false) String q,
                         @RequestParam(defaultValue = "0") int page,
                         Model model) {
        model.addAttribute("q", q == null ? "" : q.trim());
        model.addAttribute("resultado", catalogService.search(q, page));
        model.addAttribute("paginaUrl", Pages.url(PAGE, "q", q));
        return "pages/consulta";
    }

    @GetMapping(PAGE + "/{isbn}")
    public String detail(@PathVariable String isbn,
                         @RequestParam(required = false) String q,
                         @RequestParam(defaultValue = "false") boolean parcial,
                         HttpSession session,
                         Model model) {
        BookDetail livro = catalogService.detail(isbn);
        model.addAttribute("livro", livro);
        model.addAttribute("fila", waitlistFor(livro, session));
        model.addAttribute("q", q);
        // o modal da consulta busca só o miolo (js/app.js, data-modal-src)
        return parcial ? "pages/consulta-parcial" : "pages/consulta-detalhe";
    }

    /** A fila só interessa ao aluno, e só quando não há exemplar disponível. */
    private WaitlistSummary waitlistFor(BookDetail livro, HttpSession session) {
        if (livro == null || livro.totalAvailable() > 0) return null;
        if (session == null || !"students".equals(session.getAttribute("role"))) return null;
        try {
            return circulationService.waitlistSummary(livro.isbn());
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 401) throw ex;
            return null;   // sem o quadro da fila, o detalhe continua aparecendo
        } catch (RestClientException ex) {
            return null;
        }
    }

    // ---------------- fila de espera (aluno) ----------------

    @PostMapping("/fila/{isbn}")
    public String join(@PathVariable String isbn, RedirectAttributes flash) {
        try {
            circulationService.joinWaitlist(isbn);
            flash.addFlashAttribute("info", "Você entrou na fila de espera. Avisaremos por e-mail quando chegar a sua vez.");
        } catch (RestClientException ex) {
            flash.addFlashAttribute("erro", BackendMessages.forForm(ex, "Não foi possível entrar na fila."));
        }
        return "redirect:" + PAGE + "/" + UriUtils.encodePathSegment(isbn, StandardCharsets.UTF_8);
    }

    @PostMapping("/fila/{isbn}/sair")
    public String leave(@PathVariable String isbn,
                        @RequestParam(required = false) String returnTo,
                        RedirectAttributes flash) {
        try {
            circulationService.leaveWaitlist(isbn);
            flash.addFlashAttribute("info", "Você saiu da fila de espera.");
        } catch (RestClientException ex) {
            flash.addFlashAttribute("erro", BackendMessages.forForm(ex, "Não foi possível sair da fila."));
        }
        // o confirmar dentro do modal da consulta leva o endereço do pedaço (?parcial=true): não volta para ele
        if (returnTo != null && returnTo.contains("parcial=")) returnTo = null;
        return Redirects.back(returnTo, "/meus-emprestimos");
    }

    // ---------------- endereços antigos ----------------

    @GetMapping({"/acervo", "/consulta"})
    public String oldSearch(@RequestParam(required = false) String q) {
        return q == null || q.isBlank() ? "redirect:" + PAGE : "redirect:" + Pages.url(PAGE, "q", q);
    }

    @GetMapping("/consulta/{isbn}")
    public String oldDetail(@PathVariable String isbn) {
        return "redirect:" + PAGE + "/" + UriUtils.encodePathSegment(isbn, StandardCharsets.UTF_8);
    }
}
