package com.sample.pdfautotagging.services;

import com.sample.pdfautotagging.models.Box;
import com.sample.pdfautotagging.models.Page;
import com.sample.pdfautotagging.models.PdfTextBlock;
import org.apache.pdfbox.contentstream.PDContentStream;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDNumberTreeNode;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDMarkInfo;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PdfAccessibilityTagInjector {
    //After we have mapped all the TextBlocks and the Text Lines of the Page we would need a way to inject the accessibility tags into the PDF Content Stream
    final Page pdfPage;
    final PDDocument pdfDocument;
    //We will be traversing the whole stream so we will need to keep count of the
    //We will get the list of the Text BLOCKS
    final List<PdfTextBlock> listOfTextBlocks;
    long countOfMCIDS =0;
    long textBlockIndex =0;
    long textLineIndex =0;


     PDFStreamParser pdfStreamParser = null;
    public PdfAccessibilityTagInjector(Page pdfPage, PDDocument pdfDocument,
                                       List<PdfTextBlock> listOfTextBlocks) {
        this.pdfPage = pdfPage;
        this.pdfDocument = pdfDocument;

        this.listOfTextBlocks = listOfTextBlocks;
    }

    public void initialize() throws IOException {

        pdfStreamParser = new PDFStreamParser(pdfDocument.getPage(0));
    }
    public List<Object> injectAccessibilityTokensForText() throws IOException {
        //We will parse the whole page
        //Then create  new ListThat we will inject for
        List<Object> newPdfObjectList = new ArrayList<>();
        var listOfTokens = pdfStreamParser.parse();
        //We loop through the List
        for(Object token :  listOfTokens){
            //Are you  a not an operator
            //If you are not , we will just add you to the new List
            if(!(token instanceof Operator operator)){
                //We will just add you to the List'
                newPdfObjectList.add(token);
                continue;
            }
            //Now we want to check for
            //A . BT if Bt ,we set the line index to 0 and then add it to our list
            // B . If is ET , we increment our text block index and then add it to our new list
            //If Tj ,TJ , ' ," , we will have to get the corrsponding box from the line in the current index
            //If it is empty , we add the operator to the list , and then increment the line index
            //If it is not empty , we get the Box for the current line , then we
            //Convert the box class to the appropraite PDF tag ,
            //Then we Do thei we put the tag first
            //Then the MCID dictionary, with the current mcid index ,
            //Then BDC operator that is in PDF
            // Then put the COS String
            //And then the Tj Operator
            //Then the EMC
            //Then add the corresponding mcid number to the Box
            //Then increment the line index
            //If it any other operator we just Add it for now
            else{
                switch (operator.getName()) {
                    case "BT":
                        textLineIndex =0;
                        newPdfObjectList.add(operator);
                        break;
                    case "ET":
                        textBlockIndex++;
                        newPdfObjectList.add(operator);
                        break;
                    case "Tj":
                    case "TJ":
                    case "'":
                    case "\"":
                        var currentTextBlock = listOfTextBlocks.get((int) textBlockIndex);
                        var currentTextLine = currentTextBlock.getTextLines().get((int) textLineIndex);

                        if(currentTextLine.getTheBoxsYouBelongTo().isEmpty()){
                            // No Box was assigned to you, skip and increment
                            newPdfObjectList.add(operator);
                            textLineIndex++;
                            break;
                        } else {
                            var box = currentTextLine.getTheBoxsYouBelongTo().get(0);
                            var pdfTag = translateBoxClassToPdfTag(box.getBoxclass());

                            // Use setInt for MCID, it is the strict PDF standard
                            var mcidDictionary  = new COSDictionary();
                            mcidDictionary.setInt(COSName.MCID, (int) countOfMCIDS);

                            var bdcOperator = Operator.getOperator("BDC");
                            var emcOperator = Operator.getOperator("EMC");

                            // FIX #1: Safely pop the correct number of operands
                            List<Object> operandsToRestore = new ArrayList<>();
                            if (operator.getName().equals("\"")) {
                                // The " operator takes exactly 3 operands. Pop them in reverse!
                                Object op3 = newPdfObjectList.remove(newPdfObjectList.size() - 1);
                                Object op2 = newPdfObjectList.remove(newPdfObjectList.size() - 1);
                                Object op1 = newPdfObjectList.remove(newPdfObjectList.size() - 1);
                                operandsToRestore.add(op1);
                                operandsToRestore.add(op2);
                                operandsToRestore.add(op3);
                            } else {
                                // Tj, TJ, and ' take exactly 1 operand
                                operandsToRestore.add(newPdfObjectList.remove(newPdfObjectList.size() - 1));
                            }

                            // FIX #2: Add them flatly to the stream, NOT as a nested List!
                            newPdfObjectList.add(pdfTag);
                            newPdfObjectList.add(mcidDictionary);
                            newPdfObjectList.add(bdcOperator);

                            newPdfObjectList.addAll(operandsToRestore); // Restore the strings/numbers
                            newPdfObjectList.add(operator);             // Restore the Tj/"/'/TJ
                            newPdfObjectList.add(emcOperator);          // Close the tag

                            // Add it to the box mcid list
                            box.getAssignedMcids().add((int) countOfMCIDS);

                            countOfMCIDS++;
                            textLineIndex++;
                        }

                        break;
                    default:
                        newPdfObjectList.add(operator);
                        break;






                }
            }
        }

        //Then we  return the new parsed list
        //And reset it
        countOfMCIDS =0;
        textBlockIndex =0;
        textLineIndex =0;
        return newPdfObjectList;

    }

    public void writeToPdfPage (PDDocument pdfDocument,int index,List<Object> modifiedTokens ){
        PDStream pdStream =  new PDStream(pdfDocument);
        try (OutputStream out = pdStream.createOutputStream(COSName.FLATE_DECODE)) {
            ContentStreamWriter tokenWriter = new ContentStreamWriter(out);
            tokenWriter.writeTokens(modifiedTokens);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //Then we set the pd document pag e
        pdfDocument.getPage(index).setContents(pdStream);
    }

    public void buildStructureTree() {
        PDDocumentCatalog catalog = pdfDocument.getDocumentCatalog();

        // 1. Tell the PDF Reader: "This is a Tagged PDF!"
        PDMarkInfo markInfo = new PDMarkInfo();
        markInfo.setMarked(true);
        catalog.setMarkInfo(markInfo);

        // 2. Create the Structure Tree Root (The Front Desk)
        PDStructureTreeRoot treeRoot = new PDStructureTreeRoot();
        catalog.setStructureTreeRoot(treeRoot);

        // 3. Prepare the ParentTree (The Bridge between Page and Document)
        PDPage firstPdfPage = pdfDocument.getPage(0);
        int structParentsIndex = 0;
        firstPdfPage.getCOSObject().setInt(COSName.STRUCT_PARENTS, structParentsIndex);

        // We need an array to hold the Structure Elements in exact order of their MCIDs
        COSArray parentTreeArray = new COSArray();
        Map<Integer, PDStructureElement> mcidToElementMap = new HashMap<>();
        int highestMcid = -1;

        // 4. Loop through our JSON Boxes and build the Structure Elements
        for (Box box : pdfPage.getBoxes()) {

            // Skip boxes that didn't get any text assigned to them in Pass 2
            if (box.getAssignedMcids() == null || box.getAssignedMcids().isEmpty()) {
                continue;
            }

            // Create the Semantic Element (e.g., <H1> or <P>)
            COSName pdfTag = translateBoxClassToPdfTag(box.getBoxclass());
            PDStructureElement element = new PDStructureElement(pdfTag.getName(), treeRoot);
            element.setPage(firstPdfPage); // Tell the element which physical page it lives on

            // Add the MCIDs to the Element
            for (int mcid : box.getAssignedMcids()) {
                element.appendKid(mcid); // "This paragraph owns MCID 0"
                mcidToElementMap.put(mcid, element);

                if (mcid > highestMcid) {
                    highestMcid = mcid;
                }
            }

            // Attach the element to the Root
            treeRoot.appendKid(element);
        }

        // 5. Fill the ParentTree Array
        // The PDF specification requires this array to have the Structure Elements
        // at the exact index of their MCID. (e.g., Array[5] must hold the element for MCID 5)
        for (int i = 0; i <= highestMcid; i++) {
            if (mcidToElementMap.containsKey(i)) {
                parentTreeArray.add(mcidToElementMap.get(i));
            } else {
                parentTreeArray.add(COSNull.NULL); // Filler for missing/skipped MCIDs
            }
        }

        // 6. Attach the ParentTree to the Structure Tree Root
        PDNumberTreeNode parentTree = new PDNumberTreeNode(COSArray.class);
        Map<Integer, org.apache.pdfbox.pdmodel.common.COSObjectable> numTreeMap = new HashMap<>();
        numTreeMap.put(structParentsIndex, parentTreeArray);
        parentTree.setNumbers(numTreeMap);

        treeRoot.setParentTree(parentTree);
    }
    public COSName translateBoxClassToPdfTag(String boxClass) {
        if (boxClass == null) return COSName.P; // Default to paragraph

        switch (boxClass.toLowerCase()) {
            case "section-header":
                // For now, all headers are H1. Later, the JSON can provide h1, h2, etc.
                return COSName.getPDFName("H1");
            case "list-item":
                return COSName.getPDFName("LI");
            case "picture":
                return COSName.getPDFName("Figure");
            case "text":
            case "page-footer": // You can map footers to paragraphs or Artifacts!
            default:
                return COSName.P; // Standard reading text
        }
    }

}
