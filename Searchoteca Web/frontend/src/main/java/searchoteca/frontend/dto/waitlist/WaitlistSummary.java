package searchoteca.frontend.dto.waitlist;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

/** Situação da fila de um título para o aluno logado (GET /api/fila/{isbn}). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WaitlistSummary(String isbn, int queueSize, int limit, int availableCopies,
                              int circulatingCopies, boolean joined, Integer position, String status,
                              LocalDateTime expiresAt, boolean canJoin) {}
