package seabook.frontend.dto.archive;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Livro de uma localização com a contagem de exemplares ali (GET /api/localizacao/{codigo}/estoque). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LocationBookStock(String isbn, String title, String author, String publisher,
                                long total, long available, long loaned, long reserved) {}
