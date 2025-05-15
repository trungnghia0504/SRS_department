package com.example.hcm25_cpl_ks_java_01_lms.department;

import com.example.hcm25_cpl_ks_java_01_lms.course.CourseService;
import com.example.hcm25_cpl_ks_java_01_lms.location.LocationService;
import com.example.hcm25_cpl_ks_java_01_lms.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.List;

@RestController
@RequestMapping("/api/departments")
@PreAuthorize("@customSecurityService.hasRoleForModule(authentication, 'Department')")
@Tag(name = "Department", description = "Department management API")
public class DepartmentAPIController {

    private final DepartmentService departmentService;
    private final LocationService locationService;
    private final UserService userService;
    private final CourseService courseService;

    public DepartmentAPIController(DepartmentService departmentService, LocationService locationService,
                                UserService userService, CourseService courseService) {
        this.departmentService = departmentService;
        this.locationService = locationService;
        this.userService = userService;
        this.courseService = courseService;
    }

    @GetMapping
    @Operation(summary = "Get all departments", description = "Get a paginated list of departments with optional filtering")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved departments",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Page.class))),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<Page<Department>> listDepartments(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Optional search term") @RequestParam(required = false) String searchTerm) {
        try {
            Page<Department> departments = departmentService.getDepartments(searchTerm, page, size);
            return ResponseEntity.ok(departments);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get department by ID", description = "Get department details by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved department",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Department.class))),
            @ApiResponse(responseCode = "404", description = "Department not found")
    })
    public ResponseEntity<Department> getDepartmentById(
            @Parameter(description = "Department ID", required = true) @PathVariable Long id) {
        Department department = departmentService.getDepartmentById(id);
        if (department != null) {
            return ResponseEntity.ok(department);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    @Transactional
    @Operation(summary = "Create department", description = "Create a new department")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Department created successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Department.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - validation error or duplicate name")
    })
    public ResponseEntity<?> createDepartment(
            @Parameter(description = "Department data", required = true) @RequestBody Department department) {
        try {
            // Check if department name already exists
            if (departmentService.existsByName(department.getName())) {
                return ResponseEntity.badRequest()
                        .body("Department name '" + department.getName() + "' already exists");
            }

            Department savedDepartment = departmentService.createDepartment(department);
            return ResponseEntity.status(HttpStatus.CREATED).body(savedDepartment);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update department", description = "Update an existing department by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Department updated successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Department.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - validation error"),
            @ApiResponse(responseCode = "404", description = "Department not found")
    })
    public ResponseEntity<?> updateDepartment(
            @Parameter(description = "Department ID", required = true) @PathVariable Long id,
            @Parameter(description = "Updated department data", required = true) @RequestBody Department departmentDetails) {
        try {
            Department existingDepartment = departmentService.getDepartmentById(id);
            if (existingDepartment == null) {
                return ResponseEntity.notFound().build();
            }

            // Update only specific fields
            existingDepartment.setName(departmentDetails.getName());
            existingDepartment.setLocation(departmentDetails.getLocation());

            Department updatedDepartment = departmentService.updateDepartment(existingDepartment);
            return ResponseEntity.ok(updatedDepartment);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete department", description = "Delete a department by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Department deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Department not found")
    })
    public ResponseEntity<?> deleteDepartment(
            @Parameter(description = "Department ID", required = true) @PathVariable Long id) {
        try {
            Department department = departmentService.getDepartmentById(id);
            if (department == null) {
                return ResponseEntity.notFound().build();
            }
            
            departmentService.deleteDepartment(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/export")
    @Operation(summary = "Export departments to Excel", description = "Export departments to Excel file")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Excel file generated successfully",
                    content = @Content(mediaType = "application/vnd.ms-excel")),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Resource> exportToExcel(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") Integer size) {
        try {
            if (size == null) {
                // Export all departments if size is not specified
                List<Department> departments = departmentService.getAllDepartments("",page,size).getContent();
                ByteArrayInputStream in = departmentService.exportDepartmentsToExcel(departments);
                
                HttpHeaders headers = new HttpHeaders();
                headers.add("Content-Disposition", "attachment; filename=departments.xlsx");

                return ResponseEntity
                        .ok()
                        .headers(headers)
                        .contentType(MediaType.parseMediaType("application/vnd.ms-excel"))
                        .body(new InputStreamResource(in));
            } else {
                // Export paginated departments if size is specified
                Page<Department> departments = departmentService.getDepartments(null, page, size);
                ByteArrayInputStream in = departmentService.exportDepartmentsToExcel(departments.getContent());
                
                HttpHeaders headers = new HttpHeaders();
                headers.add("Content-Disposition", "attachment; filename=departments.xlsx");

                return ResponseEntity
                        .ok()
                        .headers(headers)
                        .contentType(MediaType.parseMediaType("application/vnd.ms-excel"))
                        .body(new InputStreamResource(in));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/import")
    @Transactional
    @Operation(summary = "Import departments from Excel", description = "Import departments from an Excel file")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Departments imported successfully"),
            @ApiResponse(responseCode = "400", description = "Bad request - invalid file format or empty file")
    })
    public ResponseEntity<?> importExcel(
            @Parameter(description = "Excel file to import", required = true) @RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("Please select a file to upload");
            }
            if (!file.getOriginalFilename().endsWith(".xlsx") && !file.getOriginalFilename().endsWith(".xls")) {
                return ResponseEntity.badRequest().body("Only Excel files (.xlsx, .xls) are supported");
            }

            List<Department> departments = DepartmentExcelImporter.importDepartments(file.getInputStream());
            departmentService.saveAllFromExcel(departments);
            return ResponseEntity.ok("Data imported successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to import: " + e.getMessage());
        }
    }

    @PostMapping("/delete-all")
    @Transactional
    @Operation(summary = "Delete multiple departments", description = "Delete multiple departments by IDs")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Departments deleted successfully"),
            @ApiResponse(responseCode = "400", description = "Bad request - empty ID list")
    })
    public ResponseEntity<String> deleteSelectedDepartments(
            @Parameter(description = "List of department IDs to delete", required = true) @RequestBody DepartmentController.DeleteRequest deleteRequest) {
        try {
            List<Long> ids = deleteRequest.getIds();
            if (ids == null || ids.isEmpty()) {
                return ResponseEntity.badRequest().body("No departments selected for deletion");
            }
            for (Long id : ids) {
                departmentService.deleteDepartment(id);
            }
            return ResponseEntity.ok("Departments deleted successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to delete departments: " + e.getMessage());
        }
    }

    // Inner class for delete request
    public static class DeleteRequest {
        private List<Long> ids;

        public List<Long> getIds() {
            return ids;
        }

        public void setIds(List<Long> ids) {
            this.ids = ids;
        }
    }
}