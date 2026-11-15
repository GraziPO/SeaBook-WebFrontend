package searchoteca.frontend.dto.users;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

/**
 * Pedido de desativação ou reativação de conta (GET /api/usuarios/pedidos).
 * type: DEACTIVATE | REACTIVATE; status: PENDING | APPROVED | REJECTED | CANCELLED;
 * deactivationReason: por que a conta está desativada (INACTIVITY | ADMIN | REQUEST).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountRequestView(Long requestId, Long userId, String username, String completeName, String ra,
                                 String type, String status, String reason, LocalDateTime createdAt,
                                 LocalDateTime decidedAt, String decidedBy, String decisionNote,
                                 String deactivationReason) {

    public boolean pending() {
        return "PENDING".equals(status);
    }

    public String typeLabel() {
        return "REACTIVATE".equals(type) ? "Reativação" : "Desativação";
    }

    public String statusLabel() {
        return switch (status == null ? "" : status) {
            case "PENDING" -> "Pendente";
            case "APPROVED" -> "Aprovado";
            case "REJECTED" -> "Recusado";
            case "CANCELLED" -> "Cancelado pelo aluno";
            default -> status;
        };
    }

    public String pillClass() {
        return switch (status == null ? "" : status) {
            case "PENDING" -> "open";
            case "APPROVED" -> "active";
            case "REJECTED" -> "inactive";
            default -> "cancelled";
        };
    }

    public String deactivationLabel() {
        if (deactivationReason == null) return "Conta ativa";
        return switch (deactivationReason) {
            case "INACTIVITY" -> "Desativada por inatividade";
            case "ADMIN" -> "Desativada pela administração";
            case "REQUEST" -> "Desativada a pedido do aluno";
            default -> deactivationReason;
        };
    }
}
