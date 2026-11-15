package searchoteca.DTO.archive;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Um livro de um empréstimo do aluno (área "Meus empréstimos"). */
public record StudentLoanItem(
        Long loanId,
        Long copyId,
        Integer copyIndex,
        String code,
        String isbn,
        String title,
        String author,
        LocalDateTime loanDate,
        LocalDate dueDate,
        LocalDateTime returnedAt,
        String loanStatus) {
}
