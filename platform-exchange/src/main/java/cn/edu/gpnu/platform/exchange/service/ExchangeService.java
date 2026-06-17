package cn.edu.gpnu.platform.exchange.service;

import cn.edu.gpnu.platform.common.api.PageResult;
import cn.edu.gpnu.platform.exchange.dto.ExchangeQuery;
import cn.edu.gpnu.platform.exchange.dto.ImportConfirmRequest;
import cn.edu.gpnu.platform.exchange.vo.BatchVO;
import cn.edu.gpnu.platform.exchange.vo.ExchangeFile;
import cn.edu.gpnu.platform.exchange.vo.ImportResultVO;
import cn.edu.gpnu.platform.exchange.vo.PrevalidateResultVO;
import cn.edu.gpnu.platform.exchange.vo.RollbackResultVO;
import org.springframework.web.multipart.MultipartFile;

public interface ExchangeService {

    ExchangeFile template(ExchangeQuery query);

    PrevalidateResultVO prevalidate(MultipartFile file);

    ExchangeFile errorReport(Long batchId);

    ImportResultVO confirmImport(Long batchId, ImportConfirmRequest request);

    RollbackResultVO rollback(Long batchId);

    PageResult<BatchVO> batches(String type, String status);

    ExchangeFile export(String type, ExchangeQuery query);

    ExchangeFile exportAttachments(ExchangeQuery query);
}
