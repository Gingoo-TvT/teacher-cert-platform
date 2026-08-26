package cn.edu.gpnu.platform.exchange.support;

import cn.idev.excel.ExcelReader;
import cn.idev.excel.FastExcel;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.enums.ReadDefaultReturnEnum;
import cn.idev.excel.event.AnalysisEventListener;
import cn.idev.excel.read.metadata.ReadSheet;
import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.exchange.model.ExchangeColumn;
import cn.edu.gpnu.platform.exchange.model.ExchangeStandardRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
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
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
@RequiredArgsConstructor
public class ExchangeExcelHelper {

    public static final String STANDARD_SHEET = "标准上报表";
    public static final String ERROR_SHEET = "异常数据表";
    public static final String TEXT_FORMAT = "@";

    private static final int ROW_ACCESS_WINDOW = 200;
    private static final int ZIP_SCAN_BUFFER_SIZE = 8192;
    private static final int MAX_OOXML_ENTRIES = 1024;
    private static final int MAX_OOXML_WORKSHEETS = 8;
    private static final long MAX_OOXML_METADATA_ENTRY_BYTES = 1024L * 1024L;

    private final ExchangeImportProperties importProperties;

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

    /**
     * 在把上传内容物化为 byte[] 之前先按 MultipartFile 声明大小拒绝，随后仍对实际字节长度复核。
     */
    public List<ReadRow> readStandardRows(MultipartFile file) {
        assertFileSize(file.getSize());
        try {
            return readStandardRows(file.getBytes());
        } catch (IOException e) {
            throw new BizException("读取Excel失败");
        }
    }

    public List<ReadRow> readStandardRows(byte[] content) {
        if (content == null) {
            throw new BizException("导入文件不能为空");
        }
        assertFileSize(content.length);
        assertExpandedSize(content);
        assertWorkbookStructure(content);
        StandardRowListener listener = new StandardRowListener();
        try (InputStream in = new ByteArrayInputStream(content);
             ExcelReader reader = FastExcel.read(in)
                     .autoCloseStream(false)
                     .autoTrim(false)
                     .ignoreEmptyRow(false)
                     .readDefaultReturn(ReadDefaultReturnEnum.STRING)
                     .build()) {
            ReadSheet sheet = FastExcel.readSheet(0)
                    .headRowNumber(1)
                    .registerReadListener(listener)
                    .build();
            reader.read(sheet);
            return listener.rows();
        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            throw new BizException("读取Excel失败");
        } catch (RuntimeException e) {
            BizException businessCause = businessCause(e);
            if (businessCause != null) {
                throw businessCause;
            }
            throw new BizException("读取Excel失败");
        }
    }

