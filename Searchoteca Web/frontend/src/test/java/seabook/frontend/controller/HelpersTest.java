package seabook.frontend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Leitura da lista de códigos, endereço da paginação e proteção do "returnTo". */
class HelpersTest {

    @Test
    @DisplayName("lista de leitura aceita Enter, vírgula, ponto e vírgula e espaço")
    void codes() {
        assertEquals(List.of("A1", "B2", "C3", "D4"), FormParsing.codes("A1\nB2, C3;  D4\r\n"));
        assertEquals(List.of(), FormParsing.codes(null));
        assertEquals(List.of(), FormParsing.codes("  \n ,; "));
        assertEquals("A1\nB2", FormParsing.join(List.of("A1", "B2")));
    }

    @Test
    @DisplayName("número opcional: vazio ou texto viram null")
    void intOrNull() {
        assertEquals(1999, FormParsing.intOrNull(" 1999 "));
        assertNull(FormParsing.intOrNull(null));
        assertNull(FormParsing.intOrNull(" "));
        assertNull(FormParsing.intOrNull("mil"));
    }

    @Test
    @DisplayName("paginação mantém só os filtros preenchidos, codificados")
    void pages() {
        assertEquals("/acervo/consulta?q=dom%20casmurro", Pages.url("/acervo/consulta", "q", " dom casmurro "));
        assertEquals("/emprestimos", Pages.url("/emprestimos", "status", "", "x", null));
        assertEquals("/a?b=1", Pages.url("/a", "b", "1", "sem-valor"));
    }

    @Test
    @DisplayName("returnTo: só caminhos internos; o resto cai no padrão")
    void redirects() {
        assertEquals("redirect:/acervo/livros?page=2", Redirects.back("/acervo/livros?page=2", "/x"));
        assertEquals("redirect:/x", Redirects.back(null, "/x"));
        assertEquals("redirect:/x", Redirects.back("https://site.com", "/x"));
        assertEquals("redirect:/x", Redirects.back("//site.com", "/x"));
        assertEquals("redirect:/x", Redirects.back("/\\site.com", "/x"));
        assertEquals("redirect:/x", Redirects.back("/a\r\nSet-Cookie: x", "/x"));
        assertEquals("redirect:/x", Redirects.back("relativo", "/x"));
    }
}
