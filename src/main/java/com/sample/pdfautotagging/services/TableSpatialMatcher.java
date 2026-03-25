package com.sample.pdfautotagging.services;

import com.sample.pdfautotagging.models.PdfTextBlock;
import com.sample.pdfautotagging.models.PdfTextLine;
import com.sample.pdfautotagging.models.TableCell;
import com.sample.pdfautotagging.models.json.Box;

import java.util.ArrayList;
import java.util.List;

public class TableSpatialMatcher {
    //THis will map textlines in text blocks to corresponing table cells
    //So that we can easily identify them in the structure tree

    public List<TableCell> mapTextToTableCells(Box tableBox, List<PdfTextBlock> pageTextBlocks) {
        List<TableCell> processedCells = new ArrayList<>();

        List<List<List<Double>>> rawCells = tableBox.getTable().getCells();
        int rowCount = tableBox.getTable().getRowCount();
        int colCount = tableBox.getTable().getColCount();

        // --- NEW: The 'Claimed' Grid ---
        //We want col spans to be claimed
        //And also row spans
        boolean[][] claimed = new boolean[rowCount][colCount];

        for (int r = 0; r < rowCount; r++) {
            for (int c = 0; c < colCount; c++) {

                // 1. If a previous cell's colSpan or rowSpan already ate this slot, skip it!
                if (claimed[r][c]) continue;

                List<Double> cellBbox = rawCells.get(r).get(c);

                // Safety check: If Docling is weird and gave an unclaimed null, just skip
                if (cellBbox == null || cellBbox.isEmpty()) continue;

                int colSpan = 1;
                int rowSpan = 1;

                // 2. Calculate ColSpan: Look to the right!
                // As long as the next cell is unclaimed and null, we eat it.
                while (c + colSpan < colCount) {
                    //We would get the next  column
                    List<Double> rightCell = rawCells.get(r).get(c + colSpan);
                    //If it is not claimed  , and the cell is  null or empty
                    // We would claim it
                    //Else we break  the col span of the current column
                    if (!claimed[r][c + colSpan] && (rightCell == null || rightCell.isEmpty())) {
                        colSpan++;
                    } else {
                        break;
                    }
                }

                // 3. Calculate RowSpan: Look down!
                // To span down, the ENTIRE width of our colSpan must be null in the row below.
                while (r + rowSpan < rowCount) {
                    boolean entireRowSpanIsNull = true;
                    for (int cs = 0; cs < colSpan; cs++) {
                        List<Double> downCell = rawCells.get(r + rowSpan).get(c + cs);
                        //If any is not null , we reverse the condition and break out of the loop
                        if (claimed[r + rowSpan][c + cs] || (downCell != null && !downCell.isEmpty())) {
                            entireRowSpanIsNull = false;
                            break;
                        }
                    }
                    if (entireRowSpanIsNull) {
                        rowSpan++;
                    } else {
                        break;
                    }
                }

                // 4. Mark all eaten cells as CLAIMED so the loop ignores them later
                for (int rs = 0; rs < rowSpan; rs++) {
                    for (int cs = 0; cs < colSpan; cs++) {
                        claimed[r + rs][c + cs] = true;
                    }
                }

                // 5. Create our glorious TableCell!
                TableCell newCell = new TableCell();
                newCell.setRowIndex(r);
                newCell.setColumnIndex(c);
                newCell.setColumnSpan(colSpan);
                newCell.setRowSpan(rowSpan);
                newCell.setHeader(  (r == 0)); // Row 0 is the header
                newCell.setBoundingBox(new double[] {
                        cellBbox.get(0), cellBbox.get(1), cellBbox.get(2), cellBbox.get(3)
                });

                // 6. Center-Point Collision Math (Unchanged)
                for (PdfTextBlock block : pageTextBlocks) {
                    for (PdfTextLine line : block.getTextLines()) {

                        double lineCenterX = line.getX0() + ((line.getX1() - line.getX0()) / 2.0);
                        double lineCenterY = line.getY0() + ((line.getY1() - line.getY0()) / 2.0);

                        boolean isInsideX = lineCenterX >= newCell.getBoundingBox()[0] && lineCenterX <= newCell.getBoundingBox()[2];
                        boolean isInsideY = lineCenterY >= newCell.getBoundingBox()[1] && lineCenterY <= newCell.getBoundingBox()[3];

                        if (isInsideX && isInsideY) {
                            newCell.getPdfTextLines().add(line);
                            if (!line.getTheBoxsYouBelongTo().contains(tableBox)) {
                                line.getTheBoxsYouBelongTo().add(tableBox);
                            }
                        }
                    }
                }
                processedCells.add(newCell);
            }
        }
        return processedCells;
    }

}
