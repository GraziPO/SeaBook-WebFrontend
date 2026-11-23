package seabook.frontend.dto.archive;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Resposta da movimentação por quantidade: o backend escolhe quais exemplares foram e devolve,
 * por livro, os números deles ("Dom Casmurro — cópias 4 e 3") e os exemplares movimentados
 * (o código não muda com a localização, então as etiquetas continuam as mesmas).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QuantityMoveResult(String fromLocalCode, String toLocalCode, int moved,
                                 List<MovedBook> books, List<CopyLabel> labels) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MovedBook(String isbn, String title, List<Integer> copyIndexes) {}
}
