package seabook.frontend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import seabook.frontend.config.BackendMessages;
import seabook.frontend.dto.ModalReopen;
import seabook.frontend.model.Book;
import seabook.frontend.service.BackendService;

import java.math.BigDecimal;
import java.util.Map;

/**
 * ACERVO / LIVROS: lista + modal de cadastrar/editar (com a busca do ISBN na Open Library)
 * + modal do preço de venda (só root, com o módulo de vendas ligado).
 */
@Controller
public class BookController {

    private static final String LIST = "/acervo/livros";

    private final BackendService backendService;

    public BookController(BackendService backendService) {
        this.backendService = backendService;
    }

    @GetMapping(LIST)
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("pagina", backendService.listBooksPage(page));
        model.addAttribute("vendasAtivas", backendService.salesEnabled());
        return "pages/livros";
    }

    /** Usado pelo js/isbn.js: repassa o status e o corpo do backend como vieram. */
    @GetMapping(LIST + "/isbn")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> lookupIsbn(@RequestParam String isbn) {
        return backendService.lookupIsbn(isbn);
    }

    @PostMapping(LIST)
    public String create(@RequestParam String isbn,
                         @RequestParam String title,
                         @RequestParam String author,
                         @RequestParam(required = false) String publisher,
                         @RequestParam(name = "rel_year", required = false) String relYear,
                         @RequestParam(required = false) String genre,
                         @RequestParam(required = false) String returnTo,
                         RedirectAttributes flash) {
        Book book = book(isbn, title, author, publisher, relYear, genre);
        try {
            backendService.createBook(book);
            flash.addFlashAttribute("info", "Livro \"" + book.getTitle() + "\" cadastrado. Agora cadastre os exemplares em Exemplares.");
        } catch (RestClientException ex) {
            flash.addFlashAttribute("erroModal", BackendMessages.forForm(ex, "Não foi possível cadastrar o livro."));
            flash.addFlashAttribute("reabrirModal", reopen(book).withAction(LIST, "Cadastrar livro", "Cadastrar"));
        }
        return Redirects.back(returnTo, LIST);
    }

    @PostMapping(LIST + "/{isbn}/editar")
    public String update(@PathVariable String isbn,
                         @RequestParam String title,
                         @RequestParam String author,
                         @RequestParam(required = false) String publisher,
                         @RequestParam(name = "rel_year", required = false) String relYear,
                         @RequestParam(required = false) String genre,
                         @RequestParam(required = false) String returnTo,
                         RedirectAttributes flash) {
        Book book = book(isbn, title, author, publisher, relYear, genre);   // o ISBN não muda na edição
        try {
            backendService.updateBook(isbn, book);
            flash.addFlashAttribute("info", "Livro \"" + book.getTitle() + "\" atualizado.");
        } catch (RestClientException ex) {
            flash.addFlashAttribute("erroModal", BackendMessages.forForm(ex, "Não foi possível salvar o livro."));
            flash.addFlashAttribute("reabrirModal", reopen(book)
                    .withAction(LIST + "/" + isbn + "/editar", "Editar livro", "Salvar")
                    .withReadonly("isbn"));
        }
        return Redirects.back(returnTo, LIST);
    }

    @PostMapping(LIST + "/{isbn}/preco")
    public String price(@PathVariable String isbn,
                        @RequestParam(required = false) String salePrice,
                        @RequestParam(required = false) String returnTo,
                        RedirectAttributes flash) {
        try {
            BigDecimal price = salePrice == null || salePrice.isBlank()
                    ? null
                    : new BigDecimal(salePrice.trim().replace(',', '.'));
            backendService.updatePrice(isbn, price);
            flash.addFlashAttribute("info", price == null
                    ? "Livro retirado da venda."
                    : "Preço atualizado para R$ " + String.format(java.util.Locale.ROOT, "%.2f", price).replace('.', ',') + ".");
        } catch (NumberFormatException ex) {
            flash.addFlashAttribute("erro", "Preço inválido. Use números, por exemplo 35,00.");
        } catch (RestClientException ex) {
            flash.addFlashAttribute("erro", BackendMessages.forForm(ex, "Não foi possível alterar o preço."));
        }
        return Redirects.back(returnTo, LIST);
    }

    @PostMapping(LIST + "/{isbn}/excluir")
    public String delete(@PathVariable String isbn,
                         @RequestParam(required = false) String returnTo,
                         RedirectAttributes flash) {
        try {
            backendService.deleteBook(isbn);
            flash.addFlashAttribute("info", "Livro " + isbn + " excluído.");
        } catch (RestClientException ex) {
            flash.addFlashAttribute("erro", BackendMessages.forForm(ex, "Não foi possível excluir o livro."));
        }
        return Redirects.back(returnTo, LIST);
    }

    private static Book book(String isbn, String title, String author, String publisher, String relYear, String genre) {
        Book book = new Book();
        book.setIsbn(isbn == null ? null : isbn.trim());
        book.setTitle(title == null ? null : title.trim());
        book.setAuthor(author == null ? null : author.trim());
        book.setPublisher(blankToNull(publisher));
        book.setRel_year(FormParsing.intOrNull(relYear));
        book.setGenre(blankToNull(genre));
        return book;
    }

    private static ModalReopen reopen(Book b) {
        return ModalReopen.of("modal-livro", ModalReopen.fields(
                "isbn", b.getIsbn(),
                "title", b.getTitle(),
                "author", b.getAuthor(),
                "publisher", b.getPublisher(),
                "rel_year", b.getRel_year() == null ? null : String.valueOf(b.getRel_year()),
                "genre", b.getGenre()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
