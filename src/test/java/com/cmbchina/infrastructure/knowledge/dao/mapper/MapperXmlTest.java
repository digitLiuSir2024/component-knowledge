package com.cmbchina.infrastructure.knowledge.dao.mapper;

import java.io.InputStream;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

/** 确保新增 mapper XML 可被 MyBatis 解析，不连接数据库。 */
public class MapperXmlTest {
    @Test
    public void parsesLegacyAndSyncMappers() {
        Configuration configuration = new Configuration();
        parse(configuration, "mapper/knowledge/LegacyCrudMapper.xml",
                "com.cmbchina.infrastructure.knowledge.dao.mapper.LegacyCrudMapper.upsert");
        parse(configuration, "mapper/knowledge/InteractionSyncMapper.xml",
                "com.cmbchina.infrastructure.knowledge.dao.mapper.InteractionSyncMapper.deleteRelations");
    }

    private void parse(Configuration configuration, String resource, String statement) {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull("缺少 " + resource, stream);
            new XMLMapperBuilder(stream, configuration, resource, configuration.getSqlFragments()).parse();
            assertNotNull("缺少语句 " + statement, configuration.getMappedStatement(statement));
        } catch (java.io.IOException cause) {
            throw new IllegalStateException(cause);
        }
    }
}
