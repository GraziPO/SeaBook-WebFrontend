package searchoteca.repository.users;

import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import searchoteca.DTO.archive.publicSearch.PageResponse;
import searchoteca.DTO.users.UserRow;

import java.util.List;

/** Lista de usuários separada em operadores e alunos (abas da tela de usuários). */
@Repository
public class UserQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public UserQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PageResponse<UserRow> search(boolean students, Pageable page) {
        String where = students ? "WHERE u.role_code = 'students'" : "WHERE u.role_code <> 'students'";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", page.getPageSize())
                .addValue("offset", page.getOffset());

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM org_users u " + where, params, Long.class);
        List<UserRow> content = jdbc.query("""
                SELECT u.user_id, u.username, u.complete_name, u.email, u.role_code, u.status, s.ra
                FROM org_users u
                LEFT JOIN org_students s ON s.user_id = u.user_id
                """ + where + """

                ORDER BY u.username
                LIMIT :limit OFFSET :offset
                """, params, (rs, i) -> new UserRow(
                rs.getLong("user_id"),
                rs.getString("username"),
                rs.getString("complete_name"),
                rs.getString("email"),
                rs.getString("role_code"),
                rs.getBoolean("status"),
                rs.getString("ra")));

        long count = total == null ? 0 : total;
        int totalPages = (int) Math.ceil((double) count / page.getPageSize());
        return new PageResponse<>(content, page.getPageNumber(), page.getPageSize(), count, totalPages);
    }
}
