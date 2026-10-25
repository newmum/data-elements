package com.linewell.dataelement.feature.logmanagement;

import com.linewell.dataelement.platform.audit.AuditStore;
import com.linewell.dataelement.platform.persistence.jdbc.JdbcValueNormalizer;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class LogComponentService {
    private final AuditStore store;
    private static final List<String> NAMES=List.of("log-management");
    public LogComponentService(AuditStore store) { this.store=store; }
    public List<Map<String,Object>> components() {
        return store.named(null).queryForList("select tid,name,compile_js,compile_css from ui_component_t where is_del=0 and type='1' and name in (:names)",Map.of("names",NAMES)).stream().map(JdbcValueNormalizer::map).map(AuditStore::lower).toList();
    }
}
