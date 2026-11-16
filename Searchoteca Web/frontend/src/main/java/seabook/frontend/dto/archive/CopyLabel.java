package seabook.frontend.dto.archive;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Dados de uma etiqueta, devolvidos pelo cadastro, pela reimpressão e pela movimentação.
 * copyIndex é o número do exemplar dentro do livro ("Exemplar 2"), que aparece no lugar do ID.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CopyLabel(Long copyId, String code, String title, String author,
                        String departCode, String localCode, Integer copyIndex) {}
