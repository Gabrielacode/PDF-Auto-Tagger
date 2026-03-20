package com.sample.pdfautotagging.models;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class PdfTextLine {
    @Getter
    @Setter
    int index ;
    @Getter
    List<Box> theBoxsYouBelongTo;
    @Getter
    @Setter
    boolean hasMeasuredFirstLine;

    public PdfTextLine(int index) {
        this.index = index;
        theBoxsYouBelongTo = new ArrayList<>();
        hasMeasuredFirstLine = false;
    }

    @Override
    public String toString() {
        return  "Boxes :"+theBoxsYouBelongTo.stream().map(Box::getBoundingBox).collect(Collectors.joining(";"));
    }
}
