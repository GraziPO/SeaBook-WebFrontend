package searchoteca.DTO.archive;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Uma linha da lista de empréstimos do operador: já com o aluno e a contagem de livros. */
public record LoanView(
        Long loanId,
        String studentName,
        String ra,
        LocalDateTime loanDate,
        LocalDate dueDate,
        String status,       // OPEN, CLOSED, CANCELLED
        boolean overdue,     // aberto e com o prazo vencido
        int items,
        int openItems) {
}
