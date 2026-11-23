package seabook.frontend.dto.auth;

/**
 * Corpo de POST /api/auth/reativacao. A conta está desativada e não consegue entrar,
 * então o aluno se identifica com usuário e senha; o motivo é opcional.
 */
public record ReactivationRequest(String username, String password, String reason) {}
