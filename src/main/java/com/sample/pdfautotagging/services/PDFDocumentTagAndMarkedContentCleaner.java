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
import java.util.ArrayList;
import java.util.List;

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

        //Then we loop through it , analysing each line
        for (Object token : oldTokens) {
            if (token instanceof Operator operator) {
                String opName = operator.getName();

                switch (opName) {
                    case "EMC":
                        // It's a closing tag. Just ignore it (don't add it to the new list).
                        continue;

                    case "BMC":
                        // Pop 1 Operand , which will be the tag
                        if (!newTokens.isEmpty()) {
                            newTokens.remove(newTokens.size() - 1);
                        }
                        // Ignore the BMC operator itself
                        continue;

                    case "BDC":
                        // Pop 2 operands (The Dictionary (MCID Identifier), and then the Tag Name)
                        if (!newTokens.isEmpty()) {
                            newTokens.remove(newTokens.size() - 1); // Removes Dictionary
                        }
                        if (!newTokens.isEmpty()) {
                            newTokens.remove(newTokens.size() - 1); // Removes Tag Name
                        }
                        // Ignore the BDC operator itself
                        continue;

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
