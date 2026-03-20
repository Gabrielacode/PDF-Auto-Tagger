package com.sample.pdfautotagging.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Box {
    private double x0;
    private double y0;
    private double x1;
    private double y1;

    private String boxclass;
    private Object image; // Null in JSON, typed as Object
    private Object table; // Null in JSON, typed as Object

    private List<TextLine> textlines;

    // IN Pass 2 we will fill this up
    @JsonIgnore
    private List<Integer> assignedMcids = new ArrayList<>();


   public String getBoundingBox(){
        return "Box Class "+boxclass+ "["+ x0 +","+y0 +","+x1 +","+y1 +"]";
    }
}
