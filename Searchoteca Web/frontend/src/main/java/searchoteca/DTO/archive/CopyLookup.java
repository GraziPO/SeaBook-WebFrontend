package searchoteca.DTO.archive;

import java.math.BigDecimal;

/**
 * Um exemplar encontrado pelo código da etiqueta (lista de leitura do frontend:
 * baixa, venda, empréstimo, devolução, movimentação e reimpressão).
 */
public record CopyLookup(
        Long copyId,
        Integer copyIndex,
        String code,
        String isbn,
        String title,
        String author,
        String status,
        String departCode,
        String localCode,
        BigDecimal salePrice) {
}
