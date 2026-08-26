package cn.edu.gpnu.platform.business.material.service;

import cn.edu.gpnu.platform.business.material.dto.MaterialBatchDownloadRequest;
import cn.edu.gpnu.platform.business.material.dto.MaterialQuery;
import cn.edu.gpnu.platform.business.material.dto.MaterialReviewRequest;
import cn.edu.gpnu.platform.business.material.dto.MaterialDirectUploadInitRequest;
import cn.edu.gpnu.platform.business.material.dto.MaterialDirectUploadCompleteRequest;
import cn.edu.gpnu.platform.business.material.vo.BatchDownloadFile;
import cn.edu.gpnu.platform.business.material.vo.ProcessMaterialVO;
import cn.edu.gpnu.platform.business.material.vo.ProcessStatusVO;
import cn.edu.gpnu.platform.business.material.vo.MaterialDirectUploadVO;
import cn.edu.gpnu.platform.common.api.PageResult;

import java.io.InputStream;

public interface ProcessMaterialService {

    PageResult<ProcessMaterialVO> list(MaterialQuery query);

    Long upload(Long studentId, String assessmentYear, String category, InputStream input,
                String originalFilename, String contentType, long size);

    MaterialDirectUploadVO initDirectUpload(MaterialDirectUploadInitRequest request);

    Long completeDirectUpload(MaterialDirectUploadCompleteRequest request);

    void cancelDirectUpload(Long fileId);

    void replace(Long id, InputStream input, String originalFilename, String contentType, long size);

    void delete(Long id);

    Long previewFileId(Long id);

    void submit(Long id);

    void firstReview(Long id, MaterialReviewRequest request);

    void secondReview(Long id, MaterialReviewRequest request);

    ProcessStatusVO processStatus(Long studentId, String assessmentYear);

    BatchDownloadFile batchDownload(MaterialBatchDownloadRequest request);
}
