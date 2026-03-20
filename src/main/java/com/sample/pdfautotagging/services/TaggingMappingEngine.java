package com.sample.pdfautotagging.services;
import com.sample.pdfautotagging.models.Box;
import com.sample.pdfautotagging.models.Page;
import com.sample.pdfautotagging.models.PdfTextBlock;
import com.sample.pdfautotagging.models.PdfTextLine;
import lombok.Getter;

import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TaggingMappingEngine  extends PDFTextStripper {

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
    @Getter
    private Map<Operator, Integer> operatorToMcidMap = new HashMap<>();

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


        System.out.println(currentOperator);
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
    public void beginText() throws IOException {
        System.out.println("Page Begin ");
        super.beginText();
    }

    @Override


    //This will be called on each  text , to get thier position

    protected void processTextPosition(TextPosition text) {


        //We want to draw
       // if (currentOperator == null) return;

        //We want to check if we are currently in a text box and in a text line and the text line has not measured it first character
        if(currentPdfTextBlock == null || currentPdfTextBlock.getCurrentPdfTextLine() == null || currentPdfTextBlock.getCurrentPdfTextLine().isHasMeasuredFirstLine()) return;

       // Calculate X -Y coordinates of the  bounding BOX , which is top-left coordinates
        //Of the current Pdf Text Line
        // text.getYDirAdj() is the font baseline. Subtracting height gives us the top-left Y.
        float x = text.getXDirAdj();
        float y = text.getYDirAdj() - text.getHeightDir();
        float w = text.getWidthDirAdj();
        float h = text.getHeightDir();

        Rectangle2D.Float charBox = new Rectangle2D.Float(x, y, w, h);

        //We would loop through the Boxs of the current Page
        for(Box box : page.getBoxes()){
            if(isThereAnIntersectionOrContainmentBetweenJsonBoxAndCharBox(box, charBox)){
                currentPdfTextBlock.getCurrentPdfTextLine().getTheBoxsYouBelongTo().add(box);
            }
        }
        //Now after looping through , we will check , if the text line has been added to one or more boxes , if yes , we move on , else we continue

//        if(!currentPdfTextBlock.getCurrentPdfTextLine().getTheBoxsYouBelongTo().isEmpty()){
//            //We set has measured first pass to true
//            currentPdfTextBlock.getCurrentPdfTextLine().setHasMeasuredFirstLine(true);
//        }

        // Just set it to true immediately after the loop!
        currentPdfTextBlock.getCurrentPdfTextLine().setHasMeasuredFirstLine(true);
        super.processTextPosition(text);

        // 2. The Hit-Test Logic goes here!
        // In your main execution block, you will pass your JSON array in here.
        // For demonstration, let's pretend we have a jsonBox [50.34, 17.0, 250.41, 36.38]
        // and its intended MCID is 0.

        /* if (BoundingBoxHitTester.isHit(jsonBox, charBox, 0.5f)) {
            // We found a hit! Link this raw PDF operator to our desired MCID.
            // If this operator draws "SAMUEL", it will now be mapped to MCID 0.
            operatorToMcidMap.put(currentOperator, 0);
        }
        */

        // Note: For Type3 fonts, text.getUnicode() might return gibberish,
        // which is exactly why we rely entirely on the charBox spatial coordinates!
    }

    boolean isThereAnIntersectionOrContainmentBetweenJsonBoxAndCharBox(Box jsonBox , Rectangle2D.Float charBox){
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
