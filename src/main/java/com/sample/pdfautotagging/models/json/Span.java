package com.sample.pdfautotagging.models.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Span {
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
