package seabook.frontend.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import seabook.frontend.dto.ModalReopen;
import seabook.frontend.dto.archive.BatchMoveResult;
import seabook.frontend.dto.archive.CopyLabel;
import seabook.frontend.dto.archive.CopyLookup;
import seabook.frontend.dto.archive.LabelSummary;
import seabook.frontend.dto.archive.LocationBookStock;
import seabook.frontend.dto.archive.MovedLine;
import seabook.frontend.dto.archive.QuantityMoveRequest;
import seabook.frontend.dto.archive.QuantityMoveResult;
import seabook.frontend.model.Book;
import seabook.frontend.service.BackendService;
import seabook.frontend.support.BackendErrors;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exemplares (cadastro, etiquetas, reimpressão) e Movimentações (avulsa e em lote). */
class CopyAndMovementControllersTest {

    private BackendService backend;
    private CopyCodes copyCodes;
    private RedirectAttributesModelMap flash;
    private ExtendedModelMap model;

    @BeforeEach
    void setUp() {
        backend = mock(BackendService.class);
        copyCodes = mock(CopyCodes.class);
        flash = new RedirectAttributesModelMap();
        model = new ExtendedModelMap();
    }

    private Object flash(String key) {
        return flash.getFlashAttributes().get(key);
    }

    private static CopyLookup copy(long id, int index, String isbn, String local, String status) {
        return new CopyLookup(id, index, "C" + id, isbn, "Livro " + isbn, "Autor", status, "DEP", local, null);
    }

    private static CopyLabel label(String code) {
        return new CopyLabel(1L, code, "Livro", "Autor", "DEP", "L2", 1);
    }

    // ------------------------------------------------------------------ exemplares

    @Test
    @DisplayName("cadastro de exemplares: usa o departamento da localização e abre a lista das etiquetas novas")
    void createCopies() {
        CopyController c = new CopyController(backend, copyCodes);
        assertEquals("redirect:/acervo/livros", c.page());
        when(backend.getLocal("L1")).thenReturn(ArchiveControllersTest.local("L1", "BIB"));
        when(backend.createCopies(any())).thenReturn(List.of(label("A1"), label("A2")));

        String view = c.create(" 111 ", 2, "L1", null, flash);

        assertEquals("redirect:/acervo/exemplares/etiquetas?modo=entrada&codigos=A1,A2", view);
        verify(backend).createCopies(argThat(r -> r.isbn().equals("111") && "BIB".equals(r.departCode()) && r.quantity() == 2));
        assertTrue(((String) flash("info")).startsWith("2 exemplares cadastrados."));

        when(backend.createCopies(any())).thenReturn(List.of(label("A3")));
        c.create("111", 1, "L1", null, flash);
        assertTrue(((String) flash("info")).startsWith("1 exemplar cadastrado."));
    }

    @Test
    @DisplayName("cadastro recusado: reabre o modal com o nome do livro (ou 'Livro' se não achar)")
    void createCopiesRefused() {
        CopyController c = new CopyController(backend, copyCodes);
        when(backend.createCopies(any())).thenThrow(BackendErrors.status(409, "Localização não pertence ao departamento"));
        Book book = new Book();
        book.setTitle("Dom Casmurro");
        when(backend.getBook("111")).thenReturn(book);

        assertEquals("redirect:/acervo/livros", c.create("111", 3, "L1", null, flash));
        assertEquals("Localização não pertence ao departamento", flash("erroModal"));
        ModalReopen reopen = (ModalReopen) flash("reabrirModal");
        assertEquals("Dom Casmurro", reopen.message());
        assertEquals("3", reopen.fields().get("quantity"));

        when(backend.getBook("222")).thenThrow(BackendErrors.status(404));
        c.create("222", 1, "L1", null, flash);
        assertEquals("Livro", ((ModalReopen) flash("reabrirModal")).message());
    }

    @Test
    @DisplayName("leitura de etiqueta pela tela: vazio 404, achado 200, erro com o status do backend, 401 sobe")
    void lookup() {
        CopyController c = new CopyController(backend, copyCodes);
        assertEquals(404, c.lookup(" ").getStatusCode().value());
        when(backend.lookupCopy("C1")).thenReturn(copy(1, 1, "111", "L1", "AVAILABLE"));
        assertEquals("C1", c.lookup("C1").getBody().code());
        when(backend.lookupCopy("X")).thenThrow(BackendErrors.status(404));
        assertEquals(404, c.lookup("X").getStatusCode().value());
        when(backend.lookupCopy("Y")).thenThrow(BackendErrors.status(401));
        assertThrows(RestClientResponseException.class, () -> c.lookup("Y"));
    }

