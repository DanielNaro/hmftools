package com.hartwig.hmftools.patientreporter.cfreport.components;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.patientreporter.cfreport.ReportResources;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.property.TextAlignment;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class Footer {

    private final List<PageNumberTemplate> pageNumberTemplates = Lists.newArrayList();

    public void renderFooter(@NotNull PdfPage page, boolean fullWidth, @Nullable String pageNumberPrefix) {
        final PdfCanvas canvas = new PdfCanvas(page.getLastContentStream(), page.getResources(), page.getDocument());

        int pageNumber = page.getDocument().getPageNumber(page);
        PdfFormXObject pageNumberTemplate = new PdfFormXObject(new Rectangle(0, 0, 200, 20));
        canvas.addXObject(pageNumberTemplate, 58, 20);
        pageNumberTemplates.add(new PageNumberTemplate(pageNumber, pageNumberPrefix, pageNumberTemplate));

        BaseMarker.renderMarkerGrid(fullWidth ? 5 : 3, 1, 156, 87, 22, 0, .2f, 0, canvas);

        canvas.release();
    }

    public void writeTotalPageCount(@NotNull PdfDocument document) {
        int totalPageCount = document.getNumberOfPages();
        for (PageNumberTemplate tpl : pageNumberTemplates) {
            tpl.renderPageNumber(totalPageCount, document);
        }
    }

    private static class PageNumberTemplate {

        private final int pageNumber;
        @Nullable
        private final String prefix;
        @NotNull
        private final PdfFormXObject template;

        PageNumberTemplate(int pageNumber, @Nullable String prefix, @NotNull PdfFormXObject template) {
            this.pageNumber = pageNumber;
            this.prefix = prefix;
            this.template = template;
        }

        void renderPageNumber(int totalPageCount, @NotNull PdfDocument document) {
            String displayString = ((prefix != null) ? prefix.toUpperCase() + " \u2014 " : "") + pageNumber + "/" + totalPageCount;

            Canvas canvas = new Canvas(template, document);
            canvas.showTextAligned(new Paragraph().add(displayString).addStyle(ReportResources.pageNumberStyle()),
                    0,
                    0,
                    TextAlignment.LEFT);
        }
    }
}