    /**
     * 在 FastExcel 为一整行构造 Map 前，先用常量内存检查 OOXML 结构。固定模板只允许 A-Z，
     * 因而伪造的重复行号、超宽单行、公式及纯空白超长值都会在对象物化前被拒绝。
     */
    private void assertWorkbookStructure(byte[] content) {
        int worksheetCount = 0;
        byte[] buffer = new byte[ZIP_SCAN_BUFFER_SIZE];
        try (ZipArchiveInputStream zip = new ZipArchiveInputStream(new ByteArrayInputStream(content))) {
            ZipArchiveEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name != null && name.startsWith("xl/worksheets/") && name.endsWith(".xml")) {
                    parseXmlEntry(zip, new WorksheetStructureHandler());
                    worksheetCount++;
                } else if ("xl/sharedStrings.xml".equals(name)) {
                    parseXmlEntry(zip, new SharedStringStructureHandler());
                } else {
                    while (zip.read(buffer) != -1) {
                        // 结构预检只需消费非目标条目，展开总量已在上一遍固定缓冲扫描中校验。
                    }
                }
            }
        } catch (BizException e) {
            throw e;
        } catch (IOException | ParserConfigurationException | SAXException e) {
            BizException businessCause = businessCause(e);
            if (businessCause == null && e instanceof SAXException saxException) {
                businessCause = businessCause(saxException.getException());
            }
            if (businessCause != null) {
                throw businessCause;
            }
            throw new BizException("Excel压缩包内容异常");
        }
        if (worksheetCount == 0) {
            throw new BizException("Excel压缩包内容异常");
        }
    }

    private void parseXmlEntry(InputStream input, DefaultHandler handler)
            throws ParserConfigurationException, SAXException, IOException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        SAXParser parser = factory.newSAXParser();
        parser.parse(new NonClosingInputStream(input), handler);
    }

    private String elementName(String localName, String qName) {
        if (StringUtils.hasText(localName)) {
            return localName;
        }
        int separator = qName == null ? -1 : qName.indexOf(':');
        return separator < 0 ? qName : qName.substring(separator + 1);
    }

    private int positiveNumber(String value, String label) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new NumberFormatException("not positive");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new BizException(label + "不合法");
        }
    }

    private int columnIndex(String cellReference) {
        if (!StringUtils.hasText(cellReference)) {
            return -1;
        }
        int index = 0;
        int position = 0;
        while (position < cellReference.length() && Character.isLetter(cellReference.charAt(position))) {
            char letter = Character.toUpperCase(cellReference.charAt(position));
            if (letter < 'A' || letter > 'Z') {
                throw new BizException("Excel单元格引用不合法");
            }
            index = index * 26 + (letter - 'A' + 1);
            if (index > ExchangeColumn.ALL.size()) {
                return index - 1;
            }
            position++;
        }
        if (position == 0) {
            throw new BizException("Excel单元格引用不合法");
        }
        return index - 1;
    }

    private void assertFileSize(long size) {
        if (size > importProperties.getMaxFileBytes()) {
            throw new BizException("Excel文件大小超过上限 " + importProperties.getMaxFileBytes() + " 字节");
        }
    }

    /**
     * 先顺序解压并计数 OOXML 包，再交给 SAX 读取器。只保留固定缓冲区，不驻留解压内容。
     */
    private void assertExpandedSize(byte[] content) {
        long expanded = 0L;
        int entryCount = 0;
        int worksheetCount = 0;
        boolean hasEntry = false;
        Set<String> entryNames = new HashSet<>();
        byte[] buffer = new byte[ZIP_SCAN_BUFFER_SIZE];
        try (ZipArchiveInputStream zip = new ZipArchiveInputStream(new ByteArrayInputStream(content))) {
            ZipArchiveEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                hasEntry = true;
                entryCount++;
                if (entryCount > MAX_OOXML_ENTRIES) {
                    throw new BizException("Excel压缩包条目数量超过上限 " + MAX_OOXML_ENTRIES);
                }
                String entryName = entry.getName();
                if (!StringUtils.hasText(entryName) || !entryNames.add(entryName)) {
                    throw new BizException("Excel压缩包含重复或无效条目");
                }
                if (entryName.startsWith("xl/worksheets/") && entryName.endsWith(".xml")) {
                    worksheetCount++;
                    if (worksheetCount > MAX_OOXML_WORKSHEETS) {
                        throw new BizException("Excel工作表数量超过上限 " + MAX_OOXML_WORKSHEETS);
                    }
                }
                long entryExpanded = 0L;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    expanded += read;
                    entryExpanded += read;
                    if (expanded > importProperties.getMaxExpandedBytes()) {
                        throw new BizException("Excel展开大小超过上限 "
                                + importProperties.getMaxExpandedBytes() + " 字节");
                    }
                    if (isObjectMetadata(entryName)
                            && entryExpanded > MAX_OOXML_METADATA_ENTRY_BYTES) {
                        throw new BizException("Excel元数据条目大小超过上限 "
                                + MAX_OOXML_METADATA_ENTRY_BYTES + " 字节");
                    }
                }
            }
        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            throw new BizException("Excel压缩包内容异常");
        }
        if (!hasEntry) {
            throw new BizException("Excel压缩包内容异常");
        }
    }

    private boolean isObjectMetadata(String entryName) {
        return "[Content_Types].xml".equals(entryName)
                || "xl/workbook.xml".equals(entryName)
                || "xl/styles.xml".equals(entryName)
                || entryName.endsWith(".rels");
    }

    private void validateHeaders(Map<Integer, String> header) {
        if (header == null || header.isEmpty()) {
            throw new BizException("Excel表头不能为空");
        }
        rejectExtraColumns(header, 1);
        for (int c = 0; c < ExchangeColumn.ALL.size(); c++) {
            String actual = text(header.get(c));
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

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private void rejectExtraColumns(Map<Integer, String> values, int rowNo) {
        values.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey() >= ExchangeColumn.ALL.size())
                .filter(entry -> StringUtils.hasText(text(entry.getValue())))
                .findFirst()
                .ifPresent(entry -> {
                    throw new BizException("Excel第" + rowNo + "行含标准模板之外的列");
                });
    }

    private BizException businessCause(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof BizException bizException) {
                return bizException;
            }
            current = current.getCause();
        }
        return null;
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

    private final class WorksheetStructureHandler extends DefaultHandler {

        private int rowEvents;
        private int lastExplicitRow;
        private int cellsInRow;
        private int cellCharacters;
        private boolean inRow;
        private boolean inCell;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            String name = elementName(localName, qName);
            if ("row".equals(name)) {
                startRow(attributes.getValue("r"));
            } else if ("c".equals(name)) {
                startCell(attributes.getValue("r"));
            } else if ("f".equals(name)) {
                throw new BizException("Excel导入不允许公式单元格");
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (!inCell || length == 0) {
                return;
            }
            cellCharacters += length;
            if (cellCharacters > importProperties.getMaxCellCharacters()) {
                throw new BizException("Excel单元格原始内容超过 "
                        + importProperties.getMaxCellCharacters() + " 个字符");
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            String name = elementName(localName, qName);
            if ("c".equals(name)) {
                inCell = false;
            } else if ("row".equals(name)) {
                inRow = false;
            }
        }

        private void startRow(String reference) {
            if (inRow) {
                throw new BizException("Excel行结构不合法");
            }
            rowEvents++;
            if (rowEvents > (long) importProperties.getMaxRows() + 1L) {
                throw new BizException("Excel数据行数超过上限 " + importProperties.getMaxRows());
            }
            if (StringUtils.hasText(reference)) {
                int row = positiveNumber(reference, "Excel行号");
                if (row <= lastExplicitRow) {
                    throw new BizException("Excel行号必须严格递增");
                }
                if (row > (long) importProperties.getMaxRows() + 1L) {
                    throw new BizException("Excel数据行数超过上限 " + importProperties.getMaxRows());
                }
                lastExplicitRow = row;
            }
            cellsInRow = 0;
            inRow = true;
        }

        private void startCell(String reference) {
            if (!inRow || inCell) {
                throw new BizException("Excel单元格结构不合法");
            }
            cellsInRow++;
            if (cellsInRow > ExchangeColumn.ALL.size()) {
                throw new BizException("Excel单行列数超过标准模板上限");
            }
            int column = columnIndex(reference);
            if (column >= ExchangeColumn.ALL.size()) {
                throw new BizException("Excel含标准模板之外的列");
            }
            cellCharacters = 0;
            inCell = true;
        }
    }

    private static final class NonClosingInputStream extends FilterInputStream {

        private NonClosingInputStream(InputStream input) {
            super(input);
        }

        @Override
        public void close() {
            // SAXParser 会主动关闭输入；ZIP 扫描器仍需继续读取后续条目。
        }
    }

    private final class SharedStringStructureHandler extends DefaultHandler {

        private long itemCount;
        private int itemCharacters;
        private boolean inItem;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            if (!"si".equals(elementName(localName, qName))) {
                return;
            }
            itemCount++;
            long maximumItems = ((long) importProperties.getMaxRows() + 1L) * ExchangeColumn.ALL.size();
            if (itemCount > maximumItems) {
                throw new BizException("Excel共享文本数量超过模板容量");
            }
            itemCharacters = 0;
            inItem = true;
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (!inItem || length == 0) {
                return;
            }
            itemCharacters += length;
            if (itemCharacters > importProperties.getMaxCellCharacters()) {
                throw new BizException("Excel共享文本内容超过 "
                        + importProperties.getMaxCellCharacters() + " 个字符");
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if ("si".equals(elementName(localName, qName))) {
                inItem = false;
            }
        }
    }

    private final class StandardRowListener extends AnalysisEventListener<Map<Integer, String>> {

        private final List<ReadRow> rows = new ArrayList<>();
        private boolean headerRead;
        private int rowEvents;

        @Override
        public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
            recordRowEvent();
            if (headerRead) {
                throw new BizException("Excel只能包含一个表头行");
            }
            validateHeaders(headMap);
            headerRead = true;
        }

        @Override
        public void invoke(Map<Integer, String> values, AnalysisContext context) {
            recordRowEvent();
            int zeroBasedRow = context.readRowHolder().getRowIndex();
            int rowNo = zeroBasedRow + 1;
            if (zeroBasedRow > importProperties.getMaxRows()) {
                throw new BizException("Excel数据行数超过上限 " + importProperties.getMaxRows());
            }
            rejectExtraColumns(values, rowNo);
            List<String> normalized = new ArrayList<>(ExchangeColumn.ALL.size());
            boolean blank = true;
            for (int c = 0; c < ExchangeColumn.ALL.size(); c++) {
                String value = text(values.get(c));
                if (value.length() > importProperties.getMaxCellCharacters()) {
                    throw new BizException("Excel第" + rowNo + "行"
                            + ExchangeColumn.ALL.get(c).letter() + "列内容超过 "
                            + importProperties.getMaxCellCharacters() + " 个字符");
                }
                normalized.add(value);
                blank = blank && !StringUtils.hasText(value);
            }
            if (blank) {
                return;
            }
            if (rows.size() >= importProperties.getMaxRows()) {
                throw new BizException("Excel数据行数超过上限 " + importProperties.getMaxRows());
            }
            ExchangeStandardRow data = new ExchangeStandardRow();
            for (int c = 0; c < ExchangeColumn.ALL.size(); c++) {
                ExchangeColumn.ALL.get(c).set(data, normalized.get(c));
            }
            rows.add(new ReadRow(rowNo, data));
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            if (!headerRead) {
                throw new BizException("Excel表头不能为空");
            }
        }

        private List<ReadRow> rows() {
            return List.copyOf(rows);
        }

        private void recordRowEvent() {
            rowEvents++;
            if (rowEvents > (long) importProperties.getMaxRows() + 1L) {
                throw new BizException("Excel数据行数超过上限 " + importProperties.getMaxRows());
            }
        }
    }

    public record ReadRow(int rowNo, ExchangeStandardRow row) {
    }

    public record ErrorRow(String batchNo, int rowNo, String studentNo, String studentName,
                           String fieldName, String errorValue, String errorReason, String suggestion) {
    }
}
