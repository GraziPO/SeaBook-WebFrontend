package searchoteca.DTO.archive;

/**
 * Dados de uma etiqueta, devolvidos pelo cadastro, pela reimpressão e pela movimentação.
 * copyIndex: o número do exemplar dentro do título ("Exemplar 2"), mostrado no lugar do ID.
 */
public record CopyLabel (
    Long copyId,
    String code,
    String title,
    String author,
    String departCode,
    String localCode,
    Integer copyIndex
    ){}
