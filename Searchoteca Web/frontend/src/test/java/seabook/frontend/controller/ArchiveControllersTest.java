package seabook.frontend.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import seabook.frontend.dto.ModalReopen;
import seabook.frontend.dto.archive.LocationBookStock;
import seabook.frontend.model.Book;
import seabook.frontend.model.Department;
import seabook.frontend.model.Location;
import seabook.frontend.service.BackendService;
import seabook.frontend.support.BackendErrors;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Livros, departamentos e localizações: telas, cadastros e o que acontece quando o backend recusa. */
class ArchiveControllersTest {

    private BackendService backend;
    private RedirectAttributesModelMap flash;
    private ExtendedModelMap model;

    @BeforeEach
    void setUp() {
        backend = mock(BackendService.class);
        flash = new RedirectAttributesModelMap();
        model = new ExtendedModelMap();
    }

    private Object flash(String key) {
        return flash.getFlashAttributes().get(key);
    }

    static Department depart(String code) {
        Department d = new Department();
        d.setDepartCode(code);
        d.setDepartName("Dep " + code);
        return d;
    }

    static Location local(String code, String depart) {
        Location l = new Location();
        l.setLocalCode(code);
        l.setLocalName("Estante " + code);
        l.setDepartCode(depart);
        return l;
    }

    // ------------------------------------------------------------------ livros

    @Test
    @DisplayName("lista de livros: localizações carregadas; se falharem, a lista abre mesmo assim; 401 sobe")
    void bookList() {
        BookController c = new BookController(backend);
        when(backend.listLocal()).thenReturn(List.of(local("L1", "D")));
        when(backend.salesEnabled()).thenReturn(true);
        assertEquals("pages/livros", c.list(0, model));
        assertEquals(1, ((List<?>) model.get("localizacoes")).size());
        assertEquals(true, model.get("vendasAtivas"));

        reset(backend);
        when(backend.listLocal()).thenThrow(BackendErrors.status(500));
        c.list(0, model);
        assertEquals(List.of(), model.get("localizacoes"));

        reset(backend);
        when(backend.listLocal()).thenThrow(BackendErrors.offline());
        c.list(0, model);
        assertEquals(List.of(), model.get("localizacoes"));

        reset(backend);
        when(backend.listLocal()).thenThrow(BackendErrors.status(401));
        assertThrows(RestClientResponseException.class, () -> c.list(0, model));
    }

    @Test
    @DisplayName("cadastro de livro: campos aparados, ano opcional; recusa reabre o modal com o que foi digitado")
    void bookCreate() {
        BookController c = new BookController(backend);
        assertEquals("redirect:/acervo/livros", c.create(" 9788535914849 ", " Dom Casmurro ", " Machado ", " ", "1899", "", null, flash));
        verify(backend).createBook(argThat(b -> b.getIsbn().equals("9788535914849") && b.getRel_year() == 1899
                && b.getPublisher() == null && b.getGenre() == null));
        assertTrue(((String) flash("info")).contains("Dom Casmurro"));

        when(backend.createBook(any())).thenThrow(BackendErrors.status(409, "ISBN já cadastrado"));
        c.create("9788535914849", "Dom Casmurro", "Machado", "Globo", "ano?", "Romance", "/acervo/livros?page=2", flash);
        assertEquals("ISBN já cadastrado", flash("erroModal"));
        ModalReopen reopen = (ModalReopen) flash("reabrirModal");
        assertEquals("modal-livro", reopen.modal());
        assertEquals("Globo", reopen.fields().get("publisher"));
        assertNull(reopen.fields().get("rel_year"));   // ano inválido não volta
    }

