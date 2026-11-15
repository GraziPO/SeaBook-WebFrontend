package searchoteca.frontend.dto.users;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Uma linha da lista de usuários (GET /api/usuarios?tipo=operadores|alunos). ra só vem para aluno. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserRow(Long userId, String username, String completeName, String email,
                      String roleCode, Boolean status, String ra) {

    public boolean active() {
        return Boolean.TRUE.equals(status);
    }
}
