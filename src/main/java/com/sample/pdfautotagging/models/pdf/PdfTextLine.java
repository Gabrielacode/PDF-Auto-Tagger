package com.sample.pdfautotagging.models.pdf;

import com.sample.pdfautotagging.models.json.Box;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
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

    // --- NEW: Spatial Data ---

    @Getter
    @Setter
    private double x0;
    @Getter
    @Setter
    private double y0;
    @Getter
    @Setter
    private double x1;
    @Getter
    @Setter
    private double y1;

    //We want to keep count of the coordinates in the bounding box
    //If the X0 of a character , is lessthan the current x0 , we will make that the x0
    //do the same for the y0

    // --- NEW: Identity Data ---
    @Getter
    @Setter
    private int assignedMcid = -1; // -1 means it hasn't been tagged yet

    public PdfTextLine(int index) {
        this.index = index;
        theBoxsYouBelongTo = new ArrayList<>();
        hasMeasuredFirstLine = false;
        //Initialize the bounding boxes to be the min and max value
        x0 = Double.MAX_VALUE;
        y0 = Double.MAX_VALUE;
        x1 =Double.MIN_VALUE;
        y1 = Double.MIN_VALUE;
    }

    @Override
    public String toString() {
        return  "Boxes :"+theBoxsYouBelongTo.stream().map(Box::getBoundingBox).collect(Collectors.joining(";"));
    }
}
