package cn.edu.gpnu.platform.system.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class UserDataScopeRequest {

    private List<Long> collegeIds = new ArrayList<>();
    private List<Long> majorIds = new ArrayList<>();
}
