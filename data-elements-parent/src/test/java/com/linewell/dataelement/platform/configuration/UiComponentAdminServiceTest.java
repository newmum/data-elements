package com.linewell.dataelement.platform.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Map;
import java.util.List;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class UiComponentAdminServiceTest {

    private JdbcTemplate jdbc;
    private UiComponentAdminService service;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:ui-component-admin-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                create table ui_component_t (
                    tid varchar(32), pid varchar(32), name varchar(255),
                    type varchar(8), remark varchar(255), sort_order integer, is_del integer,
                    updated_by varchar(32), updated_time timestamp
                )
                """);
        jdbc.update("insert into ui_component_t(tid,pid,name,type,remark,sort_order,is_del) values (?,?,?,?,?,?,0)",
                "workbench", "portal-source", "my-workbench", "1", "01.工作台", 1);
        jdbc.update("insert into ui_component_t(tid,pid,name,type,remark,sort_order,is_del) values (?,?,?,?,?,?,0)",
                "portal-source", "0", "数据源管理", "0", "数据源管理", 1);
        jdbc.update("insert into ui_component_t(tid,pid,name,type,remark,sort_order,is_del) values (?,?,?,?,?,?,0)",
                "another-group", "0", "其他目录", "0", "其他目录", 2);
        service = new UiComponentAdminService(jdbc, mock(UiComponentCacheService.class));
    }

    @Test
    void inlineRenamePreservesParentAndType() {
        service.save(Map.of("tid", "workbench", "name", "my-workbench", "remark", "01.新工作台"));

        assertThat(jdbc.queryForMap("select pid,name,type,remark,sort_order from ui_component_t where tid='workbench'"))
                .containsEntry("PID", "portal-source")
                .containsEntry("TYPE", "1")
                .containsEntry("REMARK", "01.新工作台")
                .containsEntry("SORT_ORDER", 1);
    }

    @Test
    void explicitMoveStillChangesParent() {
        service.save(Map.of("tid", "workbench", "pid", "another-group", "remark", "01.工作台"));

        assertThat(jdbc.queryForObject("select pid from ui_component_t where tid='workbench'", String.class))
                .isEqualTo("another-group");
        assertThat(jdbc.queryForObject("select sort_order from ui_component_t where tid='workbench'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void renamingDoesNotReorderSiblings() {
        jdbc.update("insert into ui_component_t(tid,pid,name,type,remark,sort_order,is_del) values (?,?,?,?,?,?,0)",
                "datasource", "portal-source", "department-datasource", "1", "02.数据源管理", 2);

        service.save(Map.of("tid", "workbench", "remark", "99.工作台"));
        Map<String, Object> portal = (Map<String, Object>) ((List<?>) service.tree().get("list")).getFirst();
        List<Map<String, Object>> children = (List<Map<String, Object>>) portal.get("children");

        assertThat(children).extracting(row -> row.get("tid"))
                .containsExactly("workbench", "datasource");
    }
}