    @Test
    @DisplayName("edição de livro: ISBN não muda; recusa reabre com o ISBN só leitura")
    void bookUpdate() {
        BookController c = new BookController(backend);
        c.update("111", "Título", "Autor", null, "2000", null, null, flash);
        verify(backend).updateBook(eq("111"), argThat(b -> b.getRel_year() == 2000));
        assertEquals("Livro \"Título\" atualizado.", flash("info"));

        when(backend.updateBook(eq("111"), any())).thenThrow(BackendErrors.status(400));
        c.update("111", "Título", "Autor", null, null, null, null, flash);
        ModalReopen reopen = (ModalReopen) flash("reabrirModal");
        assertEquals("isbn", reopen.readonly());
        assertEquals("/acervo/livros/111/editar", reopen.action());
        assertEquals("Não foi possível salvar o livro.", flash("erroModal"));
    }

    @Test
    @DisplayName("preço: vírgula aceita, vazio tira da venda, texto inválido avisa, erro do backend aparece")
    void bookPrice() {
        BookController c = new BookController(backend);
        c.price("111", "35,5", null, flash);
        verify(backend).updatePrice("111", new BigDecimal("35.5"));
        assertEquals("Preço atualizado para R$ 35,50.", flash("info"));

        c.price("111", " ", null, flash);
        verify(backend).updatePrice("111", null);
        assertEquals("Livro retirado da venda.", flash("info"));

        c.price("111", "trinta", null, flash);
        assertEquals("Preço inválido. Use números, por exemplo 35,00.", flash("erro"));

        when(backend.updatePrice(eq("222"), any())).thenThrow(BackendErrors.status(409, "Preço deve ser maior que zero"));
        c.price("222", "-1", null, flash);
        assertEquals("Preço deve ser maior que zero", flash("erro"));
    }

    @Test
    @DisplayName("exclusão de livro e consulta do ISBN pela tela")
    void bookDeleteAndLookup() {
        BookController c = new BookController(backend);
        c.delete("111", null, flash);
        assertEquals("Livro 111 excluído.", flash("info"));
        doThrow(BackendErrors.status(409, "O livro tem exemplares")).when(backend).deleteBook("222");
        c.delete("222", null, flash);
        assertEquals("O livro tem exemplares", flash("erro"));

        when(backend.lookupIsbn("111")).thenReturn(ResponseEntity.ok(Map.of("title", "x")));
        assertEquals("x", c.lookupIsbn("111").getBody().get("title"));
    }

    // ------------------------------------------------------------------ departamentos

    @Test
    @DisplayName("departamentos: nenhum cadastrado")
    void departmentsEmpty() {
        when(backend.publicDepartments()).thenReturn(List.of());
        assertEquals("pages/departamentos", new DepartmentController(backend).list(null, model));
        assertNull(model.get("departamento"));
        assertEquals(List.of(), model.get("locais"));
    }

    @Test
    @DisplayName("departamentos: em ordem de código, com anterior/próximo e só as localizações do escolhido")
    void departmentsNavigation() {
        when(backend.publicDepartments()).thenReturn(List.of(depart("C"), depart("a"), depart("B")));
        when(backend.publicLocations()).thenReturn(List.of(local("L2", "B"), local("L1", "B"), local("X", "C")));
        DepartmentController c = new DepartmentController(backend);

        c.list(" b ", model);
        assertEquals("B", ((Department) model.get("departamento")).getDepartCode());
        assertEquals("a", model.get("anterior"));
        assertEquals("C", model.get("proximo"));
        List<?> locais = (List<?>) model.get("locais");
        assertEquals("L1", ((Location) locais.get(0)).getLocalCode());

        c.list("nao-existe", model);
        assertEquals("a", ((Department) model.get("departamento")).getDepartCode());
        assertNull(model.get("anterior"));

        assertEquals("redirect:/acervo/departamentos?d=B", c.detail("B"));
    }

