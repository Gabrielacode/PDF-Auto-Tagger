package com.sample.pdfautotagging.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
public class FullTextItem {
    private int type;
    private int number;
    private int flags;
    private List<Double> bbox;
    private List<FullTextLine> lines;
}
