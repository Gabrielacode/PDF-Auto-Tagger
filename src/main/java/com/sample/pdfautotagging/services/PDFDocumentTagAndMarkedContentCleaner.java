package com.sample.pdfautotagging.services;

import org.apache.pdfbox.contentstream.PDContentStream;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDStream;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

public class PDFDocumentTagAndMarkedContentCleaner{

    final PDDocument pdfDocument ;



    public PDFDocumentTagAndMarkedContentCleaner(PDDocument pdfDocument) {
        this.pdfDocument = pdfDocument;
    }

    //If a Document has already been tagged and the user wants to remove the tagged  and the structure info ,
    //We do that here
    //First we parse the whole document page by page
    //Then we would remove the BDC, BMC and EMC Tags from there


    private  void cleanPdfPage(PDPage pdfPage) throws IOException {
        PDFStreamParser pdfStreamParser =   new PDFStreamParser(pdfPage);
        List<Object> oldTokens = pdfStreamParser.parse();
        List<Object> newTokens = new ArrayList<>();
        
        //We want to keep a stack for the operators we want to delete
        //We want to preserve the operator , for some marked content

        Deque<Boolean> keepStack = new ArrayDeque<>();

        //Then we loop through it , analysing each line
        for (Object token : oldTokens) {
            if (token instanceof Operator operator) {
                String opName = operator.getName();

                switch (opName) {
                    case "EMC":

                        // It's a closing tag. Check the stack to see if we should keep it
                        if (!keepStack.isEmpty()) {
                            boolean keep = keepStack.pop();
                            if (keep) {
                                newTokens.add(operator);
                            }
                        } else {
                            // Safe fallback just in case the PDF was already malformed an we didnt know whether to keep it
                            newTokens.add(operator);
                        }
                        break;


                    case "BMC":
                        //We want to do similar for the BDC
                        //If there is no supporting tag before it
                        if(!newTokens.isEmpty()){
                            //We want to get the Name
                            Object tagName = newTokens.get(newTokens.size()-1);
                            if (isOptionalContent(tagName)) {
                                keepStack.push(true);
                                newTokens.add(operator);
                            } else {
                                keepStack.push(false);
                                newTokens.remove(newTokens.size() - 1); // Remove Tag Name
                            }
                        }else{
                            keepStack.push(false);
                            //No need to remove
                        }
                        break;

                    case "BDC":
                        //We would first need to know if the  first two tokesn are available
                        if(newTokens.size()>=2){
                            //We would get the Name , before the MCID dictionary and check if it is OC
                            Object tagName = newTokens.get(newTokens.size()-2);
                            boolean isOCTaggedName = isOptionalContent(tagName);
                            //If it is true we want to keep the operator and then set is true to stack
                            if(isOCTaggedName){
                                keepStack.push(true);
                                newTokens.add(token);
                            }else{
                                //We dont want the /Name and the MCID dictionary so we remove it
                                //We want to also tell the stack to add is false so that when we reach the EMC , we can know whether to op it
                                keepStack.push(false);
                                newTokens.remove(newTokens.size()-1);
                                newTokens.remove(newTokens.size()-1);
                            }
                        }else {
                            //If is not greater than 2 now , that means there is no /Name or MCID Dictionary at the time
                            keepStack.push(false);
                        }
                        break;

                    default:
                        // Normal operators (Tj, BT, Do, etc.) pass through safely!
                        newTokens.add(operator);
                        break;
                }
            } else {
                // It's an operand (String, Dictionary, Number, Name). Add it!
                newTokens.add(token);
            }
        }

        //Then we would set th new list of tokens as the new pdf stream

        PDStream newContents = new PDStream(pdfDocument);
        try (OutputStream out = newContents.createOutputStream(COSName.FLATE_DECODE)) {
            ContentStreamWriter tokenWriter = new ContentStreamWriter(out);
            tokenWriter.writeTokens(newTokens);
        }
        pdfPage.setContents(newContents);

    }


    // Helper method to check if the tag is for PDF Layers (Optional Content)
    private boolean isOptionalContent(Object tagNameObj) {
        if (tagNameObj instanceof COSName) {
            COSName name = (COSName) tagNameObj;
            return "OC".equals(name.getName()); // "OC" stands for Optional Content
        }
        return false;
    }

    //Then the method that would clean the whole document and destroy the structure tree
    public void cleanDocument() throws IOException {
        // 1. Clean the physical byte stream on every single page
        for (PDPage page : pdfDocument.getPages()) {
            cleanPdfPage(page);

            // Remove the ParentTree bridge reference from the page
            page.getCOSObject().removeItem(COSName.STRUCT_PARENTS);
        }

        // 2. Clean the Document Catalog  OR /Root
        PDDocumentCatalog catalog = pdfDocument.getDocumentCatalog();

        // Destroy the Structure Tree Root
        catalog.setStructureTreeRoot(null);

        // Turn off the Tagged PDF flag
        if (catalog.getMarkInfo() != null) {
            catalog.getMarkInfo().setMarked(false);
        }
    }




}
