package cn.edu.gpnu.platform.common.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 列表接口分页请求归一化（P1-1 真分页契约，Phase 44e-contract）。
 *
 * <p>全站列表接口统一接收 {@code page}（页码，从 1 起，默认 {@link #DEFAULT_PAGE}）与
 * {@code size}（每页条数，默认 {@link #DEFAULT_SIZE}、硬上限 {@link #MAX_SIZE}）两个查询参数，
 * 经本工具钳制后构造 MyBatis-Plus {@link Page}，交给 mapper 的 {@code selectPage} 做真分页。</p>
 *
 * <p>钳制规则（rollout 必须逐字沿用，保证全站一致）：</p>
 * <ul>
 *   <li>{@code page} 为 null 或 &lt; 1 → 取 {@link #DEFAULT_PAGE}；</li>
 *   <li>{@code size} 为 null 或 &lt; 1 → 取 {@link #DEFAULT_SIZE}；</li>
 *   <li>{@code size} &gt; {@link #MAX_SIZE} → 收敛到 {@link #MAX_SIZE}（防止客户端传超大 size 退化成全表）。</li>
 * </ul>
 *
 * <p>向后兼容取默认 {@code size=20}：既有集成测试的数据集均为个位数条，页 1 即可返回全部，
 * 不会被默认页大小静默截断（详见 docs/pagination-rollout-spec.md）。</p>
 */
public final class PageQuery {

    /** 默认页码（从 1 起）。 */
    public static final int DEFAULT_PAGE = 1;
    /** 默认每页条数。 */
    public static final int DEFAULT_SIZE = 20;
    /** 每页条数硬上限（客户端不可突破）。 */
    public static final int MAX_SIZE = 200;

    private PageQuery() {
    }

    /**
     * 由请求入参构造已钳制的 MyBatis-Plus 分页对象。
     *
     * @param page 请求页码（可空）
     * @param size 请求每页条数（可空）
     * @param <T>  记录类型
     * @return 归一化后的 {@link Page}
     */
    public static <T> Page<T> of(Integer page, Integer size) {
        long current = (page == null || page < 1) ? DEFAULT_PAGE : page;
        long limit = (size == null || size < 1) ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return new Page<>(current, limit);
    }
}
