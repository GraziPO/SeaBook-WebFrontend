package seabook.frontend.dto.waitlist;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

/** Uma posição na fila de espera (GET /api/fila/minhas). status: WAITING ou NOTIFIED (reserva pronta). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WaitlistEntry(Long waitlistId, String isbn, String title, String author, String status,
                            int position, LocalDateTime createdAt, LocalDateTime expiresAt,
                            String copyCode, String localCode, String studentName, String ra) {

    public boolean notified() {
        return "NOTIFIED".equals(status);
    }
}
