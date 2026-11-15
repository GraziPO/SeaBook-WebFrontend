package seabook.frontend.dto.circulation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Uma linha da lista de empréstimos do operador (GET /api/emprestimos), já com o aluno e os livros. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LoanView(Long loanId, String studentName, String ra, LocalDateTime loanDate,
                       LocalDate dueDate, String status, boolean overdue, int items, int openItems) {

    public boolean open() {
        return "OPEN".equals(status);
    }

    /** Cancelar só faz sentido enquanto nenhum livro foi devolvido. */
    public boolean cancellable() {
        return open() && openItems == items;
    }

    public String statusLabel() {
        if (overdue) return "Atrasado";
        return switch (status == null ? "" : status) {
            case "OPEN" -> "Aberto";
            case "CLOSED" -> "Devolvido";
            case "CANCELLED" -> "Cancelado";
            default -> status;
        };
    }

    /** Classe do selo (status-pill) em style.css. */
    public String pillClass() {
        if (overdue) return "overdue";
        return switch (status == null ? "" : status) {
            case "OPEN" -> "open";
            case "CANCELLED" -> "cancelled";
            default -> "closed";
        };
    }
}
