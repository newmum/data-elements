package com.linewell.dataelement.datareport;

import com.linewell.dataelement.model.common.CommonResponse;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Receives the original report workbook and delegates the transfer to the ODS
 * non-structured-storage datasource.  The browser never receives FTP details.
 */
@RestController
@RequestMapping("/dst/database/report-file")
public class FtpReportUploadController {

    private final FtpReportUploadService service;

    public FtpReportUploadController(FtpReportUploadService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResponse<Map<String, Object>> upload(
            @RequestParam("datasourceId") String datasourceId,
            @RequestParam("tableName") String tableName,
            @RequestParam("file") MultipartFile file
    ) {
        return CommonResponse.success(service.upload(datasourceId, tableName, file));
    }
}
