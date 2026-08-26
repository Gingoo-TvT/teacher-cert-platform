package cn.edu.gpnu.platform.exchange.support;

import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.exchange.model.ExchangeColumn;
import cn.edu.gpnu.platform.exchange.model.ExchangeStandardRow;
import cn.edu.gpnu.platform.system.config.DatabaseBackupProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExchangeImportResourceBudgetTest {

    @Test
    void invalidBudgetFailsDuringConfigurationValidation() {
        ExchangeImportProperties properties = new ExchangeImportProperties();
        properties.setMaxErrorDetails(0);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("资源预算必须为正数");
    }

    @Test
    void oversizedMultipartIsRejectedBeforeReadingBytes() throws IOException {
        ExchangeImportProperties properties = new ExchangeImportProperties();
        properties.setMaxFileBytes(4);
        ExchangeExcelHelper helper = new ExchangeExcelHelper(properties);
        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(5L);

        assertThatThrownBy(() -> helper.readStandardRows(file))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Excel文件大小超过上限");

        verify(file, never()).getBytes();
    }

    @Test
    void expandedWorkbookIsRejectedBeforeXssfParsing() {
        ExchangeImportProperties properties = new ExchangeImportProperties();
        ExchangeExcelHelper helper = new ExchangeExcelHelper(properties);
        byte[] workbook = helper.writeStandardWorkbook(List.of(row("1")), null);
        properties.setMaxExpandedBytes(32);

        assertThatThrownBy(() -> helper.readStandardRows(workbook))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Excel展开大小超过上限");
    }

    @Test
    void rowAndCellLimitsRejectBeforeBuildingTheFullBatch() {
        ExchangeImportProperties properties = new ExchangeImportProperties();
        ExchangeExcelHelper helper = new ExchangeExcelHelper(properties);
        byte[] twoRows = helper.writeStandardWorkbook(List.of(row("1"), row("2")), null);
        properties.setMaxRows(1);

        assertThatThrownBy(() -> helper.readStandardRows(twoRows))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Excel数据行数超过上限 1");

        properties.setMaxRows(20_000);
        properties.setMaxCellCharacters(64);
        byte[] longCell = helper.writeStandardWorkbook(List.of(row("x".repeat(65))), null);
        assertThatThrownBy(() -> helper.readStandardRows(longCell))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("超过 64 个字符");
    }

    @Test
    void manyCompressedPhysicalRowsAreRejectedBySaxBudget() {
        ExchangeImportProperties properties = new ExchangeImportProperties();
        properties.setMaxRows(100);
        ExchangeExcelHelper helper = new ExchangeExcelHelper(properties);
        byte[] workbook = minimalWorkbook(50_000, false);

        assertThatThrownBy(() -> helper.readStandardRows(workbook))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Excel数据行数超过上限 100");
    }

    @Test
    void nonEmptyColumnsOutsideStandardTemplateAreRejected() {
        ExchangeImportProperties properties = new ExchangeImportProperties();
        ExchangeExcelHelper helper = new ExchangeExcelHelper(properties);

        assertThatThrownBy(() -> helper.readStandardRows(minimalWorkbook(1, true)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("标准模板之外的列");
    }

    @Test
    void duplicateAndDecreasingPhysicalRowsAreRejectedBeforeFastExcelMaterialization() {
        ExchangeExcelHelper helper = new ExchangeExcelHelper(new ExchangeImportProperties());
        String row2 = "<row r=\"2\">" + inlineCell("A2", "1") + "</row>";
        String duplicateRow2 = row2 + "<row r=\"2\">" + inlineCell("A2", "2") + "</row>";
        String decreasingRows = "<row r=\"3\">" + inlineCell("A3", "1") + "</row>"
                + "<row r=\"2\">" + inlineCell("A2", "2") + "</row>";

        assertThatThrownBy(() -> helper.readStandardRows(customWorkbook(duplicateRow2, 0, null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("行号必须严格递增");
        assertThatThrownBy(() -> helper.readStandardRows(customWorkbook(decreasingRows, 0, null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("行号必须严格递增");
    }

    @Test
    void formulasAndWhitespaceOnlyOversizedCellsAreRejectedBeforeRowMaps() {
        ExchangeImportProperties properties = new ExchangeImportProperties();
        properties.setMaxCellCharacters(64);
        ExchangeExcelHelper helper = new ExchangeExcelHelper(properties);
        String formula = "<row r=\"2\"><c r=\"A2\"><f>1+1</f><v>2</v></c></row>";
        String whitespace = "<row r=\"2\"><c r=\"A2\" t=\"inlineStr\"><is>"
                + "<t xml:space=\"preserve\">" + " ".repeat(65) + "</t></is></c></row>";

        assertThatThrownBy(() -> helper.readStandardRows(customWorkbook(formula, 0, null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不允许公式");
        assertThatThrownBy(() -> helper.readStandardRows(customWorkbook(whitespace, 0, null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("原始内容超过 64 个字符");
    }

    @Test
    void generatedWorkbookRoundTripsAcrossWorksheetEntries() {
        ExchangeExcelHelper helper = new ExchangeExcelHelper(new ExchangeImportProperties());
        byte[] workbook = helper.writeStandardWorkbook(
                List.of(row("1")),
                Map.of(
                        "gender", List.of("男", "女"),
                        "teachingSubject", List.of("数学", "语文")
                )
        );

        List<ExchangeExcelHelper.ReadRow> rows = helper.readStandardRows(workbook);

        assertThat(rows).singleElement().satisfies(readRow ->
                assertThat(readRow.row().getSequenceNo()).isEqualTo("1"));
    }

    @Test
    void realSharedStringsExpansionStopsInsideBoundedJsonSerialization() {
        int dataRows = 20_000;
        String sharedValue = "共".repeat(4_096);
        ExchangeImportProperties properties = new ExchangeImportProperties();
        ExchangeExcelHelper helper = new ExchangeExcelHelper(properties);

        List<ExchangeExcelHelper.ReadRow> rows =
                helper.readStandardRows(sharedStringWorkbook(dataRows, sharedValue));

        assertThat(rows).hasSize(dataRows);
        assertThat(rows.get(0).row().getSequenceNo()).isEqualTo(sharedValue);
        assertThat(rows.get(dataRows - 1).row().getSequenceNo()).isEqualTo(sharedValue);

        AtomicInteger getterCalls = new AtomicInteger();
        List<CountingPreview> previews = rows.stream()
                .map(row -> new CountingPreview(row.row().getSequenceNo(), getterCalls))
                .toList();
        properties.setMaxPreviewJsonBytes(1024L * 1024);

        assertThatThrownBy(() -> BoundedPreviewJsonWriter.write(
                new ObjectMapper(), previews, properties.getMaxPreviewJsonBytes()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("备份兼容上限");
        assertThat(getterCalls.get()).isLessThan(dataRows);
    }

    @Test
    void excessiveZipEntriesAndObjectMetadataAreRejectedBeforeOpcPackage() {
        ExchangeExcelHelper helper = new ExchangeExcelHelper(new ExchangeImportProperties());

        assertThatThrownBy(() -> helper.readStandardRows(customWorkbook("", 1_020, null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("条目数量超过上限 1024");

        String oversizedStyles = "x".repeat(1024 * 1024 + 1);
        assertThatThrownBy(() -> helper.readStandardRows(customWorkbook("", 0, oversizedStyles)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("元数据条目大小超过上限");
    }

    @Test
    void errorDetailBudgetAllowsBoundaryAndRejectsOverflow() {
        ExchangeImportProperties properties = new ExchangeImportProperties();
        properties.setMaxErrorDetails(3);

        assertThatCode(() -> properties.assertErrorDetailBudget(2, 1)).doesNotThrowAnyException();
        assertThatThrownBy(() -> properties.assertErrorDetailBudget(2, 2))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Excel错误明细超过上限 3");
    }

    @Test
    void previewJsonBudgetRejectsRowsThatTheBackupFormatCannotRepresent() {
        ExchangeImportProperties properties = new ExchangeImportProperties();
        properties.setMaxPreviewJsonBytes(6);
        ObjectMapper objectMapper = new ObjectMapper();

        assertThat(BoundedPreviewJsonWriter.write(objectMapper, "1234", 6))
                .isEqualTo("\"1234\"");
        assertThatThrownBy(() -> BoundedPreviewJsonWriter.write(objectMapper, "12345", 6))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("备份兼容上限 6 字节");
    }

    @Test
    void previewJsonConfigurationCannotExceedBackupStatementBudget() {
        DatabaseBackupProperties backupProperties = new DatabaseBackupProperties();
        backupProperties.setMaxSqlStatementBytes(8 * 1024 * 1024);
        ExchangeImportProperties properties = new ExchangeImportProperties(backupProperties);
        properties.setMaxPreviewJsonBytes(20L * 1024 * 1024);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("超过备份单语句可表示范围");
    }

    private ExchangeStandardRow row(String sequenceNo) {
        ExchangeStandardRow row = new ExchangeStandardRow();
        row.setSequenceNo(sequenceNo);
        return row;
    }

    private byte[] minimalWorkbook(int dataRows, boolean extraColumn) {
        StringBuilder rows = new StringBuilder(dataRows * 55);
        for (int index = 1; index <= dataRows; index++) {
            int rowNo = index + 1;
            rows.append("<row r=\"").append(rowNo).append("\">")
                    .append(inlineCell("A" + rowNo, String.valueOf(index)));
            if (extraColumn && index == 1) {
                rows.append(inlineCell("AA" + rowNo, "unexpected"));
            }
            rows.append("</row>");
        }
        return customWorkbook(rows.toString(), 0, null);
    }

    private byte[] sharedStringWorkbook(int dataRows, String sharedValue) {
        StringBuilder rows = new StringBuilder(dataRows * 55);
        for (int index = 1; index <= dataRows; index++) {
            int rowNo = index + 1;
            rows.append("<row r=\"").append(rowNo).append("\"><c r=\"A")
                    .append(rowNo).append("\" t=\"s\"><v>0</v></c></row>");
        }
        String sharedStrings = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\""
                + " count=\"" + dataRows + "\" uniqueCount=\"1\"><si><t>"
                + escapeXml(sharedValue)
                + "</t></si></sst>";
        return customWorkbook(rows.toString(), 0, null, sharedStrings);
    }

    private byte[] customWorkbook(String dataRows, int extraEntries, String styles) {
        return customWorkbook(dataRows, extraEntries, styles, null);
    }

    private byte[] customWorkbook(
            String dataRows,
            int extraEntries,
            String styles,
            String sharedStrings) {
        StringBuilder sheet = new StringBuilder(2048 + dataRows.length());
        sheet.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
                .append("<sheetData><row r=\"1\">");
        for (ExchangeColumn column : ExchangeColumn.ALL) {
            sheet.append(inlineCell(column.letter() + "1", column.header()));
        }
        sheet.append("</row>").append(dataRows);
        sheet.append("</sheetData></worksheet>");

        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            String sharedStringsContentType = sharedStrings == null ? "" : """
                      <Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>
                    """;
            put(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                      <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                    """ + sharedStringsContentType + "</Types>");
            put(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                    </Relationships>
                    """);
            put(zip, "xl/workbook.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                              xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                      <sheets><sheet name="标准上报表" sheetId="1" r:id="rId1"/></sheets>
                    </workbook>
                    """);
            String sharedStringsRelationship = sharedStrings == null ? "" : """
                      <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>
                    """;
            put(zip, "xl/_rels/workbook.xml.rels", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                    """ + sharedStringsRelationship + "</Relationships>");
            put(zip, "xl/worksheets/sheet1.xml", sheet.toString());
            if (sharedStrings != null) {
                put(zip, "xl/sharedStrings.xml", sharedStrings);
            }
            if (styles != null) {
                put(zip, "xl/styles.xml", styles);
            }
            for (int index = 0; index < extraEntries; index++) {
                put(zip, "custom/empty-" + index + ".bin", "");
            }
            zip.finish();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private String inlineCell(String reference, String value) {
        return "<c r=\"" + reference + "\" t=\"inlineStr\"><is><t>"
                + escapeXml(value) + "</t></is></c>";
    }

    private String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static final class CountingPreview {

        private final String value;
        private final AtomicInteger getterCalls;

        private CountingPreview(String value, AtomicInteger getterCalls) {
            this.value = value;
            this.getterCalls = getterCalls;
        }

        public String getValue() {
            getterCalls.incrementAndGet();
            return value;
        }
    }
}
