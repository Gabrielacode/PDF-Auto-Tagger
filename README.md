# PDF Auto-Tagger API 

An enterprise-grade Spring Boot microservice that transforms raw, untagged PDFs into fully accessible, 100% **ISO 14289-1 (PDF/UA-1)** compliant documents.

Powered by **Apache PDFBox** and machine-learning layout data (via Docling JSON), this engine autonomously injects semantic structure, metadata, and navigational bookmarks into chaotic PDF byte streams.


---

##  Features

* **Dynamic Typography Engine:** Mathematically analyzes page font sizes to dynamically generate true `<H1>`, `<H2>`, `<H3>`, and `<P>` semantic hierarchies.
* **Complex Table Matrices:** Automatically calculates `RowSpans`, `ColSpans`, and `<TH>` `Scope` attributes for nested data grids.
* **Artifact Handling:** Traps and hides non-semantic visual noise (page numbers, decorative background images, and hundreds of vector graphic path-painting operators) using `BMC /Artifact` tags.
* **Smart Picture Handling:** Intercepts `Do` operators to generate `<Figure>` elements and injects strict PDF/UA Alternate Text (`/Alt`).
* **Interactive Navigation Tree:** Parses flat arrays into a 3D nested PDF Bookmark menu (Document Outline) for instant jumping between chapters.
* **Metadata Injection:** Auto-generates the mandatory XMP XML streams, Dublin Core schemas, and the strict `<pdfuaid:part>1</pdfuaid:part>` extension schema.

---

##  API Documentation

The microservice exposes a single, powerful multipart endpoint to process documents synchronously.

### `POST /api/v1/accessibility/tag-pdf`

**Request Type:** `multipart/form-data`

| Parameter | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `pdfFile` | `File` | Yes | The raw, untagged PDF document. |
| `jsonFile` | `File` | Yes | The parsed layout JSON (e.g., from Docling) containing bounding boxes, text blocks, and the TOC array. |
| `skipMarkedFiles`| `Boolean`| No | Defaults to `false`. If `true`, the engine checks the PDF's `MarkInfo` dictionary and instantly returns the file untouched if it is already tagged. |

**cURL Example:**
```bash
curl -X POST http://localhost:8080/api/v1/accessibility/tag-pdf \
  -H "Content-Type: multipart/form-data" \
  -F "pdfFile=@/path/to/raw_document.pdf" \
  -F "jsonFile=@/path/to/docling_output.json" \
  -F "skipMarkedFiles=true" --output tagged_document.pdf

```
##  Docker Deployment

The project includes a highly optimized, multi-stage Dockerfile.

**Note: The runtime image uses eclipse-temurin:21-jre-jammy (Ubuntu-based) instead of alpine. Alpine Linux strips out essential OS-level font metrics, which can cause Apache PDFBox to crash with NullPointerExceptions during text parsing. Jammy ensures total font compatibility**

##### **1. Build the Image**
```bash
docker build -t pdf-tagger-api .
```

##### **2. Run the container**
```bash
docker run -p 8080:8080 pdf-tagger-api
```

The API will now be live at http://localhost:8080.

##  Short Breakdown on Architecture

**1. Spatial Mapping:**  
 The program uses Axis Aligned Bounding Boxes Collision Techniques to map the JSON Bounding Boxes of the page to Text Matrices to find which `Tj`, `'` , `"` or any other Text Drawing Operator is to be marked   
This also applies to Image Drawing Operators `Do` and other essential Operators.

**2. PDF Stream Rewrite and Operator Tagging:**  
In this pass , the program , then gets the full byte stream of the PDF, analyzes the mapped Operators from the previous pass  
, and rewrites the PDF stream  ,wraps the Text Operators `Tj` , `'`,`"` ,Image Drawing `Do` and  Path Painting Operators `f`,`re`in  `BDC` (Begin Dictionary Content) blocks with linked MCIDs, and marks non-essentials  into `BMC /Artifact` blocks.  

**3. PDF Document Structure Root and Metadata Generation:**  
After the PDF Stream for each page has been rewritten, it then builds the Structure Tree Root, nesting the proper Structure Tree Elements.
The Parent Tree arrays are linked 1-to-1 with the page MCIDs.
The proper Metadata and Language is embedded into the Structure Tree Root and the document is marked as tagged.





