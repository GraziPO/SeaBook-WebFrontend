package seabook.frontend.dto.archive;

/**
 * Resumo mostrado acima da lista de exemplares (tela depois da nova entrada ou da movimentação).
 * Campos que não se aplicam ficam null (na movimentação, o livro pode variar).
 */
public record LabelSummary(String bookTitle, String author, String isbn,
                           String localCode, String localName,
                           String departCode, String departName,
                           int quantity) {}
