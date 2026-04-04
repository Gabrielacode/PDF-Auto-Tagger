package com.sample.pdfautotagging.services;

import com.sample.pdfautotagging.models.pdf.FontStyle;
import com.sample.pdfautotagging.models.pdf.PdfTextLine;
import com.sample.pdfautotagging.models.pdf.TableCell;
import com.sample.pdfautotagging.models.json.*;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.common.PDNumberTreeNode;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDMarkInfo;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.apache.pdfbox.pdmodel.documentinterchange.taggedpdf.PDTableAttributeObject;
import org.apache.pdfbox.pdmodel.documentinterchange.taggedpdf.StandardStructureTypes;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitWidthDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode;
import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.schema.DublinCoreSchema;
import org.apache.xmpbox.schema.XMPBasicSchema;
import org.apache.xmpbox.xml.XmpSerializer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.stream.Collectors;

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
    Map<Integer,Map<Double, String> >pagesAndThierMappedHeadersFonts;


     PDFStreamParser pdfStreamParser = null;
    public PdfAccessibilityTagInjector(PdfData data, PDDocument pdfDocument,
                                       List<TaggingMappingEngine> taggingMappingEngines) {
        this.pdfJsonData = data;
        this.pdfDocument = pdfDocument;

        this.taggingMappingEngines = taggingMappingEngines;
    }

    void mapPagesAndThierFontStyles() {
        pagesAndThierMappedHeadersFonts = new HashMap<>();

        // Loop with the integer index
        for (int i = 0; i < pdfJsonData.getPages().size(); i++) {
            Page page = pdfJsonData.getPages().get(i);
            var fontStyles = getFontsSizesOrderedBySizeAndNumberOfTimesAppearedInPdfPage(page);
            var mappedHeaderFonts = buildDynamicHeadingMap(fontStyles);

            // Save using the Integer index!
            pagesAndThierMappedHeadersFonts.put(i, mappedHeaderFonts);
        }
    }


    public List<Object> injectAccessibilityTokensForText(int pageIndex) throws IOException {
        //We will parse the whole page
        pdfStreamParser = new PDFStreamParser(pdfDocument.getPage(pageIndex));
        var listOfTokens = pdfStreamParser.parse();

        // Pre-size it to be slightly larger than the old list so it never has to resize!
        // Since Java Array Lists also creates new array and resizes it if it is full
        List<Object> newPdfObjectList = new ArrayList<>((int) (listOfTokens.size() * 2.5));

        //We want to keep track of the images
        var currentPageJson = pdfJsonData.getPages().get(pageIndex);
        List<Box> pictureBoxes = currentPageJson.getBoxes().stream()
                .filter(box -> "picture".equalsIgnoreCase(box.getBoxclass()))
                .toList();
        int currentPictureIndex = 0;

        // Memory Optimization Cache common operators as local variables to avoid Map lookups in the tight loop
        final Operator bdcOperator = Operator.getOperator("BDC");
        final Operator bmcOperator = Operator.getOperator("BMC");
        final Operator emcOperator = Operator.getOperator("EMC");

        //We loop through the List
        for(Object token :  listOfTokens){
            //If you are not an operator, we will just add you to the new List
            if(!(token instanceof Operator operator)){
                newPdfObjectList.add(token);
                continue;
            }

            switch (operator.getName()) {
                case "BT":
                    textLineIndex = 0;
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
                        // Wrap the text in an Artifact block!

                        if (operator.getName().equals("\"")) {
                            //We pop 3 here
                            Object op3 = newPdfObjectList.remove(newPdfObjectList.size() - 1);
                            Object op2 = newPdfObjectList.remove(newPdfObjectList.size() - 1);
                            Object op1 = newPdfObjectList.remove(newPdfObjectList.size() - 1);

                            newPdfObjectList.add(COSName.ARTIFACT);
                            newPdfObjectList.add(bmcOperator);

                            // Add operands directly!
                            newPdfObjectList.add(op1);
                            newPdfObjectList.add(op2);
                            newPdfObjectList.add(op3);
                        } else {
                            //Else we pop one
                            Object op1 = newPdfObjectList.remove(newPdfObjectList.size() - 1);

                            newPdfObjectList.add(COSName.ARTIFACT);
                            newPdfObjectList.add(bmcOperator);

                            // Add operand directly!
                            newPdfObjectList.add(op1);
                        }

                        newPdfObjectList.add(operator);
                        newPdfObjectList.add(emcOperator); // Close the Artifact

                        textLineIndex++;
                        break;

                    } else {
                        var box = currentTextLine.getTheBoxsYouBelongTo().get(0);
                        var mappedHeadings = pagesAndThierMappedHeadersFonts.get(pageIndex);
                        var pdfTag = translateBoxClassToPdfTag(box, mappedHeadings);

                        // Use setInt for MCID, it is the strict PDF standard
                        var mcidDictionary  = new COSDictionary();
                        mcidDictionary.setInt(COSName.MCID, (int) countOfMCIDS);

                        if (operator.getName().equals("\"")) {
                            // The " operator takes exactly 3 operands. Pop them in reverse!
                            Object op3 = newPdfObjectList.remove(newPdfObjectList.size() - 1);
                            Object op2 = newPdfObjectList.remove(newPdfObjectList.size() - 1);
                            Object op1 = newPdfObjectList.remove(newPdfObjectList.size() - 1);

                            newPdfObjectList.add(COSName.getPDFName(pdfTag));
                            newPdfObjectList.add(mcidDictionary);
                            newPdfObjectList.add(bdcOperator);

                            // Add operands directly!
                            newPdfObjectList.add(op1);
                            newPdfObjectList.add(op2);
                            newPdfObjectList.add(op3);
                        } else {
                            // Tj, TJ, and ' take exactly 1 operand
                            Object op1 = newPdfObjectList.remove(newPdfObjectList.size() - 1);

                            newPdfObjectList.add(COSName.getPDFName(pdfTag));
                            newPdfObjectList.add(mcidDictionary);
                            newPdfObjectList.add(bdcOperator);

                            // Add operand directly!
                            newPdfObjectList.add(op1);
                        }

                        newPdfObjectList.add(operator);             // Restore the Tj/"/'/TJ
                        newPdfObjectList.add(emcOperator);          // Close the tag

                        // Add it to the box mcid list
                        box.getAssignedMcids().add((int) countOfMCIDS);
                        //Assign it also to the text line
                        currentTextLine.setAssignedMcid((int) countOfMCIDS);
                        countOfMCIDS++;
                        textLineIndex++;
                    }

                    break;

                //For Images
                case "Do":
                    //  Pop the single operand (the XObject name, e.g., /Im1)
                    Object imageOperand = newPdfObjectList.remove(newPdfObjectList.size() - 1);

                    //  Check if we have a mapped Docling picture box for this image
                    if (currentPictureIndex < pictureBoxes.size()) {
                        Box picBox = pictureBoxes.get(currentPictureIndex);

                        // Set up the MCID dictionary
                        var mcidDictionary = new COSDictionary();
                        mcidDictionary.setInt(COSName.MCID, (int) countOfMCIDS);

                        // Wrap in a <Figure> BDC tag
                        newPdfObjectList.add(COSName.getPDFName(StandardStructureTypes.Figure));
                        newPdfObjectList.add(mcidDictionary);
                        newPdfObjectList.add(bdcOperator);

                        // Restore the operand and the "Do" operator
                        newPdfObjectList.add(imageOperand);
                        newPdfObjectList.add(operator);

                        // Close the tag
                        newPdfObjectList.add(emcOperator);

                        // Safely initialize the MCID list for the Box if Docling left it null
                        if (picBox.getAssignedMcids() == null) {
                            picBox.setAssignedMcids(new ArrayList<>());
                        }
                        // Assign the MCID to the picture Box so Pass 3 can find it
                        picBox.getAssignedMcids().add((int) countOfMCIDS);

                        countOfMCIDS++;
                        currentPictureIndex++;
                    } else {
                        // THE ARTIFACT SWEEPER (For extra/decorative images)
                        // Docling didn't map this, so hide it from the screen reader!
                        newPdfObjectList.add(COSName.ARTIFACT);
                        newPdfObjectList.add(bmcOperator); // No dictionary needed

                        newPdfObjectList.add(imageOperand);
                        newPdfObjectList.add(operator);

                        newPdfObjectList.add(emcOperator);
                    }
                    break;

                // Table borders, background colors, and decorative lines.
                case "S":   // Stroke path
                case "s":   // Close and stroke path
                case "f":   // Fill path
                case "F":   // Fill path (obsolete but still used)
                case "f*":  // Fill path (even-odd rule)
                case "B":   // Fill and stroke path
                case "B*":  // Fill and stroke path (even-odd rule)
                case "b":   // Close, fill, and stroke path
                case "b*":  // Close, fill, and stroke path (even-odd rule)
                case "n":   // End path without filling or stroking

                    // These operators take ZERO operands! Just wrap them in a BMC Artifact block.
                    newPdfObjectList.add(COSName.ARTIFACT);
                    newPdfObjectList.add(bmcOperator);

                    newPdfObjectList.add(operator); // The painting operator (S, f, B, etc.)

                    newPdfObjectList.add(emcOperator);
                    break;

                case "sh": // Shading (Gradients)
                    // Shading takes exactly 1 operand (the shading dictionary name)
                    Object shadingOperand = newPdfObjectList.remove(newPdfObjectList.size() - 1);

                    newPdfObjectList.add(COSName.ARTIFACT);
                    newPdfObjectList.add(bmcOperator);

                    newPdfObjectList.add(shadingOperand);
                    newPdfObjectList.add(operator);

                    newPdfObjectList.add(emcOperator);
                    break;

                default:
                    newPdfObjectList.add(operator);
                    break;
            }
        }

        //And reset it
        countOfMCIDS = 0;
        textBlockIndex = 0;
        textLineIndex = 0;

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

    public void buildStructureTreeForEachPage(PDPage pdfPage , int pageIndex , Page jsonPage,PDStructureElement documentElement,Map<Integer, org.apache.pdfbox.pdmodel.common.COSObjectable> numTreeMap,TaggingMappingEngine taggingMappingEngine){

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
                        //--ADDED THIS GEMINI SHOULD CONFIRM THAT IT IS CORRECT
                        if (textMcid > highestMcid) highestMcid = textMcid;
                    }
                    liElement.appendKid(lbodyElement); // Attach to <LI>
                }

            }
            //We are in a table
            //We would need to get the table cellsfirst from the box
            else if("table".equalsIgnoreCase(box.getBoxclass())) {

                //We break the former list container
                currentListContainer =null;
                //We would first get our list of tables cells using the Table Spatial Matcher we created
                TableSpatialMatcher tableSpatialMatcher = new TableSpatialMatcher();
                var listOfPdfTextBoxes = taggingMappingEngine.listOfPdfTextBlocksInThePage;
                var listOfTableCells = tableSpatialMatcher.mapTextToTableCells(box,listOfPdfTextBoxes);

                //Now we have all the table cells we want to create our
                //Table structure Element , that will contain
                //The Tr elements for each row
                //Then each Tr , we will contain the list of Th or Td
                //The document element will be the head of the table document
                PDStructureElement tableStructureElement  = new PDStructureElement(StandardStructureTypes.TABLE, documentElement);
                //We set the page
                tableStructureElement.setPage(pdfPage);
                documentElement.appendKid(tableStructureElement);

                //Then we would loop through the rows of the table
                for (int i = 0; i < box.getTable().getRowCount(); i++) {
                    //For each row , we will create a corresponding Table row element
                    PDStructureElement tableRowStructureElement = new PDStructureElement(StandardStructureTypes.TR,tableStructureElement);
                    tableRowStructureElement.setPage(pdfPage);
                    //We would append it to the table structure element
                    tableStructureElement.appendKid(tableRowStructureElement);
                    //Now we get all the Table cells that are here
                    int index = i;
                    List<TableCell> currentRowTableCells = listOfTableCells.stream().filter((tableCell)-> tableCell.getRowIndex() == index).toList();
                    //Now we will just check if we are in the first row

                        for (TableCell cell :currentRowTableCells) {
                            //We will create header structure elements or Td elements

                            PDStructureElement tableRowChildStructureElement = (index==0)?
                                    new PDStructureElement(StandardStructureTypes.TH,tableRowStructureElement)
                                    : new PDStructureElement(StandardStructureTypes.TD,tableRowStructureElement);
                            //Add another accessibility tag

                            tableRowStructureElement.appendKid(tableRowChildStructureElement);
                            tableRowChildStructureElement.setPage(pdfPage);
                            //Then get the col span and row  and set it to the attributes of the element.IF ANY IS grater than 1
                            // We always need an attribute object if it's a Header (index == 0) OR if it has spans
                            if (index == 0 || cell.getRowSpan() > 1 || cell.getColumnSpan() > 1) {

                                PDTableAttributeObject tableAttributeObject = new PDTableAttributeObject();

                                if (cell.getColumnSpan() > 1) {
                                    tableAttributeObject.setColSpan(cell.getColumnSpan());
                                }
                                if (cell.getRowSpan() > 1) {
                                    tableAttributeObject.setRowSpan(cell.getRowSpan());
                                }

                                // Now ALL headers in the top row will get the Scope attribute!
                                if (index == 0) {
                                    tableAttributeObject.setScope(PDTableAttributeObject.SCOPE_COLUMN);
                                }

                                tableRowChildStructureElement.addAttribute(tableAttributeObject);
                            }

                            //Then our P element , as it is enclosed in the TD or TH element
                            PDStructureElement pElement = new PDStructureElement(StandardStructureTypes.P, tableRowChildStructureElement);
                            pElement.setPage(pdfPage);
                            tableRowChildStructureElement.appendKid(pElement);

                            //Then get the MCID numbers assigned to each text line
                            //Attach it to the P tags
                            var mcidNumbers = cell.pdfTextLines.stream().map(PdfTextLine::getAssignedMcid).toList();

                            for(Integer mcidNumber : mcidNumbers){
                                if(mcidNumber == -1) continue;
                                pElement.appendKid(mcidNumber);
                                //Then do the same to the mcid map
                                mcidToElementMap.put(mcidNumber,pElement);

                                //Then set the highest MCID
                                highestMcid =Math.max(highestMcid,mcidNumber);
                            }

                            }



                            //Then get all the text
                    }

                } else{

                // Break the list chain! If we hit a normal paragraph, the list is over.
                currentListContainer = null;

                // Create the Semantic Element ( <H1> or <P>)
                var mappedHeadings = pagesAndThierMappedHeadersFonts.get(pageIndex);
                var pdfTag = translateBoxClassToPdfTag(box, mappedHeadings);
                PDStructureElement element = new PDStructureElement(pdfTag, documentElement);
                element.setPage(pdfPage); // Tell the element which physical page it lives on

                //If it is an Image we will add an Alt Text

                if (StandardStructureTypes.Figure.equals(pdfTag)) {
                    // PDF/UA strictly requires Alt text for all figures.
                    element.setAlternateDescription("Image extracted from document");
                }

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

        //We will need to set the Language of the Document Catalog
        injectDocumentMetadataAndLanguage();
        //Bild the Document Outline
        buildDocumentOutline();


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
            buildStructureTreeForEachPage(pdfDocument.getPage(i),i,jsonPage,documentStructureElement,numTreeMap,taggingMappingEngines.get(i));
        }
        //Then set the parent tree here

        PDNumberTreeNode parentTree = new PDNumberTreeNode(COSArray.class);
        parentTree.setNumbers(numTreeMap);
        treeRoot.setParentTree(parentTree);
    }
    //We want to inject XMP Metadata and the Language to the PDF
    public void injectDocumentMetadataAndLanguage(){
    PDDocumentCatalog documentCatalog = pdfDocument.getDocumentCatalog();

    //We want to get the catalog
        //Lets get the Language from the Document if not

//        Let the document use its default Language

    //Set the Standard Document Information Dictionary
        PDDocumentInformation pdDocumentInformation = pdfDocument.getDocumentInformation();
        PdfMetadata jsonMeta = pdfJsonData.getMetadata();

        if (jsonMeta != null) {
            if (jsonMeta.getTitle() != null && !jsonMeta.getTitle().isEmpty()) {
                //If there is a title
                pdDocumentInformation.setTitle(jsonMeta.getTitle());
            } else {
                pdDocumentInformation.setTitle("Auto-Tagged Document"); // PDF/UA absolutely requires a title!
            }
            pdDocumentInformation.setAuthor(jsonMeta.getAuthor());
            pdDocumentInformation.setSubject(jsonMeta.getSubject());
            pdDocumentInformation.setCreator(jsonMeta.getCreator());
            pdDocumentInformation.setProducer(jsonMeta.getProducer());
        }
        pdfDocument.setDocumentInformation(pdDocumentInformation);
        //Set the XMP Metadata for Proper Validation by Vera PDF
        try {
            XMPMetadata xmpMetadata =XMPMetadata.createXMPMetadata();

            //We want to create a Dublin Core Scheme that will hold the title and the author
            DublinCoreSchema dublinCoreSchema = xmpMetadata.createAndAddDublinCoreSchema();
            dublinCoreSchema.setTitle(pdDocumentInformation.getTitle());
            if (pdDocumentInformation.getAuthor() != null && !pdDocumentInformation.getAuthor().isEmpty()) {
                dublinCoreSchema.addCreator(pdDocumentInformation.getAuthor());
            }
            // XMP Basic Schema (Holds the Creator Tool info)
            XMPBasicSchema xmpBasic = xmpMetadata.createAndAddXMPBasicSchema();
            xmpBasic.setCreatorTool(pdDocumentInformation.getProducer());

            // --- ADD THIS BLOCK: PDF/UA Identification Schema ---
            org.apache.xmpbox.schema.XMPSchema pdfuaSchema = new org.apache.xmpbox.schema.XMPSchema(
                    xmpMetadata,
                    "http://www.aiim.org/pdfua/ns/id/", // The official PDF/UA namespace
                    "pdfuaid",                          // The official prefix
                    "pdfua Identification Schema"       // Description
            );
            pdfuaSchema.setIntegerPropertyValue("part", 1); // We are declaring compliance with PDF/UA Part 1
            xmpMetadata.addSchema(pdfuaSchema);
            // Serialize the XMP data into an XML byte stream
            XmpSerializer serializer = new XmpSerializer();
            ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
            serializer.serialize(xmpMetadata, byteOutputStream, true);

            // Inject the XML stream into the PDF Catalog
            PDMetadata metadataStream = new PDMetadata(pdfDocument);
            metadataStream.importXMPMetadata(byteOutputStream.toByteArray());
            documentCatalog.setMetadata(metadataStream);
        } catch (Exception e) {

                System.err.println("Failed to inject XMP Metadata: " + e.getMessage());
        }
    }
    //We want to get the highest  and second highest font sizes and third highes font sizes from the fonts on a page
    public List<FontStyle> getFontsSizesOrderedBySizeAndNumberOfTimesAppearedInPdfPage(Page pdfJsonPage) {

        // 1. Extract all Spans safely (Your existing code)
        var allSpans = pdfJsonPage.getBoxes().stream()
                .filter(box -> box.getTextlines() != null)
                .flatMap(box -> box.getTextlines().stream())
                .filter(line -> line.getSpans() != null)
                .flatMap(line -> line.getSpans().stream())
                .toList();

        // 2. Map Spans to FontStyle objects
        var allFonts = allSpans.stream().map(span -> {
            var newFontStyle = new FontStyle();
            newFontStyle.setFontSize(span.getSize());
            newFontStyle.setName(span.getFont());
            return newFontStyle;
        });

        // 3. Group by the FontStyle (uses your EqualsAndHashCode) AND Count the occurrences!
        Map<FontStyle, Long> groupedAndCountedFonts = allFonts.collect(
                Collectors.groupingBy(fontStyle -> fontStyle, Collectors.counting())
        );

        // 4. Map the counts back into the objects, and SORT them!
        return groupedAndCountedFonts.entrySet().stream()
                .map(entry -> {
                    FontStyle fontStyle = entry.getKey();
                    // Inject the count back into your appearance field
                    fontStyle.setAppearance(entry.getValue().intValue());
                    return fontStyle;
                })
                // 5. Sort: First by Size (Largest to Smallest), then by Appearance (Most to Least)
                .sorted(
                        Comparator.comparingDouble(FontStyle::getFontSize).reversed()
                                .thenComparingInt(FontStyle::getAppearance).reversed()
                )
                .toList();
    }
    //Now dynamically assign the headings to them
    public Map<Double, String> buildDynamicHeadingMap(List<FontStyle> pageFonts) {
        Map<Double, String> headingMap = new HashMap<>();
        if (pageFonts == null || pageFonts.isEmpty()) return headingMap;

        // 1. Find the Baseline Font (The one used the most times on the page)
        FontStyle baselineFont = pageFonts.stream()
                .max(Comparator.comparingInt(FontStyle::getAppearance))
                .orElse(pageFonts.get(0));

        // 2. Extract all distinct sizes that are LARGER than the baseline
        List<Double> headingSizes = pageFonts.stream()
                .map(FontStyle::getFontSize)
                .filter(size -> size > baselineFont.getFontSize())
                .distinct()
                .sorted(Comparator.reverseOrder()) // Sort Largest to Smallest
                .toList();

        // 3. Assign H1 to the largest, H2 to the second largest, etc.
        int level = 1;
        for (Double size : headingSizes) {
            if (level > 6) break; // PDF standard stops at H6
            headingMap.put(size, StandardStructureTypes.H + level); // Generates "H1", "H2"...
            level++;
        }

        return headingMap;
    }

    /**
     * Then handling Document Outline
     * **/


    public void buildDocumentOutline() {
        List<List<Object>> tocData = pdfJsonData.getToc();

        // If Docling didn't find any headers, skip building the outline
        if (tocData == null || tocData.isEmpty()) {
            return;
        }

        PDDocumentOutline outline = new PDDocumentOutline();
        pdfDocument.getDocumentCatalog().setDocumentOutline(outline);

        // We use an array to track the last seen item at each heading level (up to H6)
        // Level 0 is the root outline. Level 1 is H1, Level 2 is H2, etc.
        PDOutlineNode[] lastItemAtLevel = new PDOutlineNode[10];
        lastItemAtLevel[0] = outline;

        for (List<Object> tocEntry : tocData) {
            if (tocEntry.size() < 3) continue;

            try {
                // Parse Docling's array: [level, "Title", pageNumber]
                int level = ((Number) tocEntry.get(0)).intValue();
                String title = (String) tocEntry.get(1);
                int pageNum = ((Number) tocEntry.get(2)).intValue();

                // Create the visual bookmark
                PDOutlineItem bookmark = new PDOutlineItem();
                bookmark.setTitle(title.trim());

                // Create the clickable destination
                // Note: Docling page numbers are usually 1-based, PDFBox is 0-based
                int pdfBoxPageIndex = pageNum - 1;
                if (pdfBoxPageIndex >= 0 && pdfBoxPageIndex < pdfDocument.getNumberOfPages()) {
                    PDPageFitWidthDestination dest = new PDPageFitWidthDestination();
                    dest.setPage(pdfDocument.getPage(pdfBoxPageIndex));
                    bookmark.setDestination(dest);
                }

                // Find the correct parent to attach this bookmark to
                PDOutlineNode parent = outline; // Default to root
                // Search backwards to find the nearest valid parent
                for (int i = level - 1; i >= 0; i--) {
                    if (lastItemAtLevel[i] != null) {
                        parent = lastItemAtLevel[i];
                        break;
                    }
                }

                // Attach it to the tree
                parent.addLast(bookmark);

                // Update our tracker for this level
                lastItemAtLevel[level] = bookmark;

                // Clear out any deeper levels so H3 doesn't accidentally attach to an old H2
                for (int i = level + 1; i < lastItemAtLevel.length; i++) {
                    lastItemAtLevel[i] = null;
                }

            } catch (Exception e) {
                System.err.println("Skipped malformed TOC entry: " + e.getMessage());
            }
        }
    }

    //Then now the final looping
    //The final form

    public void transformPDFDocumentAccessibility() throws IOException {

        //We want to pre compute the fonts

        mapPagesAndThierFontStyles();
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



    public String translateBoxClassToPdfTag(Box box, Map<Double, String> headingMap) {
        if (box == null || box.getBoxclass() == null) return StandardStructureTypes.P;

        switch (box.getBoxclass().toLowerCase()) {
            case "section-header":
                double boxFontSize = 0.0;

                // Extract the font size from the first span in this box
                if (box.getTextlines() != null) {
                    boxFontSize = box.getTextlines().stream()
                            .filter(line -> line.getSpans() != null)
                            .flatMap(line -> line.getSpans().stream())
                            .findFirst()
                            .map(Span::getSize)
                            .orElse(0.0);
                }

                // Look up the size in our dynamic map!
                // If it's not in the map (e.g. Docling got confused and called baseline text a header), default to P
                return headingMap.getOrDefault(boxFontSize, StandardStructureTypes.P);

            case "list-item":
                return StandardStructureTypes.LI;
            case "picture":
                return StandardStructureTypes.Figure;
            case "table":
                return StandardStructureTypes.TABLE;
            case "text":
            case "page-footer":
                return StandardStructureTypes.P;
            default:
                return StandardStructureTypes.P;
        }
    }

}
