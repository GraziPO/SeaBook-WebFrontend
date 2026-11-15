package searchoteca.frontend.dto.archive;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Um exemplar encontrado pelo código da etiqueta (GET /api/exemplares/codigo/{codigo}).
 * Alimenta a lista de leitura (js/scan.js) e a conversão "código -> ID" antes de enviar ao backend.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CopyLookup(Long copyId, Integer copyIndex, String code, String isbn, String title,
                         String author, String status, String departCode, String localCode,
                         BigDecimal salePrice) {}
