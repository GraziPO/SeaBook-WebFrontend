package searchoteca.repository.catalog;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import searchoteca.DTO.archive.CopyLookup;

import java.util.Map;
import java.util.Optional;

/** Consulta de exemplar pelo código da etiqueta, já com os dados do livro. */
@Repository
public class CopyQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public CopyQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** O código é comparado sem diferenciar maiúsculas (o leitor pode mandar em minúsculas). */
    public Optional<CopyLookup> findByCode(String code) {
        return jdbc.query("""
                SELECT c.copy_id, c.copy_index, c.copy_custom_code, c.isbn, c.status,
                       c.depart_code, c.local_code, b.title, b.author, b.sale_price
                FROM org_copies c
                JOIN org_books b ON b.isbn = c.isbn
                WHERE upper(c.copy_custom_code) = upper(:code)
                LIMIT 1
                """, Map.of("code", code), (rs, i) -> new CopyLookup(
                rs.getLong("copy_id"),
                rs.getInt("copy_index"),
                rs.getString("copy_custom_code"),
                rs.getString("isbn"),
                rs.getString("title"),
                rs.getString("author"),
                rs.getString("status"),
                rs.getString("depart_code"),
                rs.getString("local_code"),
                rs.getBigDecimal("sale_price"))).stream().findFirst();
    }
}
