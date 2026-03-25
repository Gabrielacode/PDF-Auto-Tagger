package com.sample.pdfautotagging.models;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class TableCell {
    //We want to keep track of the row , index and the column index
    int rowIndex;
    int columnIndex;

    //We also want to keep track of the span of the column or the row
    int rowSpan=1;
    int columnSpan=1;

    //We want to check if the cell is a header cell or a normal row
    //Is it the first row
    boolean isHeader = false;

    //We want to keep count of the bounding box
    double [] boundingBox ;
    //Assigned MCIDS
    public List<Integer> assignedMcids = new ArrayList<>();

    //The list of TextLines
    public List<PdfTextLine> pdfTextLines = new ArrayList<>();
}
