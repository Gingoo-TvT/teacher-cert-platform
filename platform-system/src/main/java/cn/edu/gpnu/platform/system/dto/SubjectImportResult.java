package cn.edu.gpnu.platform.system.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SubjectImportResult {

    private int total;
    private int successCount;
    private int failCount;
    private List<SubjectImportError> errors = new ArrayList<>();
}
