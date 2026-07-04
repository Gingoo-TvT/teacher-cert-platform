package cn.edu.gpnu.platform.business.video.service;

import cn.edu.gpnu.platform.business.video.dto.VideoArbitrateRequest;
import cn.edu.gpnu.platform.business.video.dto.VideoAssignRequest;
import cn.edu.gpnu.platform.business.video.dto.VideoQuery;
import cn.edu.gpnu.platform.business.video.dto.VideoReturnRequest;
import cn.edu.gpnu.platform.business.video.dto.VideoScoreRequest;
import cn.edu.gpnu.platform.business.video.dto.VideoThirdReviewRequest;
import cn.edu.gpnu.platform.business.video.dto.VideoUploadInitRequest;
import cn.edu.gpnu.platform.business.video.dto.VideoUploadMergeRequest;
import cn.edu.gpnu.platform.business.video.vo.ReviewerCandidateVO;
import cn.edu.gpnu.platform.business.video.vo.VideoPlaybackVO;
import cn.edu.gpnu.platform.business.video.vo.VideoReviewTaskVO;
import cn.edu.gpnu.platform.business.video.vo.VideoReviewVO;
import cn.edu.gpnu.platform.business.video.vo.VideoUploadInitVO;
import cn.edu.gpnu.platform.business.video.vo.VideoUploadProgressVO;
import cn.edu.gpnu.platform.common.api.PageResult;

import java.io.InputStream;
import java.util.List;

public interface VideoReviewService {

    VideoUploadInitVO initUpload(VideoUploadInitRequest request);

    void uploadChunk(String uploadId, Integer index, String md5, InputStream input, long size);

    VideoReviewVO merge(VideoUploadMergeRequest request);

    VideoUploadProgressVO progress(String uploadId);

    PageResult<VideoReviewVO> list(VideoQuery query);

    List<ReviewerCandidateVO> reviewerCandidates();

    VideoReviewVO detail(Long id);

    void assign(Long reviewId, VideoAssignRequest request);

    PageResult<VideoReviewTaskVO> myTasks(String status, Integer page, Integer size);

    VideoReviewTaskVO taskDetail(Long taskId);

    void submitScore(Long taskId, VideoScoreRequest request);

    void thirdReview(Long reviewId, VideoThirdReviewRequest request);

    void arbitrate(Long reviewId, VideoArbitrateRequest request);

    void confirm(Long reviewId);

    void returnReview(Long reviewId, VideoReturnRequest request);

    VideoPlaybackVO playback(Long reviewId);

    List<VideoReviewTaskVO> tasks(Long reviewId);
}
