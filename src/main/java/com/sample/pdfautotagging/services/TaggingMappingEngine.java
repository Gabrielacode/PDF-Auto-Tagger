package com.sample.pdfautotagging.services;
import com.sample.pdfautotagging.models.json.Box;
import com.sample.pdfautotagging.models.json.Page;
import com.sample.pdfautotagging.models.pdf.PdfTextBlock;
import com.sample.pdfautotagging.models.pdf.PdfTextLine;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class TaggingMappingEngine  extends PDFTextStripper {

    //This for one page
    //We want to have a list of text positions
   //   public  List<TextPosition> textPositions = new ArrayList<>();
    // This tracks the raw stream operator currently being processed
    private Operator currentOperator;
    //We want to keep track of the Page we currently editing
    final Page page;

    public PdfTextBlock currentPdfTextBlock = null;
    public List<PdfTextBlock> listOfPdfTextBlocksInThePage;

    // This map will store the Operator reference and the MCID it belongs to.
    // We will use this map in Pass 2 to know exactly which operators to wrap.
    //There are now stored in the PdfTextBlocks


    public TaggingMappingEngine(Page page) {
        super();
        this.page = page;
        listOfPdfTextBlocksInThePage = new ArrayList<>();

        //
        setSortByPosition(false);
    }

    //This will be called on each operator , since PDF uses the Post Fix Notation
    //Where operands are passed onto the stack before the operator
    @Override
    protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
        //We want to keep count of the current operator ,and check check if it a text draw opertion
        //(Tj or TJ) operator

        this.currentOperator = operator;
       // System.out.println("OPERANDS :"+ operands.stream().map(Object::toString).collect(Collectors.joining(";")));

        //We want  to keep check of the current  operator
        //If we see Bt ,we want to create a new text block  and set it as the current text block
        //If we see Et , we want to  add the current Text block to the list of text blocks and then set the current Text Box to null

        if(operator.getName().equals("BT")){
            PdfTextBlock newPdfTextBlock = new PdfTextBlock();
            newPdfTextBlock.setIndex(listOfPdfTextBlocksInThePage.size());
            currentPdfTextBlock = newPdfTextBlock;
        } else if (operator.getName().equals("ET")) {
            //If we have gone through the text positions forthe current text block and there isno match there , we will just add it still to the list of text blocks and assign it a value
            if(currentPdfTextBlock != null) {
                listOfPdfTextBlocksInThePage.add(currentPdfTextBlock);
                currentPdfTextBlock = null;
            }
        } else if (operator.getName().equals("TJ") || operator.getName().equals("Tj") || operator.getName().equals("'") || operator.getName().equals("\"")) {

            //We now know we are in a new text line ,,we will set the current text line of the current text box
            if(currentPdfTextBlock != null){
                //Create a new Text line in the current Text Box
                PdfTextLine pdfTextLine = new PdfTextLine(currentPdfTextBlock.getTextLines().size());
                currentPdfTextBlock.getTextLines().add(pdfTextLine);
                currentPdfTextBlock.setCurrentPdfTextLine( pdfTextLine );
            }
        }

        // System.out.println("Operator : "+operator + " Operands : "+operands.stream().map(op->op.getKey().toString()));
        super.processOperator(operator, operands);
        //This will cause us tomove to the next methods , that help us calculte the text positiorn
        //Then we release the operator , so as to reduce memory costs and all that
        currentOperator = null;
    }




    @Override


    //This will be called on each  text , to get thier position

    protected void processTextPosition(TextPosition textPosition    ) {

        //We want to draw
       // if (currentOperator == null) return;

        //We want to check if we are currently in a text box and in a text line and the text line has not measured it first character
        //We are moving out of the measured first line
        //We would need to get the bounding box
        if(currentPdfTextBlock == null || currentPdfTextBlock.getCurrentPdfTextLine() == null ) return;

       // Calculate X -Y coordinates of the  bounding BOX , which is top-left coordinates
        //Of the current Pdf Text Line
        // text.getYDirAdj() is the font baseline. Subtracting height gives us the top-left Y.
        // 1. Calculate this specific character's boundaries
        double charX0 = textPosition.getXDirAdj();
        // Remember: YDirAdj is the baseline (bottom). Subtract height to get the top!
        //Since text startsfrom the baseline
        double charY0 = textPosition.getYDirAdj() - textPosition.getHeightDir();

        double charX1 = charX0 + textPosition.getWidthDirAdj();
        double charY1 = textPosition.getYDirAdj(); // The baseline is the maximum Y
        //Since Tl sets the baseline and T* = (0 -TL) Td , which affects the displacement in the matrix

        //Now we want the 0, to be the minimum and the 1 to be the maximum

        var currentTextLine = currentPdfTextBlock.getCurrentPdfTextLine();
        currentTextLine.setX0(Math.min(currentTextLine.getX0(),charX0));
        currentTextLine.setY0(Math.min(currentTextLine.getY0(),charY0));
        //Then the inverse
        currentTextLine.setX1(Math.max(currentTextLine.getX1(),charX1));
        currentTextLine.setY1(Math.max(currentTextLine.getY1(),charY1));



        Rectangle2D.Double charBox = new Rectangle2D.Double(charX0,charY0,(charX1-charX0),(charY1-charY0));



        //We would loop through the Boxs of the current Page
        for(Box box : page.getBoxes()){
            if(isThereAnIntersectionOrContainmentBetweenJsonBoxAndCharBox(box, charBox)){
                //We would need to check if it has not been added
                if(!currentPdfTextBlock.getCurrentPdfTextLine().getTheBoxsYouBelongTo().contains(box)) {
                    currentPdfTextBlock.getCurrentPdfTextLine().getTheBoxsYouBelongTo().add(box);
                }
            }
        }
        //Now after looping through , we will check , if the text line has been added to one or more boxes , if yes , we move on , else we continue

//        if(!currentPdfTextBlock.getCurrentPdfTextLine().getTheBoxsYouBelongTo().isEmpty()){
//            //We set has measured first pass to true
//            currentPdfTextBlock.getCurrentPdfTextLine().setHasMeasuredFirstLine(true);
//        }

        // Just set it to true immediately after the loop!
        currentPdfTextBlock.getCurrentPdfTextLine().setHasMeasuredFirstLine(true);
        super.processTextPosition(textPosition);

    }

    boolean isThereAnIntersectionOrContainmentBetweenJsonBoxAndCharBox(Box jsonBox , Rectangle2D charBox){
       //We would just need to check if the start x , is below or equal the start x of the origin
        //Do the same for

        Rectangle2D.Float jsonBoundingBox = new  Rectangle2D.Float(
                (float) jsonBox.getX0(),
                (float) jsonBox.getY0(),
                (float) (jsonBox.getX1()-jsonBox.getX0()),
                (float)(jsonBox.getY1()-jsonBox.getY0())
        );
        return  jsonBoundingBox.contains(charBox);

    }


}