    @Test
    @DisplayName("departamento: cadastrar, editar (código fixo) e excluir, com e sem erro")
    void departmentWrites() {
        DepartmentController c = new DepartmentController(backend);
        Department d = depart("BIB");
        assertEquals("redirect:/acervo/departamentos?d=BIB", c.create(d, null, flash));

        when(backend.createDepart(any())).thenThrow(BackendErrors.status(409, "Código já existe"));
        assertEquals("redirect:/acervo/departamentos", c.create(d, null, flash));
        assertEquals("Código já existe", flash("erroModal"));

        Department edit = depart("OUTRO");
        c.update("BIB", edit, null, flash);
        assertEquals("BIB", edit.getDepartCode());
        when(backend.updateDepart(eq("BIB"), any())).thenThrow(BackendErrors.status(400));
        assertEquals("redirect:/acervo/departamentos?d=BIB", c.update("BIB", edit, null, flash));
        assertEquals("departCode", ((ModalReopen) flash("reabrirModal")).readonly());

        assertEquals("redirect:/acervo/departamentos", c.delete("BIB", null, flash));
        doThrow(BackendErrors.status(409, "Tem localizações")).when(backend).deleteDepart("X");
        assertEquals("redirect:/acervo/departamentos?d=X", c.delete("X", null, flash));
        assertEquals("Tem localizações", flash("erro"));
    }

    // ------------------------------------------------------------------ localizações

    @Test
    @DisplayName("localizações: endereços antigos levam ao departamento; detalhe parcial com o estoque")
    void locationPages() {
        LocationController c = new LocationController(backend);
        assertEquals("redirect:/acervo/departamentos", c.list());
        when(backend.publicLocal("L1")).thenReturn(local("L1", "BIB"));
        assertEquals("redirect:/acervo/departamentos?d=BIB", c.detailPage("L1"));
        when(backend.publicLocal("L9")).thenThrow(BackendErrors.status(404));
        assertEquals("redirect:/acervo/departamentos", c.detailPage("L9"));

        when(backend.publicDepart("BIB")).thenReturn(depart("BIB"));
        when(backend.publicLocalBooks("L1")).thenReturn(List.of(new LocationBookStock("111", "L", "A", null, 3, 2, 1, 0)));
        assertEquals("pages/localizacao-parcial", c.details("L1", model));
        assertEquals("Dep BIB", ((Department) model.get("departamento")).getDepartName());

        when(backend.publicLocal("L2")).thenReturn(local("L2", "ZZ"));
        when(backend.publicDepart("ZZ")).thenThrow(BackendErrors.offline());
        when(backend.publicLocalBooks("L2")).thenReturn(null);
        c.details("L2", model);
        assertNull(model.get("departamento"));
        assertEquals(List.of(), model.get("livros"));
    }

    @Test
    @DisplayName("localização: adicionar (volta ao departamento), editar e excluir, com e sem erro")
    void locationWrites() {
        LocationController c = new LocationController(backend);
        Location l = local("L1", "BIB");
        assertEquals("redirect:/acervo/departamentos?d=BIB", c.create(l, null, flash));
        assertEquals("Localização L1 adicionada.", flash("info"));

        when(backend.createLocal(any())).thenThrow(BackendErrors.status(409, "Já existe"));
        when(backend.getDepart("BIB")).thenReturn(depart("BIB"));
        c.create(l, null, flash);
        assertEquals("BIB — Dep BIB", ((ModalReopen) flash("reabrirModal")).message());

        Location semDep = local("L5", null);
        c.create(semDep, null, flash);
        assertNull(((ModalReopen) flash("reabrirModal")).message());

        Location outroDep = local("L6", "Q");
        when(backend.getDepart("Q")).thenThrow(BackendErrors.offline());
        c.create(outroDep, null, flash);
        assertEquals("Q", ((ModalReopen) flash("reabrirModal")).message());

        Location edit = local("MUDOU", "BIB");
        c.update("L1", edit, null, flash);
        assertEquals("L1", edit.getLocalCode());
        when(backend.updateLocal(eq("L1"), any())).thenThrow(BackendErrors.status(400));
        c.update("L1", edit, null, flash);
        assertEquals("localCode", ((ModalReopen) flash("reabrirModal")).readonly());

        c.delete("L1", null, flash);
        assertEquals("Localização L1 excluída.", flash("info"));
        doThrow(BackendErrors.status(409, "Tem exemplares")).when(backend).deleteLocal("L2");
        c.delete("L2", null, flash);
        assertEquals("Tem exemplares", flash("erro"));
    }
}
