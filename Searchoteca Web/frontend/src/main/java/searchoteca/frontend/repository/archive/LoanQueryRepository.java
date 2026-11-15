package searchoteca.repository.archive;

import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import searchoteca.DTO.archive.LoanView;
import searchoteca.DTO.archive.StudentLoanItem;
import searchoteca.DTO.archive.publicSearch.PageResponse;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/** Consultas de leitura dos empréstimos (lista do operador e área do aluno). */
@Repository
public class LoanQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public LoanQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Lista paginada, do mais recente para o mais antigo.
     * filter: OPEN (no prazo), OVERDUE (aberto e vencido), CLOSED, CANCELLED, ou null para todos.
     */
    public PageResponse<LoanView> search(String filter, Pageable page) {
        String where = switch (filter == null ? "" : filter) {
            case "OPEN" -> "WHERE l.status = 'OPEN' AND l.due_date >= CURRENT_DATE";
            case "OVERDUE" -> "WHERE l.status = 'OPEN' AND l.due_date < CURRENT_DATE";
            case "CLOSED" -> "WHERE l.status = 'CLOSED'";
            case "CANCELLED" -> "WHERE l.status = 'CANCELLED'";
            default -> "";
        };
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", page.getPageSize())
                .addValue("offset", page.getOffset());

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM org_loans l " + where, params, Long.class);
        List<LoanView> content = jdbc.query("""
                SELECT l.loan_id, u.complete_name, s.ra, l.loan_date, l.due_date, l.status,
                       (l.status = 'OPEN' AND l.due_date < CURRENT_DATE) AS overdue,
                       COUNT(i.item_id) AS items,
                       COUNT(i.item_id) FILTER (WHERE i.returned_at IS NULL) AS open_items
                FROM org_loans l
                JOIN org_students s ON s.student_id = l.student_id
                JOIN org_users u ON u.user_id = s.user_id
                LEFT JOIN org_loan_items i ON i.loan_id = l.loan_id
                """ + where + """

                GROUP BY l.loan_id, u.complete_name, s.ra
                ORDER BY l.loan_date DESC, l.loan_id DESC
                LIMIT :limit OFFSET :offset
                """, params, (rs, i) -> new LoanView(
                rs.getLong("loan_id"),
                rs.getString("complete_name"),
                rs.getString("ra"),
                rs.getTimestamp("loan_date").toLocalDateTime(),
                rs.getDate("due_date").toLocalDate(),
                rs.getString("status"),
                rs.getBoolean("overdue"),
                rs.getInt("items"),
                rs.getInt("open_items")));

        long count = total == null ? 0 : total;
        int totalPages = (int) Math.ceil((double) count / page.getPageSize());
        return new PageResponse<>(content, page.getPageNumber(), page.getPageSize(), count, totalPages);
    }

    /** Todos os livros dos empréstimos do aluno (abertos e histórico), do mais recente para o mais antigo. */
    public List<StudentLoanItem> itemsOfStudent(Long studentId) {
        return jdbc.query("""
                SELECT l.loan_id, c.copy_id, c.copy_index, c.copy_custom_code, b.isbn, b.title, b.author,
                       l.loan_date, l.due_date, i.returned_at, l.status
                FROM org_loan_items i
                JOIN org_loans l ON l.loan_id = i.loan_id
                JOIN org_copies c ON c.copy_id = i.copy_id
                JOIN org_books b ON b.isbn = c.isbn
                WHERE l.student_id = :sid
                ORDER BY l.loan_date DESC, i.item_id
                LIMIT 300
                """, Map.of("sid", studentId), (rs, i) -> {
            Timestamp returned = rs.getTimestamp("returned_at");
            return new StudentLoanItem(
                    rs.getLong("loan_id"),
                    rs.getLong("copy_id"),
                    rs.getInt("copy_index"),
                    rs.getString("copy_custom_code"),
                    rs.getString("isbn"),
                    rs.getString("title"),
                    rs.getString("author"),
                    rs.getTimestamp("loan_date").toLocalDateTime(),
                    rs.getDate("due_date").toLocalDate(),
                    returned == null ? null : returned.toLocalDateTime(),
                    rs.getString("status"));
        });
    }
}
