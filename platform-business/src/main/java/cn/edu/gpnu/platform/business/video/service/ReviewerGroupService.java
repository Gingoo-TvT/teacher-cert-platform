package cn.edu.gpnu.platform.business.video.service;

import cn.edu.gpnu.platform.business.video.dto.ReviewerGroupMemberRequest;
import cn.edu.gpnu.platform.business.video.dto.ReviewerGroupRequest;
import cn.edu.gpnu.platform.business.video.vo.ReviewerGroupVO;

import java.util.List;

public interface ReviewerGroupService {

    List<ReviewerGroupVO> list();

    ReviewerGroupVO detail(Long id);

    ReviewerGroupVO create(ReviewerGroupRequest request);

    ReviewerGroupVO update(Long id, ReviewerGroupRequest request);

    void delete(Long id);

    ReviewerGroupVO addMember(Long groupId, ReviewerGroupMemberRequest request);

    void removeMember(Long groupId, Long memberId);
}
