package com.pareidolia.roster_service.util;

import org.apache.poi.ss.usermodel.*;

public class ExcelStyleUtil {

    public static CellStyle header(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.CENTER);
        return s;
    }

    public static CellStyle color(Workbook wb, IndexedColors c) {
        CellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(c.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        return s;
    }
}
