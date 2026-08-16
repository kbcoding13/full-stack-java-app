package com.example.inventory.imports;

import com.example.inventory.imports.ImportDtos.ExportResult;
import com.example.inventory.imports.ImportDtos.ImportResult;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class ImportExportController {

    private final ProductImportService importService;
    private final InventoryExportService exportService;

    public ImportExportController(ProductImportService importService, InventoryExportService exportService) {
        this.importService = importService;
        this.exportService = exportService;
    }

    /** Proxy upload: the file is validated and parsed server-side before anything is trusted. */
    @PostMapping(value = "/imports/products", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult importProducts(@RequestParam("file") MultipartFile file) {
        return importService.importProducts(file);
    }

    /** Returns a presigned URL rather than the bytes, so large exports do not tie up the API. */
    @GetMapping("/exports/inventory")
    public ExportResult exportInventory() {
        return exportService.exportInventory();
    }
}
