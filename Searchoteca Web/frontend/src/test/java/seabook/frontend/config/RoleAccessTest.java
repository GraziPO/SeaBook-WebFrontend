package seabook.frontend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Menu de cada perfil (espelho do permissions.sql do backend). */
class RoleAccessTest {

    @Test
    @DisplayName("root vê tudo")
    void root() {
        RoleAccess r = RoleAccess.of("root");
        assertTrue(r.isRoot());
        assertTrue(r.users() && r.archive() && r.manageArchive() && r.loans() && r.exits()
                && r.audit() && r.editPrice() && r.dashboard());
        assertFalse(r.student());
        assertTrue(r.sales());
    }

    @Test
    @DisplayName("operador de empréstimo: acervo (consulta), empréstimos e saídas; não mexe no cadastro")
    void loanOperator() {
        RoleAccess r = RoleAccess.of("loan_op");
        assertTrue(r.archive() && r.loans() && r.exits());
        assertFalse(r.manageArchive() || r.users() || r.audit() || r.dashboard() || r.isRoot());
    }

    @Test
    @DisplayName("operador de acervo, administrador de usuários e aluno")
    void others() {
        RoleAccess w = RoleAccess.of("warehouse_op");
        assertTrue(w.archive() && w.manageArchive());
        assertFalse(w.loans() || w.exits());
        RoleAccess a = RoleAccess.of("admin_users");
        assertTrue(a.users());
        assertFalse(a.archive());
        RoleAccess s = RoleAccess.of("students");
        assertTrue(s.student());
        assertFalse(s.archive() || s.loans() || s.users());
    }

    @Test
    @DisplayName("sem perfil: nada liberado; vendas dependem do módulo ligado")
    void noRoleAndSales() {
        RoleAccess none = RoleAccess.of(null);
        assertEquals("", none.role());
        assertFalse(none.archive() || none.users() || none.student());
        assertFalse(RoleAccess.of("root", false).sales());
        assertFalse(RoleAccess.of("warehouse_op", true).sales());
        assertEquals("/acervo/consulta", none.homePath());
    }

    @Test
    @DisplayName("nomes dos perfis para a tela")
    void labels() {
        assertEquals("Aluno", RoleAccess.label("students"));
        assertEquals("perfil_novo", RoleAccess.label("perfil_novo"));
        assertEquals("", RoleAccess.label(null));
        assertEquals(5, RoleAccess.LABELS.size());
    }
}
