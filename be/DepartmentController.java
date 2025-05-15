package com.example.hcm25_cpl_ks_java_01_lms.department;

import com.example.hcm25_cpl_ks_java_01_lms.common.Constants;
import com.example.hcm25_cpl_ks_java_01_lms.course.CourseService;
import com.example.hcm25_cpl_ks_java_01_lms.language.Language;
import com.example.hcm25_cpl_ks_java_01_lms.location.LocationService;
import com.example.hcm25_cpl_ks_java_01_lms.user.User;
import com.example.hcm25_cpl_ks_java_01_lms.user.UserService;
import com.google.api.ResourceDescriptor;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.GeneratedMessage;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static com.google.api.ResourceProto.resource;

@Controller
@RequestMapping("/departments")
@PreAuthorize("@customSecurityService.hasRoleForModule(authentication, 'Department')")
public class DepartmentController {

    private final DepartmentService departmentService;
    private final LocationService locationService;
    private final UserService userService;
    private final CourseService courseService;

    public DepartmentController(DepartmentService departmentService, LocationService locationService,
                                UserService userService, CourseService courseService) {
        this.departmentService = departmentService;
        this.locationService = locationService;
        this.userService = userService;
        this.courseService = courseService;
    }

    @GetMapping
    public String listDepartments(Model model,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "10") int size,
                                  @RequestParam(required = false) String searchTerm) {
        try {
            Page<Department> departments = departmentService.getDepartments(searchTerm, page, size);
            model.addAttribute("departments", departments);
            model.addAttribute("content", "departments/list");
            return Constants.LAYOUT;
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return Constants.LAYOUT;
        }
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("department", new Department());
        model.addAttribute("courses", courseService.getCourseOfDepartment());
        model.addAttribute("users", userService.getUsersOfDepartment());
        model.addAttribute("locations", locationService.getLocationOfDepartment());
        model.addAttribute("content", "departments/create");
        return Constants.LAYOUT;
    }

    @PostMapping
    @Transactional
    public String createDepartment(@ModelAttribute Department department, Model model) {
        try {
            // Check if department name already exists
            if (departmentService.existsByName(department.getName())) {
                throw new IllegalArgumentException("Department name '" + department.getName() + "' already exists");
            }

            departmentService.createDepartment(department);
            return "redirect:/departments";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            populateFormAttributes(model, department);
            return Constants.LAYOUT;
        }
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model) {
        try {
            Department department = departmentService.getDepartmentById(id);
            model.addAttribute("department", department);
            model.addAttribute("courses", courseService.getAllCourses());
            model.addAttribute("users", userService.getUsersOfDepartment());
            model.addAttribute("locations", locationService.getLocationOfDepartment());
            model.addAttribute("content", "departments/update");
            return Constants.LAYOUT;
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "redirect:/departments";
        }
    }

    @PostMapping("/edit/{id}")
    @Transactional
    public String updateDepartment(@PathVariable Long id, @ModelAttribute Department departmentDetails, Model model) {
        try {
            departmentDetails.setId(id);
            // Check if department name exists for a different department
            Department existingDepartment = departmentService.getDepartmentByName(departmentDetails.getName());
            if (existingDepartment != null && !existingDepartment.getId().equals(id)) {
                throw new IllegalArgumentException("Department name '" + departmentDetails.getName() + "' is already in use by another department");
            }

            departmentService.updateDepartment(departmentDetails);
            return "redirect:/departments";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            populateFormAttributes(model, departmentDetails);
            return Constants.LAYOUT;
        }
    }

    @PostMapping("/delete/{id}")
    @Transactional
    public String deleteDepartment(@PathVariable Long id, Model model) {
        try {
            departmentService.deleteDepartment(id);
            return "redirect:/departments";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "redirect:/departments";
        }
    }

    @GetMapping("/export")
    public ResponseEntity<Resource> exportToExcel(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size) {
        try {
            // Nếu size không được truyền vào, mặc định lấy toàn bộ dữ liệu
            if (size == null) {
                size = (int) departmentService.countAllDepartments();
            }
            List<Department> departments = departmentService.getAllDepartments("", page, size).getContent();
            ByteArrayInputStream in = departmentService.exportDepartmentsToExcel(departments);

            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Disposition", "attachment; filename=departments.xlsx");

            return ResponseEntity
                    .ok()
                    .headers(headers)
                    .contentType(MediaType.parseMediaType("application/vnd.ms-excel"))
                    .body(new InputStreamResource(in));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/import")
    @Transactional
    public String importExcel(@RequestParam("file") MultipartFile file, Model model) {
        try {
            if (file.isEmpty()) {
                throw new IllegalArgumentException("Please select a file to upload");
            }
            if (!file.getOriginalFilename().endsWith(".xlsx") && !file.getOriginalFilename().endsWith(".xls")) {
                throw new IllegalArgumentException("Only Excel files (.xlsx, .xls) are supported");
            }

            List<Department> departments = DepartmentExcelImporter.importDepartments(file.getInputStream());
            departmentService.saveAllFromExcel(departments);
            return "redirect:/departments";
        } catch (Exception e) {
            model.addAttribute("error", "Failed to import: " + e.getMessage());
            return listDepartments(model, 0, 10, null);
        }
    }

    private void populateFormAttributes(Model model, Department department) {
        model.addAttribute("department", department);
        model.addAttribute("courses", courseService.getAllCourses());
        model.addAttribute("users", userService.getUsersOfDepartment());
        model.addAttribute("locations", locationService.getLocationOfDepartment());
        model.addAttribute("content", "departments/create");
    }

    @GetMapping("/print")
    public String printDepartment(Model model) {
        model.addAttribute("departments", departmentService.getDepartments("", 0, Integer.MAX_VALUE));
        return "departments/print";
    }

    @GetMapping("/download-template")
    public ResponseEntity<Resource> downloadExcelTemplate() {
        try {
            // Đường dẫn tương đối từ thư mục gốc của project
            Path filePath = Paths.get("data-excel/department_template.xlsx");
            Resource resource = new ByteArrayResource(Files.readAllBytes(filePath));

            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Disposition", "attachment; filename=department_template.xlsx");
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/delete-all")
    @Transactional
    public ResponseEntity<String> deleteSelectedDepartments(@RequestBody DeleteRequest deleteRequest, Model model) {
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

    // Class để nhận dữ liệu từ request body
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