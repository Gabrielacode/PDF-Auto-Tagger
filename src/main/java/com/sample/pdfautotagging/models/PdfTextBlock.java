package com.sample.pdfautotagging.models;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class PdfTextBlock {
    //We want to keep track of its index
    @Getter
    @Setter
    int index ;
    @Getter
    List<PdfTextLine> textLines;

    @Getter
    @Setter
    PdfTextLine currentPdfTextLine = null;

    public PdfTextBlock(){
        index = 0;
        textLines = new ArrayList<>();
    }

    @Override
    public String toString() {
        return  "Text Block : Index -> "+ index+
                "Lines : Count"+textLines.size()+"Items "+textLines.stream().map(PdfTextLine::toString).collect(Collectors.joining(","));
    }
}
