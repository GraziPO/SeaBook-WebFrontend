package seabook.frontend.dto.dashboard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import seabook.frontend.dto.dashboard.DashboardData.CountByStatus;
import seabook.frontend.dto.dashboard.DashboardData.Diary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Gráficos do dashboard desenhados no servidor (SVG): linha de empréstimos/devoluções e rosca dos exemplares. */
class DashboardChartsTest {

    private static List<Diary> days(int n, long loans) {
        List<Diary> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(new Diary(LocalDate.of(2026, 11, 1).plusDays(i), i == n - 1 ? loans : 1, i % 2));
        }
        return list;
    }

    @Test
    @DisplayName("sem dados: gráfico vazio")
    void emptyLine() {
        DashboardCharts.LineChart chart = DashboardCharts.line(List.of());
        assertTrue(chart.empty());
        assertTrue(DashboardCharts.line(null).empty());
        assertTrue(DashboardCharts.line(List.of(new Diary(LocalDate.now(), 0, 0))).empty());
    }

    @Test
    @DisplayName("um dia só: ponto no meio, com o pico marcado")
    void singleDay() {
        DashboardCharts.LineChart chart = DashboardCharts.line(List.of(new Diary(LocalDate.of(2026, 11, 5), 3, 1)));
        assertFalse(chart.empty());
        assertEquals(3, chart.totalLoans());
        assertEquals(1, chart.totalReturns());
        assertEquals(3, chart.peak().value());
        assertEquals(1, chart.labels().size());
        assertEquals("05/11", chart.labels().get(0).text());
        assertTrue(chart.days().get(0).tip().contains("3 empréstimos"));
        assertTrue(chart.days().get(0).tip().contains("1 devolução"));
    }

    @Test
    @DisplayName("30 dias: totais, pico, escala 'redonda', poucas legendas no eixo X e uma área por dia")
    void month() {
        DashboardCharts.LineChart chart = DashboardCharts.line(days(30, 37));
        assertEquals(29 + 37, chart.totalLoans());
        assertEquals(37, chart.peak().value());
        assertEquals(40, chart.ticks().get(2).value());   // 37 -> 40
        assertEquals(20, chart.ticks().get(1).value());
        assertTrue(chart.labels().size() <= 6);
        assertEquals("end", chart.labels().get(chart.labels().size() - 1).anchor());
        assertEquals("start", chart.labels().get(0).anchor());
        assertEquals(30, chart.days().size());
        assertTrue(chart.loansArea().startsWith("36,196.0"));
        assertTrue(chart.days().get(0).tip().contains("1 empréstimo ·"));
    }

    @Test
    @DisplayName("escala do eixo: 4, 6, 10 e múltiplos de 1-2-3-4-5-6-8-10")
    void niceCeil() {
        assertEquals(4, DashboardCharts.niceCeil(1));
        assertEquals(6, DashboardCharts.niceCeil(5));
        assertEquals(10, DashboardCharts.niceCeil(9));
        assertEquals(20, DashboardCharts.niceCeil(11));
        assertEquals(80, DashboardCharts.niceCeil(75));
        assertEquals(100, DashboardCharts.niceCeil(81));
        assertEquals(1000, DashboardCharts.niceCeil(999));
    }

    @Test
    @DisplayName("rosca: fatias com percentual e cor; status desconhecido e quantidade zero")
    void donut() {
        DashboardCharts.Donut donut = DashboardCharts.donut(List.of(
                new CountByStatus("AVAILABLE", 3), new CountByStatus("LOANED", 1),
                new CountByStatus("SOLD", 0), new CountByStatus("PERDIDO", 4)));
        assertEquals(8, donut.total());
        assertFalse(donut.isEmpty());
        assertEquals(3, donut.slices().size());
        assertEquals("Disponível", donut.slices().get(0).label());
        assertEquals(38, donut.slices().get(0).percent());
        assertEquals("PERDIDO", donut.slices().get(2).label());
        assertTrue(DashboardCharts.donut(null).isEmpty());
        assertTrue(DashboardCharts.donut(List.of(new CountByStatus("AVAILABLE", 0))).isEmpty());
        assertEquals("Emprestado", DashboardCharts.statusLabel("LOANED"));
        assertEquals("X", DashboardCharts.statusLabel("X"));
    }

    @Test
    @DisplayName("dados do dashboard: vazio, movimentações, vendas, ocupação e gravidade do atraso")
    void data() {
        DashboardData empty = DashboardData.empty(30);
        assertEquals(30, empty.periodDays());
        assertEquals(BigDecimal.ZERO, empty.kpis().sales());
        DashboardData.Kpis k = new DashboardData.Kpis(1, 0, 0, 0, 0, 0, 0, 2, 3, 4, null, 0);
        assertEquals(9, k.movementsInPeriod());
        assertEquals(BigDecimal.ZERO, k.sales());
        assertEquals(new BigDecimal("10"), new DashboardData.Kpis(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, new BigDecimal("10"), 0).sales());

        assertEquals(0, new DashboardData.DepartmentOccupation("D", 0, 0).percent());
        assertEquals(33, new DashboardData.DepartmentOccupation("D", 1, 3).percent());

        assertEquals(1, new DashboardData.DelayedLoan(1, "", "", "", null, 3).severity());
        assertEquals(2, new DashboardData.DelayedLoan(1, "", "", "", null, 7).severity());
        assertEquals(3, new DashboardData.DelayedLoan(1, "", "", "", null, 15).severity());
    }
}
