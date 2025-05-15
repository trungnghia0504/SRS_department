package com.example.hcm25_cpl_ks_java_01_lms.department;

import com.example.hcm25_cpl_ks_java_01_lms.course.Course;
import com.example.hcm25_cpl_ks_java_01_lms.language.Language;
import com.example.hcm25_cpl_ks_java_01_lms.location.Location;
import com.example.hcm25_cpl_ks_java_01_lms.location.LocationRepository;
import com.example.hcm25_cpl_ks_java_01_lms.user.User;
import jakarta.persistence.EntityNotFoundException;
import lombok.SneakyThrows;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class DepartmentService {
    private final DepartmentRepository departmentRepository;
    private final LocationRepository locationRepository;

    @Autowired
    public DepartmentService(DepartmentRepository departmentRepository, LocationRepository locationRepository) {
        this.departmentRepository = departmentRepository;
        this.locationRepository = locationRepository;
    }

    public Page<Department> getDepartments(String searchTerm, int page, int size) {
        if (page < 0 || size <= 0) {
            throw new IllegalArgumentException("Invalid page or size parameters");
        }
        Pageable pageable = PageRequest.of(page, size);
        return searchTerm != null && !searchTerm.trim().isEmpty() ?
                departmentRepository.findByNameContainingIgnoreCase(searchTerm.trim(), pageable) :
                departmentRepository.findAll(pageable);
    }

    @SneakyThrows
    @Transactional
    public Department createDepartment(Department department) {
        validateDepartment(department);
        if (departmentRepository.findByName(department.getName()).isPresent()) {
            throw new DepartmentAlreadyExistsException("Department with name '" + department.getName() + "' already exists");
        }
        if (department.getLocation() != null && department.getLocation().getId() == null) {
            Location savedLocation = saveOrGetLocation(department.getLocation());
            department.setLocation(savedLocation);
        }
        return departmentRepository.save(department);
    }

    @SneakyThrows
    @Transactional
    public Department updateDepartment(Department departmentDetails) {
        validateDepartment(departmentDetails);
        Department existing = getDepartmentById(departmentDetails.getId());
        Optional<Department> byName = departmentRepository.findByName(departmentDetails.getName());
        if (byName.isPresent() && !byName.get().getId().equals(departmentDetails.getId())) {
            throw new DepartmentAlreadyExistsException("Department with name '" + departmentDetails.getName() + "' already exists");
        }
        if (departmentDetails.getLocation() != null && departmentDetails.getLocation().getId() == null) {
            Location savedLocation = saveOrGetLocation(departmentDetails.getLocation());
            departmentDetails.setLocation(savedLocation);
        }
        return departmentRepository.save(departmentDetails);
    }

    @Transactional
    public void deleteDepartment(Long id) {
        Department department = getDepartmentById(id);
        departmentRepository.delete(department);
    }

    public Department getDepartmentById(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Invalid department ID");
        }
        return departmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Department not found with id: " + id));
    }

    public ByteArrayInputStream exportDepartmentsToExcel(List<Department> departments) throws IOException {
        if (departments == null) {
            throw new IllegalArgumentException("Departments list cannot be null");
        }
        return generateExcel(departments);
    }

    private ByteArrayInputStream generateExcel(List<Department> departments) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Departments");
            createHeaderRow(workbook, sheet);

            int rowIdx = 1;
            for (Department department : departments) {
                Row row = sheet.createRow(rowIdx++);
                populateRow(row, department);
            }

            for (int i = 0; i < 6; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    private void createHeaderRow(Workbook workbook, Sheet sheet) {
        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = createHeaderStyle(workbook);
        String[] headers = {"ID", "Name", "Location", "Users Count", "Courses Count", "User Names", "Course Names"};
        for (int col = 0; col < headers.length; col++) {
            Cell cell = headerRow.createCell(col);
            cell.setCellValue(headers[col]);
            cell.setCellStyle(headerStyle);
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private void populateRow(Row row, Department department) {
        row.createCell(0).setCellValue(department.getId());
        row.createCell(1).setCellValue(department.getName());
        row.createCell(2).setCellValue(department.getLocation() != null ? department.getLocation().getName() : "N/A");
        row.createCell(3).setCellValue(department.getUsers() != null ? department.getUsers().size() : 0);
        row.createCell(4).setCellValue(department.getCourses() != null ? department.getCourses().size() : 0);
        row.createCell(5).setCellValue(department.getUsers() != null ?
                department.getUsers().stream().map(User::getUsername).collect(Collectors.joining(",")) :"N/A");
        row.createCell(6).setCellValue(department.getCourses() != null ?
                department.getCourses().stream().map(Course::getName).collect(Collectors.joining(", ")) : "N/A");
    }

    @Transactional
    public List<Department> saveAllFromExcel(List<Department> departments) {
        if (departments == null || departments.isEmpty()) {
            throw new IllegalArgumentException("Departments list cannot be null or empty");
        }

        for (Department dept : departments) {
            validateDepartment(dept);
            Optional<Department> existing = departmentRepository.findByName(dept.getName());
            if (existing.isPresent()) {
                dept.setId(existing.get().getId());
            }
            if (dept.getLocation() != null) {
                Location savedLocation = saveOrGetLocation(dept.getLocation());
                dept.setLocation(savedLocation);
            }
        }
        return departmentRepository.saveAll(departments);
    }

    private Location saveOrGetLocation(Location location) {
        if (location == null || location.getName() == null || location.getName().trim().isEmpty()) {
            return null;
        }
        Optional<Location> existingLocation = locationRepository.findByName(location.getName());
        if (existingLocation.isPresent()) {
            return existingLocation.get();
        }
        if (location.getAddress() == null) {
            location.setAddress("N/A"); // Default address if not provided
        }
        return locationRepository.save(location);
    }

    private void validateDepartment(Department department) {
        if (department == null) {
            throw new IllegalArgumentException("Department cannot be null");
        }
        if (department.getName() == null || department.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Department name cannot be empty");
        }
        if (department.getName().length() > 255) {
            throw new IllegalArgumentException("Department name exceeds maximum length of 255 characters");
        }
    }

    public Department getDepartmentByName(String name) {
        return departmentRepository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Department with name " + name + " not found"));
    }

    public boolean existsByName(String name) {
        return departmentRepository.findByName(name).isPresent();
    }

    private ByteArrayInputStream generateExcelTemplate() throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Departments");

            // Tạo header cho file mẫu
            Row headerRow = sheet.createRow(0);
            CellStyle headerCellStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerCellStyle.setFont(font);

            String[] headers = {"Name"};
            for (int col = 0; col < headers.length; col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(headers[col]);
                cell.setCellStyle(headerCellStyle);
            }

            // Thêm một hàng ví dụ
            Row exampleRow = sheet.createRow(1);
            exampleRow.createCell(0).setCellValue("Example Department");

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    public Page<Department> getAllDepartments(String searchTerm, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        if (searchTerm != null && !searchTerm.isEmpty()) {
            return departmentRepository.findByNameContainingIgnoreCase(searchTerm, pageable);
        }
        return departmentRepository.findAll(pageable);
    }

    public long countAllDepartments() {
        return departmentRepository.count(); // Đếm tổng số dữ liệu
    }


    

}