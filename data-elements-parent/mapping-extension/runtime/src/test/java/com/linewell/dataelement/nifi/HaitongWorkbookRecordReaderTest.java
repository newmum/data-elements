package com.linewell.dataelement.nifi;

import org.apache.nifi.serialization.MalformedRecordException;
import org.apache.nifi.util.TestRunners;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HaitongWorkbookRecordReaderTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void preservesRegisteredPositionsAndCellDisplayWithStyleOnlyOverflow(boolean xlsx) throws Exception {
        var service = reader();
        var original = original(xlsx, false);
        try (var records = service.createRecordReader(Map.of(), new ByteArrayInputStream(original), original.length, null)) {
            assertEquals(List.of("来源名称", "column_1"), records.getSchema().getFieldNames());
            var first = records.nextRecord();
            assertEquals("0007", first.getAsString("来源名称"));
            assertEquals("后续字段", first.getAsString("column_1"));
            var next = records.nextRecord();
            assertEquals("0", next.getAsString("来源名称"));
            assertEquals("FALSE", next.getAsString("column_1"));
            assertNull(records.nextRecord());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void reportsRealValuesOutsideTheRegisteredExtent(boolean xlsx) throws Exception {
        var service = reader();
        var original = original(xlsx, true);
        try (var records = service.createRecordReader(Map.of(), new ByteArrayInputStream(original), original.length, null)) {
            assertEquals("0007", records.nextRecord().getAsString("来源名称"));
            assertThrows(MalformedRecordException.class, records::nextRecord);
        }
    }

    private static HaitongWorkbookRecordReader reader() throws Exception {
        var runner = TestRunners.newTestRunner(HaitongNormalizeJsonRecords.class);
        var service = new HaitongWorkbookRecordReader();
        runner.addControllerService("workbook", service);
        runner.setProperty(service, HaitongWorkbookRecordReader.LAYOUT, """
                {"sheet":"报告,原文","headerRow":2,"fields":["来源名称","column_1"],"titles":["来源名称",""]}
                """);
        runner.enableControllerService(service);
        return service;
    }

    private static byte[] original(boolean xlsx, boolean overflow) throws Exception {
        try (Workbook book = xlsx ? new XSSFWorkbook() : new HSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            book.createSheet("其他").createRow(0).createCell(0).setCellValue("另一张表的数据");
            var sheet = book.createSheet("报告,原文");
            var formatted = book.createCellStyle();
            formatted.setDataFormat(book.createDataFormat().getFormat("0000"));
            sheet.createRow(2).createCell(0).setCellValue("来源名称");
            var record = sheet.createRow(3);
            var display = record.createCell(0);
            display.setCellValue(7);
            display.setCellStyle(formatted);
            record.createCell(1).setCellValue("后续字段");
            record.createCell(7).setCellStyle(formatted);
            sheet.createRow(4).createCell(7).setCellStyle(formatted);
            var last = sheet.createRow(5);
            last.createCell(0).setCellValue(0);
            last.createCell(1).setCellValue(false);
            if (overflow) last.createCell(7).setCellValue("新增列");
            book.write(output);
            return output.toByteArray();
        }
    }
}
