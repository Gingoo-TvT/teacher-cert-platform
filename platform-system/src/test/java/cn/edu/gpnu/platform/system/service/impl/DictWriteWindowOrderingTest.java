package cn.edu.gpnu.platform.system.service.impl;

import cn.edu.gpnu.platform.system.cache.DictRedisPublishGuard;
import cn.edu.gpnu.platform.system.cache.ReferenceCacheInvalidator;
import cn.edu.gpnu.platform.system.config.DictCacheProperties;
import cn.edu.gpnu.platform.system.dto.DictItemSaveRequest;
import cn.edu.gpnu.platform.system.dto.DictTypeSaveRequest;
import cn.edu.gpnu.platform.system.entity.SysDictItem;
import cn.edu.gpnu.platform.system.entity.SysDictType;
import cn.edu.gpnu.platform.system.mapper.SysDictItemMapper;
import cn.edu.gpnu.platform.system.mapper.SysDictTypeMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 字典写窗口必须在首条 DML 前登记。
 *
 * <p>若事务同步注册失败，失效器会同步解除刚打开的窗口。登记晚于 DML 时，外层事务捕获异常后仍可能
 * read-your-writes 并发布最终回滚的数据；登记前移后，注册失败发生时数据库尚未被修改。</p>
 */
@ExtendWith(MockitoExtension.class)
class DictWriteWindowOrderingTest {

    private static final Long ID = 7L;
    private static final String TYPE_CODE = "type_a";

    @Mock
    private SysDictTypeMapper dictTypeMapper;

    @Mock
    private SysDictItemMapper dictItemMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private ReferenceCacheInvalidator referenceCacheInvalidator;

    private DictServiceImpl service;
    private IllegalStateException registrationFailure;

    @BeforeEach
    void setUp() {
        service = new DictServiceImpl(
                dictTypeMapper,
                dictItemMapper,
                redisTemplate,
                new ObjectMapper(),
                cacheManager,
                referenceCacheInvalidator,
                new DictRedisPublishGuard(),
                new DictCacheProperties());
        registrationFailure = new IllegalStateException("registration rejected");
        doThrow(registrationFailure).when(referenceCacheInvalidator)
                .invalidateAfterCompletion(anyString(), any(Runnable.class), any(Runnable.class),
                        anyList(), any(Runnable.class));
    }

    @AfterEach
    void tearDown() {
        service.shutdownWriterLeaseRenewer();
    }

    @Test
    void createItemDoesNotInsertWhenWindowRegistrationFails() {
        when(dictTypeMapper.selectCount(any())).thenReturn(1L);
        when(dictItemMapper.countByItemIncludingDeleted(TYPE_CODE, "item_a", "GLOBAL", null))
                .thenReturn(0L);

        assertThatThrownBy(() -> service.createItem(itemRequest()))
                .isSameAs(registrationFailure);

        verify(dictItemMapper, never()).insert(any(SysDictItem.class));
    }

    @Test
    void updateItemDoesNotUpdateWhenWindowRegistrationFails() {
        when(dictItemMapper.selectById(ID)).thenReturn(itemEntity());
        when(dictTypeMapper.selectCount(any())).thenReturn(1L);
        when(dictItemMapper.countByItemIncludingDeleted(TYPE_CODE, "item_a", "GLOBAL", ID))
                .thenReturn(0L);

        assertThatThrownBy(() -> service.updateItem(ID, itemRequest()))
                .isSameAs(registrationFailure);

        verify(dictItemMapper, never()).updateById(any(SysDictItem.class));
    }

    @Test
    void deleteItemDoesNotDeleteWhenWindowRegistrationFails() {
        when(dictItemMapper.selectById(ID)).thenReturn(itemEntity());

        assertThatThrownBy(() -> service.deleteItem(ID))
                .isSameAs(registrationFailure);

        verify(dictItemMapper, never()).deleteById(any());
    }

    @Test
    void updateTypeDoesNotUpdateWhenWindowRegistrationFails() {
        when(dictTypeMapper.selectById(ID)).thenReturn(typeEntity());

        assertThatThrownBy(() -> service.updateType(ID, typeRequest()))
                .isSameAs(registrationFailure);

        verify(dictTypeMapper, never()).updateById(any(SysDictType.class));
    }

    @Test
    void deleteTypeDoesNotDeleteWhenWindowRegistrationFails() {
        when(dictTypeMapper.selectById(ID)).thenReturn(typeEntity());
        when(dictItemMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.deleteType(ID))
                .isSameAs(registrationFailure);

        verify(dictTypeMapper, never()).deleteById(any());
    }

    private DictItemSaveRequest itemRequest() {
        DictItemSaveRequest request = new DictItemSaveRequest();
        request.setTypeCode(TYPE_CODE);
        request.setItemCode("item_a");
        request.setItemValue("Value A");
        request.setYearVersion("GLOBAL");
        request.setStatus(1);
        return request;
    }

    private DictTypeSaveRequest typeRequest() {
        DictTypeSaveRequest request = new DictTypeSaveRequest();
        request.setTypeCode(TYPE_CODE);
        request.setTypeName("Type A");
        request.setStatus(1);
        return request;
    }

    private SysDictItem itemEntity() {
        SysDictItem item = new SysDictItem();
        item.setId(ID);
        item.setTypeCode(TYPE_CODE);
        item.setItemCode("item_a");
        item.setItemValue("Value A");
        item.setYearVersion("GLOBAL");
        item.setStatus(1);
        return item;
    }

    private SysDictType typeEntity() {
        SysDictType type = new SysDictType();
        type.setId(ID);
        type.setTypeCode(TYPE_CODE);
        type.setTypeName("Type A");
        type.setStatus(1);
        return type;
    }
}
