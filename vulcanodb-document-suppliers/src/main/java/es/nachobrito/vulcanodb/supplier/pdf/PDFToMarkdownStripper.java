/*
 *    Copyright 2025 Nacho Brito
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package es.nachobrito.vulcanodb.supplier.pdf;

import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.List;

/**
 * An extension of {@link PDFTextStripper} that converts PDF content into Markdown format.
 * <p>
 * This class uses heuristics based on font size and text positioning to detect:
 * <ul>
 *     <li>Headers (using font size thresholds)</li>
 *     <li>Unordered and ordered lists</li>
 *     <li>Simple tables (using horizontal gap detection)</li>
 * </ul>
 * The resulting output is a Markdown-formatted string representation of the PDF content.
 * </p>
 *
 * @author nacho
 */
public class PDFToMarkdownStripper extends PDFTextStripper {

    /**
     * Threshold multiplier for header detection based on font size.
     */
    private static final float HEADER_THRESHOLD = 1.5f;

    /**
     * The estimated average font size used to determine header levels.
     */
    private float averageFontSize = 10.0f; // Default estimate

    /**
     * Constructs a new {@code PDFToMarkdownStripper}.
     *
     * @throws IOException if an error occurs during stripper initialization.
     */
    public PDFToMarkdownStripper() throws IOException {
        super();
        setSortByPosition(true);
    }

    @Override
    protected void writePage() throws IOException {
        // Here we could analyze the whole page to detect tables
        // For simplicity in this example, we'll continue using writeString
        // and add some basic formatting logic.
        super.writePage();
    }

    @Override
    protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
        if (textPositions == null || textPositions.isEmpty()) {
            super.writeString(text, textPositions);
            return;
        }

        float currentFontSize = textPositions.get(0).getFontSizeInPt();

        // Simple Header detection
        if (currentFontSize > averageFontSize * HEADER_THRESHOLD) {
            int level = calculateHeaderLevel(currentFontSize);
            output.write("#".repeat(level) + " ");
        }

        // List detection
        String trimmed = text.trim();
        if (isUnorderedList(trimmed)) {
            output.write("* ");
            output.write(trimmed.substring(1).trim());
        } else if (isOrderedList(trimmed)) {
            output.write(trimmed);
        } else if (isTableLine(textPositions)) {
            formatAsTable(text, textPositions);
        } else {
            output.write(text);
        }
    }

    private int calculateHeaderLevel(float fontSize) {
        if (fontSize > averageFontSize * 2.5) return 1;
        if (fontSize > averageFontSize * 2.0) return 2;
        return 3;
    }

    private boolean isUnorderedList(String text) {
        return text.startsWith("* ") || text.startsWith("- ") || text.startsWith("• ");
    }

    private boolean isOrderedList(String text) {
        return text.matches("^\\d+\\.\\s.*");
    }

    private boolean isTableLine(List<TextPosition> textPositions) {
        // Heuristic: If there are large horizontal gaps between text elements, it might be a table
        if (textPositions.size() < 2) return false;

        int gaps = 0;
        for (int i = 0; i < textPositions.size() - 1; i++) {
            float gap = textPositions.get(i + 1).getXDirAdj() - (textPositions.get(i).getXDirAdj() + textPositions.get(i).getWidthDirAdj());
            if (gap > textPositions.get(i).getFontSizeInPt() * 2) {
                gaps++;
            }
        }
        return gaps > 1; // At least 3 columns
    }

    private void formatAsTable(String text, List<TextPosition> textPositions) throws IOException {
        StringBuilder sb = new StringBuilder("| ");
        float lastX = -1;

        for (TextPosition tp : textPositions) {
            if (lastX != -1 && (tp.getXDirAdj() - lastX > tp.getFontSizeInPt() * 2)) {
                sb.append(" | ");
            }
            sb.append(tp.getUnicode());
            lastX = tp.getXDirAdj() + tp.getWidthDirAdj();
        }
        sb.append(" |");
        output.write(sb.toString());
    }
}