    @Test
    @DisplayName("etiquetas: sem códigos mostra a tela vazia; modo desconhecido vira reimpressão")
    void labelsEmpty() {
        CopyController c = new CopyController(backend, copyCodes);
        assertEquals("pages/etiquetas", c.labels(null, "qualquer", null, null, model, flash));
        assertEquals("reimpressao", model.get("modo"));
        assertEquals("Reimpressão de etiquetas", model.get("titulo"));
        assertEquals(List.of(), model.get("etiquetas"));
    }

    @Test
    @DisplayName("etiquetas da entrada: em ordem de cópia, com o resumo do livro e do local")
    void labelsEntry() {
        CopyController c = new CopyController(backend, copyCodes);
        when(copyCodes.resolve("C2,C1")).thenReturn(List.of(copy(2, 2, "111", "L1", "AVAILABLE"), copy(1, 1, "111", "L1", "AVAILABLE")));
        when(backend.findLabels(List.of(1L, 2L))).thenReturn(List.of(label("C1"), label("C2")));
        when(backend.getLocal("L1")).thenReturn(ArchiveControllersTest.local("L1", "DEP"));
        when(backend.getDepart("DEP")).thenReturn(ArchiveControllersTest.depart("DEP"));

        c.labels("C2,C1", "entrada", null, null, model, flash);

        List<?> copies = (List<?>) model.get("exemplares");
        assertEquals(1, ((CopyLookup) copies.get(0)).copyIndex());
        LabelSummary summary = (LabelSummary) model.get("resumo");
        assertEquals("Livro 111", summary.bookTitle());
        assertEquals("Estante L1", summary.localName());
        assertEquals("Dep DEP", summary.departName());
        assertEquals(2, summary.quantity());
        assertEquals("Exemplares cadastrados", model.get("titulo"));
    }

    @Test
    @DisplayName("etiquetas da movimentação: linhas 'cópias 4 e 3 movimentadas' por livro e volta para a tela certa")
    void labelsMovement() {
        CopyController c = new CopyController(backend, copyCodes);
        when(copyCodes.resolve(anyString())).thenReturn(List.of(
                copy(4, 4, "111", "L2", "AVAILABLE"), copy(3, 3, "111", "L2", "AVAILABLE"), copy(9, 1, "222", "L2", "AVAILABLE")));
        when(backend.getLocal(any())).thenThrow(BackendErrors.offline());

        c.labels("C4,C3,C9", "movimentacao", " L1 ", "avulsa", model, flash);

        List<?> lines = (List<?>) model.get("movidos");
        assertEquals("cópias 4 e 3 movimentadas", ((MovedLine) lines.get(0)).text());
        assertEquals("cópia 1 movimentada", ((MovedLine) lines.get(1)).text());
        assertEquals("L1", model.get("origem"));
        assertEquals("/acervo/movimentacoes/avulsa", model.get("voltarUrl"));
        LabelSummary summary = (LabelSummary) model.get("resumo");
        assertNull(summary.bookTitle());   // livros diferentes: sem título único
        assertNull(summary.localName());   // backend fora: resumo sem os nomes

        c.labels("C4", "movimentacao", null, "lote", model, flash);
        assertEquals("/acervo/movimentacoes/lote", model.get("voltarUrl"));
        assertNull(model.get("origem"));
    }

    @Test
    @DisplayName("agrupamento das linhas movidas: livro sem ISBN usa o título")
    void movedLines() {
        List<MovedLine> lines = CopyController.movedLines(List.of(
                new CopyLookup(1L, 2, "C1", null, "Sem ISBN", "A", "AVAILABLE", "D", "L", null)));
        assertEquals("Sem ISBN", lines.get(0).title());
    }

    @Test
    @DisplayName("etiquetas com código inválido: reimpressão reabre o modal; entrada/movimentação mostram o erro")
    void labelsErrors() {
        CopyController c = new CopyController(backend, copyCodes);
        when(copyCodes.resolve(anyString())).thenThrow(new IllegalArgumentException("Exemplar \"X\" não encontrado."));
        assertEquals("redirect:/acervo/livros", c.labels("X\nY", "reimpressao", null, null, model, flash));
        assertEquals("X\nY", ((ModalReopen) flash("reabrirModal")).fields().get("codigos"));

        assertEquals("redirect:/acervo/movimentacoes", c.labels("X", "movimentacao", null, null, model, flash));
        assertEquals("Exemplar \"X\" não encontrado.", flash("erro"));

        reset(copyCodes);
        when(copyCodes.resolve(anyString())).thenThrow(BackendErrors.status(500));
        assertEquals("redirect:/acervo/livros", c.labels("X", "entrada", null, null, model, flash));
        assertEquals("Não foi possível carregar os exemplares.", flash("erro"));
    }

