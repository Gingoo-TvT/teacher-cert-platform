package cn.edu.gpnu.platform.business.certificate.service;

import cn.edu.gpnu.platform.business.certificate.dto.CertificateCorrectRequest;
import cn.edu.gpnu.platform.business.certificate.dto.CertificateGenerateRequest;
import cn.edu.gpnu.platform.business.certificate.dto.CertificateIssueRequest;
import cn.edu.gpnu.platform.business.certificate.dto.CertificateQuery;
import cn.edu.gpnu.platform.business.certificate.dto.CertificateVoidRequest;
import cn.edu.gpnu.platform.business.certificate.vo.CertificatePrecheckVO;
import cn.edu.gpnu.platform.business.certificate.vo.CertificateVO;
import cn.edu.gpnu.platform.common.api.PageResult;

public interface CertificateService {

    PageResult<CertificateVO> list(CertificateQuery query);

    CertificateVO detail(Long id);

    CertificatePrecheckVO precheck(Long studentId, String assessmentYear);

    CertificateVO generate(CertificateGenerateRequest request);

    CertificateVO issue(Long id, CertificateIssueRequest request);

    CertificateVO markExported(Long id);

    CertificateVO archive(Long id);

    CertificateVO voidCertificate(Long id, CertificateVoidRequest request);

    CertificateVO reissue(Long id);

    CertificateVO correct(Long id, CertificateCorrectRequest request);
}
