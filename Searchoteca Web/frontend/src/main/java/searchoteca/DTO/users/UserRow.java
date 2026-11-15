package searchoteca.DTO.users;

/** Uma linha da lista de usuários (operadores ou alunos). O hash da senha nunca sai. */
public record UserRow(
        Long userId,
        String username,
        String completeName,
        String email,
        String roleCode,
        Boolean status,
        String ra) {        // só alunos
}