    // ------------------------------------------------------------------ movimentação avulsa

    @Test
    @DisplayName("avulsa: telas e movimentação certa (código em maiúsculas, lista das etiquetas novas)")
    void singleOk() {
        MovementController c = new MovementController(backend);
        assertEquals("pages/movimentacoes", c.home());
        when(backend.listLocal()).thenThrow(BackendErrors.offline());
        assertEquals("pages/movimentacao-avulsa", c.single(model));
        assertEquals(List.of(), model.get("localizacoes"));

        when(backend.lookupCopy("C4")).thenReturn(copy(4, 4, "111", "L1", "AVAILABLE"));
        when(backend.moveCopies(any())).thenReturn(new BatchMoveResult("L2", 1, List.of(label("C4N")), List.of()));

        String view = c.moveSingle(" c4 ", "L2", flash);

        assertEquals("redirect:/acervo/exemplares/etiquetas?modo=movimentacao&codigos=C4N&de=L1&tipo=avulsa", view);
        assertEquals("Livro 111 — cópia 4 movimentada de L1 para L2.", flash("info"));
    }

    @Test
    @DisplayName("avulsa: regras — etiqueta e destino obrigatórios, já está no destino, só exemplar disponível")
    void singleRules() {
        MovementController c = new MovementController(backend);
        c.moveSingle(" ", "L2", flash);
        assertEquals("Leia a etiqueta do exemplar.", flash("erro"));
        c.moveSingle("C1", " ", flash);
        assertEquals("Escolha a nova localização.", flash("erro"));
        assertEquals("C1", flash("codigoAnterior"));

        when(backend.lookupCopy("C1")).thenReturn(copy(1, 1, "111", "L2", "AVAILABLE"));
        c.moveSingle("C1", "L2", flash);
        assertEquals("Esse exemplar já está em L2.", flash("erro"));

        when(backend.lookupCopy("C2")).thenReturn(copy(2, 2, "111", "L1", "LOANED"));
        c.moveSingle("C2", "L2", flash);
        assertTrue(((String) flash("erro")).contains("(este está emprestado)"));
        for (String[] s : new String[][]{{"RESERVED", "reservado"}, {"SOLD", "vendido"}, {"DONATED", "doado"},
                {"WRITTEN_OFF", "baixado"}, {"OUTRO", "OUTRO"}}) {
            when(backend.lookupCopy("C3")).thenReturn(copy(3, 3, "111", "L1", s[0]));
            c.moveSingle("C3", "L2", flash);
            assertTrue(((String) flash("erro")).contains("(este está " + s[1] + ")"));
        }
        when(backend.lookupCopy("C5")).thenReturn(copy(5, 5, "111", "L1", null));
        c.moveSingle("C5", "L2", flash);
        assertTrue(((String) flash("erro")).contains("fora da estante"));
        verify(backend, never()).moveCopies(any());
    }

    @Test
    @DisplayName("avulsa: etiqueta inexistente, backend recusando e nada movido")
    void singleErrors() {
        MovementController c = new MovementController(backend);
        when(backend.lookupCopy("X")).thenThrow(BackendErrors.status(404));
        assertEquals("redirect:/acervo/movimentacoes/avulsa", c.moveSingle("X", "L2", flash));
        assertEquals("Exemplar \"X\" não encontrado. Confira a etiqueta.", flash("erro"));

        when(backend.lookupCopy("N")).thenReturn(null);
        c.moveSingle("N", "L2", flash);
        assertEquals("Exemplar \"N\" não encontrado. Confira a etiqueta.", flash("erro"));

        when(backend.lookupCopy("F")).thenThrow(BackendErrors.status(403, "Sem permissão"));
        c.moveSingle("F", "L2", flash);
        assertEquals("Sem permissão", flash("erro"));

        when(backend.lookupCopy("C1")).thenReturn(copy(1, 1, "111", "L1", "AVAILABLE"));
        when(backend.moveCopies(any())).thenReturn(new BatchMoveResult("L2", 0, null, List.of("C1")));
        c.moveSingle("C1", "L2", flash);
        assertEquals("Esse exemplar já está em L2.", flash("erro"));
    }

    // ------------------------------------------------------------------ movimentação em lote

