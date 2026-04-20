package com.sample.pdfautotagging.models.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PdfData {
    private String filename;

//    @JsonProperty("page_count")
//    private int pageCount;

    private List<List<Object>> toc; // Can be updated to a specific type if TOC structure is known
    private List<Page> pages;

//    @JsonProperty("full_ocred")
//    private boolean fullOcred;

//    @JsonProperty("text_ocred")
//    private boolean textOcred;

//    private List<FullTextItem> fulltext;
//    private List<Object> words; // Empty in JSON, type can be adjusted later
//    private List<PdfLink> links;
    private PdfMetadata metadata;

//    @JsonProperty("from_bytes")
//    private boolean fromBytes;
//
//    @JsonProperty("image_dpi")
//    private int imageDpi;
//
//    @JsonProperty("image_format")
//    private String imageFormat;
//
//    @JsonProperty("image_path")
//    private String imagePath;
//
//    @JsonProperty("use_ocr")
//    private boolean useOcr;
//
//    @JsonProperty("form_fields")
//    private Object formFields;
//
//    @JsonProperty("force_text")
//    private boolean forceText;
//
//    @JsonProperty("embed_images")
//    private boolean embedImages;
//
//    @JsonProperty("write_images")
//    private boolean writeImages;
}
