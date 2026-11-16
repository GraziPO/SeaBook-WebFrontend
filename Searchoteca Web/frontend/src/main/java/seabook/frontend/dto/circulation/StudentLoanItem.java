package seabook.frontend.dto.circulation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** Um livro de um empréstimo do aluno logado (GET /api/emprestimos/historico/itens). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StudentLoanItem(Long loanId, Long copyId, Integer copyIndex, String code, String isbn,
                              String title, String author, LocalDateTime loanDate, LocalDate dueDate,
                              LocalDateTime returnedAt, String loanStatus) {

    /** Ainda está com o aluno: não devolvido e empréstimo não cancelado. */
    public boolean withStudent() {
        return returnedAt == null && "OPEN".equals(loanStatus);
    }

    public boolean overdue() {
        return withStudent() && dueDate != null && LocalDate.now().isAfter(dueDate);
    }

    public long daysLate() {
        return overdue() ? ChronoUnit.DAYS.between(dueDate, LocalDate.now()) : 0;
    }

    public long daysLeft() {
        return dueDate == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), dueDate));
    }

    /** Histórico: devolvido depois do prazo. */
    public boolean late() {
        return returnedAt != null && dueDate != null && returnedAt.toLocalDate().isAfter(dueDate);
    }

    public boolean cancelled() {
        return "CANCELLED".equals(loanStatus);
    }
}
