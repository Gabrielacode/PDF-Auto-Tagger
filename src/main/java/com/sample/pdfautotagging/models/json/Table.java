package com.sample.pdfautotagging.models.json;



import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class Table {

    // The master bounding box for the entire table [x0, y0, x1, y1]
    private List<Double> bbox;

    @JsonProperty("row_count")
    private int rowCount;

    @JsonProperty("col_count")
    private int colCount;

    // The Grid: List of Rows -> List of Columns -> List of 4 Coordinates [x0, y0, x1, y1]
    // Note: The inner list will be null if a cell is merged!
    private List<List<List<Double>>> cells;

//    // The raw text data matching the grid
//    // List of Rows -> List of Columns -> String
//    private List<List<String>> extract;
//
//    // The raw markdown representation (optional, but good to keep if mapped)
   private String markdown;

}
