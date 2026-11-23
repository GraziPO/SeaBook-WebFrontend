package seabook.frontend.dto.users;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import seabook.frontend.config.RoleAccess;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Os dados do usuário logado (GET /api/usuarios/me), para a tela "Minha conta".
 * O e-mail vem completo: é o próprio titular consultando. Campos de aluno ficam null para operadores.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MyAccount(String username,
                        String completeName,
                        String email,
                        String roleCode,
                        String customId,
                        Boolean active,
                        Boolean mustChangePassword,
                        LocalDateTime passwordChangedAt,
                        String ra,
                        String phone,
                        LocalDate lastActivity,
                        String termsVersion,
                        String privacyVersion,
                        LocalDateTime termsAcceptedAt) {

    public boolean student() {
        return "students".equals(roleCode);
    }

    public String roleLabel() {
        return RoleAccess.label(roleCode);
    }

    public boolean isActive() {
        return !Boolean.FALSE.equals(active);
    }

    public boolean termsAccepted() {
        return termsAcceptedAt != null;
    }
}
