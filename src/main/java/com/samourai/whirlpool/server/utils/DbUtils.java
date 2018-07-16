package com.samourai.whirlpool.server.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.lang.invoke.MethodHandles;
import java.nio.charset.Charset;

@Service
public class DbUtils {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private ResourceLoader resourceLoader;
    private DataSource datasource;

    @Autowired
    public DbUtils(ResourceLoader resourceLoader, DataSource datasource) {
        this.resourceLoader = resourceLoader;
        this.datasource = datasource;
    }

    public void runSqlFile(String fileName) throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("running sql file: " + fileName);
        }
        Resource resource = resourceLoader.getResource("classpath:" + fileName);
        EncodedResource encodedResource = new EncodedResource(resource, Charset.forName("UTF-8"));
        ScriptUtils.executeSqlScript(datasource.getConnection(), encodedResource);
    }
}
