package com.sample.pdfautotagging.services;

import com.sample.pdfautotagging.models.Box;
import com.sample.pdfautotagging.models.Page;
import com.sample.pdfautotagging.models.PdfData;
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
import org.apache.pdfbox.pdmodel.documentinterchange.taggedpdf.StandardStructureTypes;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PdfAccessibilityTagInjector {
    //After we have mapped all the TextBlocks and the Text Lines of the Page we would need a way to inject the accessibility tags into the PDF Content Stream
    final PdfData pdfJsonData;
    final PDDocument pdfDocument;
    //We will be traversing the whole stream so we will need to keep count of the
    //We will get the list of the Text BLOCKS
    final List<TaggingMappingEngine> taggingMappingEngines;
    long countOfMCIDS =0;
    long textBlockIndex =0;
    long textLineIndex =0;


     PDFStreamParser pdfStreamParser = null;
    public PdfAccessibilityTagInjector(PdfData data, PDDocument pdfDocument,
                                       List<TaggingMappingEngine> taggingMappingEngines) {
        this.pdfJsonData = data;
        this.pdfDocument = pdfDocument;

        this.taggingMappingEngines = taggingMappingEngines;
    }


    public List<Object> injectAccessibilityTokensForText(int pageIndex) throws IOException {
        //We will parse the whole page
        //Then create  new ListThat we will inject for
        pdfStreamParser = new PDFStreamParser(pdfDocument.getPage(pageIndex));
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
                        var currentTextBlock = taggingMappingEngines.get(pageIndex).listOfPdfTextBlocksInThePage.get((int) textBlockIndex);
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
                                // Tj, TJ, and '  and Dotake exactly 1 operand
                                operandsToRestore.add(newPdfObjectList.remove(newPdfObjectList.size() - 1));
                            }

                            // FIX #2: Add them flatly to the stream, NOT as a nested List!
                            newPdfObjectList.add(COSName.getPDFName(pdfTag));
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

    public void buildStructureTreeForEachPage(PDPage pdfPage , int pageIndex , Page jsonPage,PDStructureElement documentElement,Map<Integer, org.apache.pdfbox.pdmodel.common.COSObjectable> numTreeMap){

        // 3. Prepare the ParentTree (The Bridge between Page and Document)

        pdfPage.getCOSObject().setInt(COSName.STRUCT_PARENTS, pageIndex);

        // We need an array to hold the Structure Elements in exact order of their MCIDs
        COSArray parentTreeArray = new COSArray();
        Map<Integer, PDStructureElement> mcidToElementMap = new HashMap<>();
        int highestMcid = -1;

        PDStructureElement currentListContainer = null;

        // 4. Loop through our JSON Boxes and build the Structure Elements
        for (Box box : jsonPage.getBoxes()) {


            // Skip boxes that didn't get any text assigned to them in Pass 2
            if (box.getAssignedMcids() == null || box.getAssignedMcids().isEmpty()) {
                continue;
            }
            // 2. Are we looking at a List Item? or are we in a Box that has a list Item
            if ("list-item".equalsIgnoreCase(box.getBoxclass())) {

                // If we don't have an active List Container yet, create the <L>! Structure Tree Element
                if (currentListContainer == null) {
                    currentListContainer = new PDStructureElement(StandardStructureTypes.L, documentElement);
                    // currentListContainer.setPage();
                    //We dont set the page for grouping structure elements only
                    //Structure elements that have a corresponding MCID in a page
                    documentElement.appendKid(currentListContainer);
                }

                // Create the <LI> (List Item) container and attach it to the <L> structure tree
                PDStructureElement liElement = new PDStructureElement(StandardStructureTypes.LI, documentElement);

                currentListContainer.appendKid(liElement);

                // Now handle the Bullet (<Lbl>) and the Text (<LBody>)
                //The Lbl comes first , then  the Text Lbody
                List<Integer> mcids = box.getAssignedMcids();

                // The first MCID is ALWAYS the bullet point
                PDStructureElement lblElement = new PDStructureElement(StandardStructureTypes.LBL, documentElement);
                lblElement.setPage(pdfPage);
                int bulletMcid = mcids.get(0);
                lblElement.appendKid(bulletMcid);
                mcidToElementMap.put(bulletMcid, lblElement); // Add to ParentTree index!
                liElement.appendKid(lblElement); // Attach to <LI>
                if (bulletMcid > highestMcid) highestMcid = bulletMcid;

                // If there is more text, wrap the rest in an <LBody>
                if (mcids.size() > 1) {
                    PDStructureElement lbodyElement = new PDStructureElement(StandardStructureTypes.L_BODY, documentElement);
                    lbodyElement.setPage(pdfPage);

                    for (int i = 1; i < mcids.size(); i++) {
                        int textMcid = mcids.get(i);
                        lbodyElement.appendKid(textMcid);

                        mcidToElementMap.put(textMcid, lbodyElement); // Add to ParentTree index!
                    }
                    liElement.appendKid(lbodyElement); // Attach to <LI>
                }

            } else{

                // Break the list chain! If we hit a normal paragraph, the list is over.
                currentListContainer = null;

                // Create the Semantic Element ( <H1> or <P>)
                String  pdfTag = translateBoxClassToPdfTag(box.getBoxclass());
                PDStructureElement element = new PDStructureElement(pdfTag, documentElement);
                element.setPage(pdfPage); // Tell the element which physical page it lives on

                // Add the MCIDs to the Element
                for (int mcid : box.getAssignedMcids()) {
                    element.appendKid(mcid); // "This paragraph owns MCID 0"
                    mcidToElementMap.put(mcid, element);

                    if (mcid > highestMcid) {
                        highestMcid = mcid;
                    }
                }

                // Attach the element to the Root
                documentElement.appendKid(element);
            }}

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

        //  We put the num parrent tree array of this page here


        numTreeMap.put(pageIndex, parentTreeArray);

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

        //We would need a structure element of Document as the root
        PDStructureElement documentStructureElement = new PDStructureElement(StandardStructureTypes.DOCUMENT,treeRoot);
        //We append it as kid to the structure tree root
        treeRoot.appendKid(documentStructureElement);

        //We create the num map
        Map<Integer, org.apache.pdfbox.pdmodel.common.COSObjectable> numTreeMap = new HashMap<>();

        for (int i = 0; i<pdfDocument.getPages().getCount();i++){
            if (i >= pdfJsonData.getPages().size())break;
            var jsonPage = pdfJsonData.getPages().get(i);
            buildStructureTreeForEachPage(pdfDocument.getPage(i),i,jsonPage,documentStructureElement,numTreeMap);
        }
        //Then set the parent tree here

        PDNumberTreeNode parentTree = new PDNumberTreeNode(COSArray.class);
        parentTree.setNumbers(numTreeMap);
        treeRoot.setParentTree(parentTree);
    }

    //Then now the final looping
    //The final form

    public void transformPDFDocumentAccessibility() throws IOException {
        for (int i =0;  i < pdfDocument.getPages().getCount();i++){
            if (i >= pdfJsonData.getPages().size()) {
                //We want to ensure that the JSON exists
                System.out.println("JSON data missing for page " + i + ". Stopping Pass 2 injection.");
                break;
            }
            var pageInjectionResults = injectAccessibilityTokensForText(i);
            writeToPdfPage(pdfDocument,i,pageInjectionResults);
        }
        //Then after we structure the document catalag structure tree
        buildStructureTree();
    }



    public String translateBoxClassToPdfTag(String boxClass) {
        if (boxClass == null) return StandardStructureTypes.P; // Default to paragraph

        switch (boxClass.toLowerCase()) {
            case "section-header":
                // For now, all headers are H1. Later, the JSON can provide h1, h2, etc.
                return StandardStructureTypes.H;
            case "list-item":
                return StandardStructureTypes.LI;
            case "picture":
                return StandardStructureTypes.Figure;
            case "text":
                return StandardStructureTypes.P;
            case "page-footer":// You can map footers to paragraphs or Artifacts!
                return  StandardStructureTypes.P;
            default:
                return StandardStructureTypes.P; // Standard reading text
        }
    }

}
