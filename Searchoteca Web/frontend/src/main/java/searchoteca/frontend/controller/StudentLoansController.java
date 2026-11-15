package searchoteca.frontend.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import searchoteca.frontend.dto.catalog.PageResponse;
import searchoteca.frontend.dto.circulation.StudentLoanItem;
import searchoteca.frontend.dto.waitlist.WaitlistEntry;
import searchoteca.frontend.service.BackendService;
import searchoteca.frontend.service.CirculationService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * MEUS EMPRÉSTIMOS (aluno): o que está com ele (a devolução mais próxima primeiro),
 * a fila de espera (reserva pronta no topo) e o histórico, numa tela só.
 */
@Controller
public class StudentLoansController {

    private static final String PAGE = "/meus-emprestimos";

    private final CirculationService circulationService;

    public StudentLoansController(CirculationService circulationService) {
        this.circulationService = circulationService;
    }

    @GetMapping(PAGE)
    public String page(@RequestParam(defaultValue = "0") int page, Model model) {
        List<StudentLoanItem> items = nullSafe(circulationService.myLoanItems());
        List<WaitlistEntry> fila = nullSafe(circulationService.myWaitlist());

        List<StudentLoanItem> abertos = items.stream()
                .filter(StudentLoanItem::withStudent)
                .sorted(Comparator.comparing(StudentLoanItem::dueDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<StudentLoanItem> devolvidos = items.stream()
                .filter(i -> i.returnedAt() != null)
                .sorted(Comparator.comparing(StudentLoanItem::returnedAt, Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder())))
                .toList();

        model.addAttribute("abertos", abertos);
        model.addAttribute("comVoce", abertos.size());
        model.addAttribute("atrasados", abertos.stream().filter(StudentLoanItem::overdue).count());
        model.addAttribute("proximaDevolucao", abertos.stream()
                .map(StudentLoanItem::dueDate)
                .filter(d -> d != null)
                .min(Comparator.<LocalDate>naturalOrder())
                .orElse(null));
        model.addAttribute("fila", fila.stream()
                .sorted(Comparator.comparing((WaitlistEntry w) -> !w.notified()).thenComparing(WaitlistEntry::position))
                .toList());
        model.addAttribute("reservas", fila.stream().filter(WaitlistEntry::notified).toList());
        model.addAttribute("historico", pageOf(devolvidos, page));
        return "pages/meus-emprestimos";
    }

    /** O histórico vem inteiro (até 300 livros) e é paginado aqui, 20 por página. */
    private static PageResponse<StudentLoanItem> pageOf(List<StudentLoanItem> all, int page) {
        int size = BackendService.PAGE_SIZE;
        int totalPages = Math.max(1, (all.size() + size - 1) / size);
        int p = Math.min(Math.max(page, 0), totalPages - 1);
        int from = p * size;
        int to = Math.min(from + size, all.size());
        return new PageResponse<>(all.subList(from, to), p, size, all.size(), totalPages);
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
