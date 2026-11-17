package seabook.frontend.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientResponseException;
import seabook.frontend.dto.archive.CopyLookup;
import seabook.frontend.service.BackendService;
import seabook.frontend.support.BackendErrors;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Cada código lido vira o exemplar; etiqueta repetida conta uma vez; código inexistente avisa qual foi. */
class CopyCodesTest {

    private BackendService backend;
    private CopyCodes copyCodes;

    static CopyLookup copy(long id, String code) {
        return new CopyLookup(id, (int) id, code, "9788535914849", "Dom Casmurro", "Machado de Assis",
                "AVAILABLE", "DEP", "L1", null);
    }

    @BeforeEach
    void setUp() {
        backend = mock(BackendService.class);
        copyCodes = new CopyCodes(backend);
        when(backend.lookupCopy("C1")).thenReturn(copy(1, "C1"));
        when(backend.lookupCopy("C2")).thenReturn(copy(2, "C2"));
    }

    @Test
    @DisplayName("resolve na ordem lida e ignora a repetição")
    void resolve() {
        List<CopyLookup> copies = copyCodes.resolve("C1\nC2\nC1");
        assertEquals(List.of(1L, 2L), copies.stream().map(CopyLookup::copyId).toList());
        assertEquals(List.of(1L, 2L), copyCodes.ids("C1,C2"));
    }

    @Test
    @DisplayName("lista vazia: pede para ler ao menos um")
    void empty() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> copyCodes.resolve(" "));
        assertEquals("Leia ao menos um exemplar.", ex.getMessage());
    }

    @Test
    @DisplayName("código não encontrado (404): mensagem com o código")
    void notFound() {
        when(backend.lookupCopy("X9")).thenThrow(BackendErrors.status(404));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> copyCodes.resolve("C1\nX9"));
        assertTrue(ex.getMessage().contains("\"X9\" não encontrado"));
    }

    @Test
    @DisplayName("outros erros do backend sobem como estão; resposta vazia é ignorada")
    void otherErrors() {
        when(backend.lookupCopy("X9")).thenThrow(BackendErrors.status(403));
        assertThrows(RestClientResponseException.class, () -> copyCodes.resolve("X9"));
        assertTrue(copyCodes.resolve("NADA").isEmpty());
    }
}
