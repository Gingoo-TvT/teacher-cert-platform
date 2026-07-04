package cn.edu.gpnu.platform.exchange.support;

import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.exchange.model.ExchangeColumn;
import cn.edu.gpnu.platform.exchange.model.ExchangeStandardRow;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 44b（§7.3 证书导出内存）：导出/导入回执工作簿的写入统一改为 {@link SXSSFWorkbook} 流式窗口写，
 * 不再用 {@code XSSFWorkbook} 把整表 DOM 一次性驻留堆内存——大批量导出（数千至数万行）时可显著降低峰值堆占用。
 * 行访问窗口 {@link #ROW_ACCESS_WINDOW} 仅需大于下拉目录（性别/证件类型/学段学科等字典表，均为几到几十项）的最大行数，
 * 与实际导出行数无关；{@code applyDropdowns} 对隐藏 sheet 的 {@code getRow} 复用、以及对主表的
 * {@code addValidationData}（按固定行区间写校验元数据，不依赖行是否已刷盘）在该窗口下行为与原 XSSFWorkbook 完全一致。
 * 产出仍是标准 OOXML .xlsx 字节流，列结构/样式/取值与既有断言（Phase24AcceptanceIT 等）不变。
 * POI 5.2.5 的 {@code SXSSFWorkbook#close()} 不清理已刷盘的行缓存临时文件，故显式 {@code dispose()}（见
 * {@link #disposeQuietly}）避免临时文件泄漏；两处清理都做防御性吞异常，确保不掩盖已生成的字节结果。
 */
@Slf4j
@Component
public class ExchangeExcelHelper {

    public static final String STANDARD_SHEET = "标准上报表";
    public static final String ERROR_SHEET = "异常数据表";
    public static final String TEXT_FORMAT = "@";

    private static final int ROW_ACCESS_WINDOW = 200;

    private final DataFormatter formatter = new DataFormatter();

    public byte[] writeStandardWorkbook(List<ExchangeStandardRow> rows, Map<String, List<String>> dropdowns) {
        SXSSFWorkbook workbook = newStreamingWorkbook();
        try {
            CellStyle textStyle = textStyle(workbook);
            CellStyle headerStyle = headerStyle(workbook);
            Sheet sheet = workbook.createSheet(STANDARD_SHEET);
            writeHeader(sheet, headerStyle);
            for (int i = 0; i < rows.size(); i++) {
                Row excelRow = sheet.createRow(i + 1);
                ExchangeStandardRow data = rows.get(i);
                for (int c = 0; c < ExchangeColumn.ALL.size(); c++) {
                    Cell cell = excelRow.createCell(c, CellType.STRING);
                    cell.setCellStyle(textStyle);
                    String value = ExchangeColumn.ALL.get(c).value(data);
                    cell.setCellValue(value == null ? "" : value);
                }
            }
            applyTextFormat(sheet, textStyle);
            applyDropdowns(workbook, sheet, dropdowns == null ? Map.of() : dropdowns);
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                workbook.write(out);
                return out.toByteArray();
            }
        } catch (IOException e) {
            throw new BizException("生成Excel失败");
        } finally {
            disposeQuietly(workbook);
        }
    }

    public byte[] writeErrorWorkbook(List<ErrorRow> rows) {
        List<List<String>> tableRows = rows.stream()
                .map(item -> List.of(
                        safe(item.batchNo()),
                        String.valueOf(item.rowNo()),
                        safe(item.studentNo()),
                        safe(item.studentName()),
                        safe(item.fieldName()),
                        safe(item.errorValue()),
                        safe(item.errorReason()),
                        safe(item.suggestion())
                ))
                .toList();
        return writeTableWorkbook(ERROR_SHEET, List.of("批次号", "行号", "学号", "姓名", "字段", "错误值", "错误原因", "建议处理方式"), tableRows);
    }

    public byte[] writeTableWorkbook(String sheetName, List<String> headers, List<List<String>> rows) {
        SXSSFWorkbook workbook = newStreamingWorkbook();
        try {
            CellStyle textStyle = textStyle(workbook);
            CellStyle headerStyle = headerStyle(workbook);
            Sheet sheet = workbook.createSheet(StringUtils.hasText(sheetName) ? sheetName : "导出数据");
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = header.createCell(i, CellType.STRING);
                cell.setCellStyle(headerStyle);
                cell.setCellValue(headers.get(i));
                sheet.setDefaultColumnStyle(i, textStyle);
                sheet.setColumnWidth(i, 20 * 256);
            }
            for (int r = 0; r < rows.size(); r++) {
                List<String> values = rows.get(r);
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < values.size(); c++) {
                    Cell cell = row.createCell(c, CellType.STRING);
                    cell.setCellStyle(textStyle);
                    cell.setCellValue(values.get(c) == null ? "" : values.get(c));
                }
            }
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                workbook.write(out);
                return out.toByteArray();
            }
        } catch (IOException e) {
            throw new BizException("生成Excel失败");
        } finally {
            disposeQuietly(workbook);
        }
    }

    public List<ReadRow> readStandardRows(byte[] content) {
        try (InputStream in = new ByteArrayInputStream(content); Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                return List.of();
            }
            validateHeaders(sheet);
            List<ReadRow> rows = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row excelRow = sheet.getRow(r);
                if (excelRow == null || blankRow(excelRow)) {
                    continue;
                }
                ExchangeStandardRow data = new ExchangeStandardRow();
                for (int c = 0; c < ExchangeColumn.ALL.size(); c++) {
                    ExchangeColumn.ALL.get(c).set(data, cellText(excelRow.getCell(c)));
                }
                rows.add(new ReadRow(r + 1, data));
            }
            return rows;
        } catch (IOException e) {
            throw new BizException("读取Excel失败");
        }
    }

    private void validateHeaders(Sheet sheet) {
        Row header = sheet.getRow(0);
        if (header == null) {
            throw new BizException("Excel表头不能为空");
        }
        for (int c = 0; c < ExchangeColumn.ALL.size(); c++) {
            String actual = cellText(header.getCell(c));
            String expected = ExchangeColumn.ALL.get(c).header();
            if (!expected.equals(actual)) {
                throw new BizException("Excel表头不匹配: " + ExchangeColumn.ALL.get(c).letter() + "列应为" + expected);
            }
        }
    }

    private void writeHeader(Sheet sheet, CellStyle headerStyle) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < ExchangeColumn.ALL.size(); i++) {
            ExchangeColumn column = ExchangeColumn.ALL.get(i);
            Cell cell = header.createCell(i, CellType.STRING);
            cell.setCellStyle(headerStyle);
            cell.setCellValue(column.header());
            sheet.setColumnWidth(i, Math.max(12, column.header().length() + 4) * 256);
        }
    }

    private void applyTextFormat(Sheet sheet, CellStyle textStyle) {
        for (int i = 0; i < ExchangeColumn.ALL.size(); i++) {
            sheet.setDefaultColumnStyle(i, textStyle);
        }
    }

    private void applyDropdowns(Workbook workbook, Sheet sheet, Map<String, List<String>> dropdowns) {
        if (dropdowns.isEmpty()) {
            return;
        }
        Sheet hidden = workbook.createSheet("_options");
        workbook.setSheetHidden(workbook.getSheetIndex(hidden), true);
        int optionColumn = 0;
        Map<String, String> namedRanges = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : dropdowns.entrySet()) {
            List<String> values = entry.getValue().stream().filter(StringUtils::hasText).distinct().toList();
            if (values.isEmpty()) {
                continue;
            }
            for (int r = 0; r < values.size(); r++) {
                Row row = hidden.getRow(r);
                if (row == null) {
                    row = hidden.createRow(r);
                }
                row.createCell(optionColumn, CellType.STRING).setCellValue(values.get(r));
            }
            String rangeName = "opt_" + optionColumn;
            Name name = workbook.createName();
            name.setNameName(rangeName);
            String col = columnName(optionColumn);
            name.setRefersToFormula("_options!$" + col + "$1:$" + col + "$" + values.size());
            namedRanges.put(entry.getKey(), rangeName);
            optionColumn++;
        }
        DataValidationHelper helper = sheet.getDataValidationHelper();
        addValidation(helper, sheet, namedRanges.get("gender"), 5);
        addValidation(helper, sheet, namedRanges.get("idCardType"), 6);
        addValidation(helper, sheet, namedRanges.get("identityType"), 9);
        addValidation(helper, sheet, namedRanges.get("educationLevel"), 15);
        addValidation(helper, sheet, namedRanges.get("trainingGoal"), 16);
        addValidation(helper, sheet, namedRanges.get("internshipOrgMode"), 17);
        addValidation(helper, sheet, namedRanges.get("internshipLocation"), 18);
        addValidation(helper, sheet, namedRanges.get("teachingSegment"), 19);
        addValidation(helper, sheet, namedRanges.get("teachingSubject"), 20);
        addValidation(helper, sheet, namedRanges.get("interviewOrgMode"), 21);
    }

    private void addValidation(DataValidationHelper helper, Sheet sheet, String rangeName, int columnIndex) {
        if (!StringUtils.hasText(rangeName)) {
            return;
        }
        DataValidationConstraint constraint = helper.createFormulaListConstraint(rangeName);
        CellRangeAddressList regions = new CellRangeAddressList(1, 1000, columnIndex, columnIndex);
        DataValidation validation = helper.createValidation(constraint, regions);
        validation.setSuppressDropDownArrow(true);
        validation.setShowErrorBox(true);
        sheet.addValidationData(validation);
    }

    private String columnName(int zeroBased) {
        StringBuilder builder = new StringBuilder();
        int value = zeroBased + 1;
        while (value > 0) {
            int rem = (value - 1) % 26;
            builder.insert(0, (char) ('A' + rem));
            value = (value - 1) / 26;
        }
        return builder.toString();
    }

    private boolean blankRow(Row row) {
        for (int c = 0; c < ExchangeColumn.ALL.size(); c++) {
            if (StringUtils.hasText(cellText(row.getCell(c)))) {
                return false;
            }
        }
        return true;
    }

    private String cellText(Cell cell) {
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell).trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private SXSSFWorkbook newStreamingWorkbook() {
        SXSSFWorkbook workbook = new SXSSFWorkbook(ROW_ACCESS_WINDOW);
        workbook.setCompressTempFiles(true);
        return workbook;
    }

    private void disposeQuietly(SXSSFWorkbook workbook) {
        try {
            if (!workbook.dispose()) {
                log.warn("导出临时文件清理未完全成功（可能已被清理或文件被占用）");
            }
        } catch (Exception e) {
            log.warn("清理导出临时文件失败", e);
        }
        try {
            workbook.close();
        } catch (Exception e) {
            log.warn("关闭导出工作簿失败", e);
        }
    }

    private CellStyle textStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat(TEXT_FORMAT));
        return style;
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = textStyle(workbook);
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    public record ReadRow(int rowNo, ExchangeStandardRow row) {
    }

    public record ErrorRow(String batchNo, int rowNo, String studentNo, String studentName,
                           String fieldName, String errorValue, String errorReason, String suggestion) {
    }
}
