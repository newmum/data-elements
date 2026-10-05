package com.linewell.dataelement.feature.approval.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.linewell.dataelement.feature.approval.infrastructure.persistence.mapper.ApprovalDelegationMapper;
import com.linewell.dataelement.feature.approval.infrastructure.persistence.mapper.FlowSuggestionMapper;
import com.linewell.dataelement.feature.identity.infrastructure.persistence.mapper.IdentityDirectoryMapper;
import java.io.InputStream;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class ApprovalMapperXmlTest {

    @Test
    void approvalAndDesignerIdentityMappersParse() {
        Configuration configuration = new Configuration();
        parse(configuration, "mapper/feature/approval/ApprovalDelegationMapper.xml");
        parse(configuration, "mapper/feature/approval/FlowSuggestionMapper.xml");
        parse(configuration, "mapper/feature/identity/IdentityDirectoryMapper.xml");

        assertThat(configuration.hasStatement(ApprovalDelegationMapper.class.getName() + ".selectActiveDelegatorIds"))
                .isTrue();
        assertThat(configuration.hasResultMap(FlowSuggestionMapper.class.getName() + ".BaseResultMap"))
                .isTrue();
        assertThat(configuration.hasStatement(IdentityDirectoryMapper.class.getName() + ".selectRolePage"))
                .isTrue();
    }

    private void parse(Configuration configuration, String resource) {
        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        } catch (Exception exception) {
            throw new AssertionError("Failed to parse " + resource, exception);
        }
    }
}
