package com.example.inventory.imports;

import com.example.inventory.common.ApiExceptions.StorageException;
import com.example.inventory.config.AppProperties;
import com.example.inventory.imports.ImportDtos.ExportResult;
import com.example.inventory.product.ProductRepository;
import com.example.inventory.product.ProductRow;
import com.example.inventory.storage.StorageService;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates an inventory snapshot as CSV, writes it to S3, and hands back a short-lived
 * presigned GET. The file is never streamed through the response, so a large export does not
 * hold a connection open.
 */
@Service
public class InventoryExportService {

    private static final int PAGE_SIZE = 500;

    private static final String[] HEADERS = {
        "sku", "name", "description", "category", "supplier", "unitPrice", "reorderLevel",
        "quantityOnHand", "lowStock"
    };

    private final ProductRepository productRepository;
    private final StorageService storageService;
    private final AppProperties.Storage properties;

    public InventoryExportService(
            ProductRepository productRepository, StorageService storageService, AppProperties properties) {
        this.productRepository = productRepository;
        this.storageService = storageService;
        this.properties = properties.storage();
    }

    @Transactional(readOnly = true)
    public ExportResult exportInventory() {
        StringWriter buffer = new StringWriter();
        int rowCount = 0;

        try (CSVPrinter printer =
                new CSVPrinter(buffer, CSVFormat.DEFAULT.builder().setHeader(HEADERS).get())) {

            // Paged rather than a single unbounded query, so a large catalog cannot exhaust heap.
            int pageNumber = 0;
            Page<ProductRow> page;

            do {
                page = productRepository.search(
                        null, null, null, false, PageRequest.of(pageNumber, PAGE_SIZE, Sort.by("sku")));

                for (ProductRow row : page.getContent()) {
                    printer.printRecord(
                            row.sku(),
                            row.name(),
                            row.description(),
                            row.categoryName(),
                            row.supplierName(),
                            row.unitPrice(),
                            row.reorderLevel(),
                            row.quantityOnHand(),
                            row.isLowStock());
                    rowCount++;
                }
                pageNumber++;
            } while (page.hasNext());

        } catch (IOException ex) {
            throw new StorageException("Failed to generate the inventory CSV", ex);
        }

        String objectKey = storageService.exportKey();
        storageService.upload(objectKey, buffer.toString().getBytes(StandardCharsets.UTF_8), "text/csv");

        return new ExportResult(
                objectKey,
                storageService.presignDownload(objectKey),
                rowCount,
                properties.presignTtl().toSeconds());
    }
}
