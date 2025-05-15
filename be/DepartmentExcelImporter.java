package com.example.hcm25_cpl_ks_java_01_lms.department;

import com.example.hcm25_cpl_ks_java_01_lms.location.Location;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class DepartmentExcelImporter {
    public static List<Department> importDepartments(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("Input stream cannot be null");
        }

        List<Department> departments = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalArgumentException("Excel file contains no sheets");
            }

            Iterator<Row> rows = sheet.iterator();
            if (!rows.hasNext()) {
                throw new IllegalArgumentException("Excel file is empty");
            }

            rows.next(); // Skip header row
            int rowNum = 1;

            while (rows.hasNext()) {
                Row row = rows.next();
                rowNum++;
                try {
                    Department department = parseRow(row, rowNum);
                    if (department != null) {
                        departments.add(department);
                    }
                } catch (Exception e) {
                    throw new IllegalArgumentException("Error parsing row " + rowNum + ": " + e.getMessage());
                }
            }
        }
        return departments;
    }

    private static Department parseRow(Row row, int rowNum) {
        // Validate Department Name (Column 1)
        if (row.getCell(1) == null || row.getCell(1).getStringCellValue().trim().isEmpty()) {
            throw new IllegalArgumentException("Department name is missing at row " + rowNum);
        }

        String name = row.getCell(1).getStringCellValue().trim();
        if (name.length() > 255) {
            throw new IllegalArgumentException("Department name exceeds 255 characters at row " + rowNum);
        }

        Department department = new Department();
        department.setName(name);

        // Parse Location từ cột 2
        if (row.getCell(2) != null && !row.getCell(2).getStringCellValue().trim().isEmpty()) {
            String locationName = row.getCell(2).getStringCellValue().trim();
            if (!locationName.equals("N/A")) {
                Location location = new Location();
                location.setName(locationName);
                // Địa chỉ mặc định nếu không có trong Excel
                location.setAddress("N/A");
                department.setLocation(location);
            }
        }

        // Có thể thêm logic để parse Course Names từ cột 5 nếu cần
        // Hiện tại bỏ qua vì cần CourseService để tìm kiếm và gắn Course

        return department;
    }
}