    @Test
    @DisplayName("lote: busca de estantes e conteúdo da estante (só livros com exemplar disponível; destino exclui a origem)")
    void batchPages() {
        MovementController c = new MovementController(backend);
        assertEquals("pages/movimentacao-lote", c.batch("  est ", 1, model));
        verify(backend).locationShelves("est", 1);
        assertEquals("/acervo/movimentacoes/lote?busca=est", model.get("paginaUrl"));
        c.batch(null, 0, model);
        assertEquals("", model.get("busca"));

        when(backend.getLocal("L1")).thenReturn(ArchiveControllersTest.local("L1", "BIB"));
        when(backend.getDepart("BIB")).thenReturn(ArchiveControllersTest.depart("BIB"));
        when(backend.localBooksStock("L1")).thenReturn(List.of(
                new LocationBookStock("111", "A", "x", null, 3, 2, 1, 0),
                new LocationBookStock("222", "B", "x", null, 1, 0, 1, 0)));
        when(backend.listLocal()).thenReturn(List.of(ArchiveControllersTest.local("L1", "BIB"), ArchiveControllersTest.local("L2", "BIB")));

        assertEquals("pages/movimentar-conteudo", c.content("L1", model));
        assertEquals(1, ((List<?>) model.get("livros")).size());
        assertEquals(2L, model.get("naEstante"));
        assertEquals(1, ((List<?>) model.get("destinos")).size());
        assertEquals("Dep BIB", model.get("departamento"));

        when(backend.getLocal("L3")).thenReturn(ArchiveControllersTest.local("L3", null));
        when(backend.localBooksStock("L3")).thenReturn(List.of());
        c.content("L3", model);
        assertNull(model.get("departamento"));

        when(backend.getLocal("L4")).thenReturn(ArchiveControllersTest.local("L4", "Q"));
        when(backend.getDepart("Q")).thenThrow(BackendErrors.offline());
        when(backend.localBooksStock("L4")).thenReturn(List.of());
        c.content("L4", model);
        assertNull(model.get("departamento"));
    }

    @Test
    @DisplayName("lote: envia só as quantidades preenchidas e mostra as etiquetas novas")
    void batchOk() {
        MovementController c = new MovementController(backend);
        when(backend.moveByQuantity(any())).thenReturn(new QuantityMoveResult("L1", "L2", 3,
                List.of(), List.of(label("N1"), label("N2"), label("N3"))));

        String view = c.moveContent("L1", "L2", List.of("111", "222", "333"), List.of("2", "", " 1 "), flash);

        assertEquals("redirect:/acervo/exemplares/etiquetas?modo=movimentacao&codigos=N1,N2,N3&de=L1&tipo=lote", view);
        verify(backend).moveByQuantity(argThat(r -> r.items().equals(List.of(
                new QuantityMoveRequest.Item("111", 2), new QuantityMoveRequest.Item("333", 1)))));
        assertTrue(((String) flash("info")).startsWith("3 exemplares movimentados de L1 para L2."));

        when(backend.moveByQuantity(any())).thenReturn(new QuantityMoveResult("L1", "L2", 1, List.of(), null));
        assertEquals("redirect:/acervo/movimentacoes/lote", c.moveContent("L1", "L2", List.of("111"), List.of("1"), flash));
        assertTrue(((String) flash("info")).startsWith("1 exemplar movimentado de"));
    }

    @Test
    @DisplayName("lote: regras — destino obrigatório, ao menos uma quantidade, sem negativos nem texto; mantém o que foi digitado")
    @SuppressWarnings("unchecked")
    void batchRules() {
        MovementController c = new MovementController(backend);
        c.moveContent("L1", " ", List.of("111"), List.of("1"), flash);
        assertEquals("Escolha a nova localização.", flash("erro"));
        assertEquals(Map.of("111", 1), flash("quantidadesAnteriores"));

        c.moveContent("L1", "L2", List.of("111"), List.of("0"), flash);
        assertEquals("Informe a quantidade de ao menos um livro.", flash("erro"));
        c.moveContent("L1", "L2", null, null, flash);
        assertEquals("Informe a quantidade de ao menos um livro.", flash("erro"));
        c.moveContent("L1", " ", List.of("111", "222"), List.of("1"), flash);   // quantidade faltando conta como 0
        assertEquals(0, ((Map<String, Integer>) flash("quantidadesAnteriores")).get("222"));

        assertEquals("redirect:/acervo/movimentacoes/lote/L1", c.moveContent("L1", "L2", List.of("111"), List.of("-2"), flash));
        assertEquals("A quantidade não pode ser negativa.", flash("erro"));
        c.moveContent("L1", "L2", List.of("111"), List.of("dois"), flash);
        assertEquals("Quantidade inválida: \"dois\".", flash("erro"));

        reset(backend);
        when(backend.moveByQuantity(any())).thenThrow(BackendErrors.status(409, "Só há 1 exemplar disponível de Livro"));
        c.moveContent("L1", "L2", List.of("111"), List.of("5"), flash);
        assertEquals("Só há 1 exemplar disponível de Livro", flash("erro"));
        assertEquals("L2", flash("destinoAnterior"));
    }
}
