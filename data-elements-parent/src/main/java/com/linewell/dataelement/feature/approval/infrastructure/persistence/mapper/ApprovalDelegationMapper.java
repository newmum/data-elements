package com.linewell.dataelement.feature.approval.infrastructure.persistence.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ApprovalDelegationMapper {

    List<String> selectActiveDelegatorIds(
            @Param("tenantId") String tenantId,
            @Param("delegateUserId") String delegateUserId,
            @Param("now") LocalDateTime now
    );
}
