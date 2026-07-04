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

    /**
     * Phase 43.2 §7.4：将导入的证书编号纳入序列占用。
     * 导入的 18 位标准证书号若不推进 {@code cert_sequence}，会与后续自动生成永久撞号
     * （generate 撞号回滚又不推进序列 → “证书编号已存在，请重试”永远失败）。
     * 本方法按与 {@code nextCertNo} 相同的 scopeKey 规则，把对应序列推进到不小于导入号的序号，
     * 使后续自动生成跳过已占号。
     */
    void reserveImportedSequence(String certNo);
}
