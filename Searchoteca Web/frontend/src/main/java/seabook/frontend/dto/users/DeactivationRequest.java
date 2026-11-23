package seabook.frontend.dto.users;

/** Corpo do POST /api/usuarios/me/desativacao: o motivo é opcional. */
public record DeactivationRequest(String reason) {
}
