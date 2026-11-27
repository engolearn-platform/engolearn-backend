package fit.iuh.engolearn.assessment.utils;

import fit.iuh.engolearn.assessment.exception.AssessmentException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/** Reads ordinary cell values from the three sheets of the Studio template. */
public final class AssessmentWorkbook {
    public static final int MAX_BYTES = 2_000_000;
    private static final int MAX_EXPANDED = 8_000_000;
    private static final String SHEET_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

    private AssessmentWorkbook() {}

    public record Row(int line, Map<Integer, String> cells) {
        public String cell(int index) { return cells.getOrDefault(index, "").strip(); }
    }

    public static Map<String, List<Row>> read(byte[] workbook, List<String> names) {
        if (workbook.length == 0 || workbook.length > MAX_BYTES) throw invalid("Tệp .xlsx phải nhỏ hơn 2 MB.");
        try {
            var files = open(workbook);
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var parser = factory.newDocumentBuilder();
            var books = xml(files, "xl/workbook.xml", parser);
            var relationships = xml(files, "xl/_rels/workbook.xml.rels", parser);
            Map<String, String> addresses = new HashMap<>();
            for (var relationship : elements(relationships, "*", "Relationship")) {
                String target = relationship.getAttribute("Target");
                if (target.matches("(/xl/)?worksheets/[A-Za-z0-9_.-]+\\.xml")) {
                    addresses.put(relationship.getAttribute("Id"), target.startsWith("/") ? target.substring(1) : "xl/" + target);
                }
            }
            var shared = files.containsKey("xl/sharedStrings.xml")
                    ? elements(xml(files, "xl/sharedStrings.xml", parser), SHEET_NS, "si")
                            .stream().map(AssessmentWorkbook::inlineText).toList()
                    : List.<String>of();
            Map<String, List<Row>> result = new LinkedHashMap<>();
            for (var sheet : elements(books, SHEET_NS, "sheet")) {
                String name = sheet.getAttribute("name");
                if (!names.contains(name)) continue;
                String address = addresses.get(sheet.getAttributeNS(REL_NS, "id"));
                if (address == null || result.containsKey(name)) throw invalid("Sheet " + name + " không hợp lệ.");
                result.put(name, rows(xml(files, address, parser), shared, name));
            }
            for (String name : names) if (!result.containsKey(name)) throw invalid("Thiếu sheet " + name + ".");
            return result;
        } catch (IOException | SAXException | ParserConfigurationException | IllegalArgumentException
                | IndexOutOfBoundsException malformed) {
            throw invalid("Không đọc được .xlsx. Hãy dùng mẫu của Studio và lưu dưới dạng Excel Workbook.");
        }
    }

    private static Map<String, byte[]> open(byte[] workbook) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(workbook))) {
            int remaining = MAX_EXPANDED;
            int count = 0;
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (++count > 80) throw invalid("Workbook có quá nhiều thành phần.");
                byte[] bytes = zip.readNBytes(remaining + 1);
                remaining -= bytes.length;
                if (remaining < 0) throw invalid("Workbook sau giải nén vượt giới hạn 8 MB.");
                if (entries.putIfAbsent(entry.getName(), bytes) != null) throw invalid("Workbook có thành phần bị lặp.");
            }
        }
        return entries;
    }

    private static List<Row> rows(Document sheet, List<String> shared, String name) {
        List<Row> rows = new ArrayList<>();
        int previous = 0;
        for (var row : elements(sheet, SHEET_NS, "row")) {
            int number = Integer.parseInt(row.getAttribute("r"));
            if (number <= previous) throw invalid("Sheet " + name + " có thứ tự dòng sai.");
            previous = number;
            Map<Integer, String> cells = new HashMap<>();
            for (var cell : elements(row, SHEET_NS, "c")) {
                String reference = cell.getAttribute("r");
                if (!reference.matches("[A-Z]{1,3}" + number)) throw invalid(name + ", dòng " + number + ": địa chỉ ô sai.");
                if (!elements(cell, SHEET_NS, "f").isEmpty()) {
                    throw invalid(name + ", dòng " + number + " (" + reference + "): dùng giá trị, không dùng công thức.");
                }
                int column = 0;
                for (int i = 0; i < reference.length() && Character.isLetter(reference.charAt(i)); i++) {
                    column = column * 26 + reference.charAt(i) - 'A' + 1;
                }
                var values = elements(cell, SHEET_NS, "v");
                String type = cell.getAttribute("t");
                String raw = values.isEmpty() ? "" : values.getFirst().getTextContent();
                String value = switch (type) {
                    case "inlineStr" -> inlineText(cell);
                    case "s" -> shared.get(Integer.parseInt(raw));
                    case "e" -> throw invalid(name + ", dòng " + number + " (" + reference + "): ô Excel có lỗi.");
                    default -> raw;
                };
                if (!value.isBlank() && cells.putIfAbsent(column - 1, value) != null) {
                    throw invalid(name + ", dòng " + number + ": ô bị lặp.");
                }
            }
            if (!cells.isEmpty()) rows.add(new Row(number, Map.copyOf(cells)));
        }
        return rows;
    }

    private static String inlineText(Element element) {
        return elements(element, SHEET_NS, "t").stream().map(Element::getTextContent)
                .collect(java.util.stream.Collectors.joining());
    }

    private static Document xml(Map<String, byte[]> entries, String name, DocumentBuilder parser)
            throws IOException, SAXException {
        byte[] content = entries.get(name);
        if (content == null) throw invalid("Workbook thiếu dữ liệu sheet.");
        return parser.parse(new ByteArrayInputStream(content));
    }

    private static List<Element> elements(org.w3c.dom.Node node, String namespace, String name) {
        var list = node instanceof Document d ? d.getElementsByTagNameNS(namespace, name)
                : ((Element) node).getElementsByTagNameNS(namespace, name);
        List<Element> result = new ArrayList<>(list.getLength());
        for (int i = 0; i < list.getLength(); i++) result.add((Element) list.item(i));
        return result;
    }

    public static AssessmentException invalid(String detail) {
        return new AssessmentException(422, "DRAFT_IMPORT_INVALID", detail);
    }
}
