package seabook.frontend.dto.archive;

import java.util.List;

/** Corpo de POST /api/exemplares/movimentacao/quantidade: quantos exemplares de cada livro saem da origem. */
public record QuantityMoveRequest(String fromLocalCode, String toLocalCode, List<Item> items) {

    public record Item(String isbn, Integer quantity) {}
}
