package com.sample.pdfautotagging.models.json;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PdfExtractionResponse {
    private boolean success;
    private String filename;

    @JsonProperty("page_count")
    private int pageCount;

    private PdfData data;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
class PdfLink {
    private int kind;
    private int xref;
    private List<Double> from;
    private String uri;
    private String id;
}

