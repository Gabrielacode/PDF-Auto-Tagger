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
class TextLine {
    private List<Double> bbox;
    private List<Span> spans;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
class Span {
    private double size;
    private int flags;
    private int bidi;

    @JsonProperty("char_flags")
    private int charFlags;

    private String font;
    private int color;
    private int alpha;
    private double ascender;
    private double descender;
    private String text;

    private List<Double> origin;
    private List<Double> bbox;

    private Integer line; // Optional in some contexts inside fulltext
    private Integer block;

    private List<Double> dir;
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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
class PdfMetadata {
    private String format;
    private String title;
    private String author;
    private String subject;
    private String keywords;
    private String creator;
    private String producer;
    private String creationDate;
    private String modDate;
    private String trapped;
    private Object encryption;
}