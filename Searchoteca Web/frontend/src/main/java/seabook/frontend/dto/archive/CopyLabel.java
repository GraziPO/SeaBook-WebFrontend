package seabook.frontend.dto.archive;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Dados de uma etiqueta, devolvidos pelo cadastro, pela reimpressão e pela movimentação.
 * copyIndex é o número do exemplar dentro do livro ("Exemplar 2"), que aparece no lugar do ID.
 * salePrice: preço de venda (null se o livro não tiver). A etiqueta só mostra com vendas ligadas.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CopyLabel(Long copyId, String code, String title, String author,
                        String departCode, String localCode, Integer copyIndex, BigDecimal salePrice) {

    /** Etiqueta sem preço. */
    public CopyLabel(Long copyId, String code, String title, String author,
                     String departCode, String localCode, Integer copyIndex) {
        this(copyId, code, title, author, departCode, localCode, copyIndex, null);
    }
}
