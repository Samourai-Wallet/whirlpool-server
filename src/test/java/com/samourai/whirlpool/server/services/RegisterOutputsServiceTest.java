package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.lang.invoke.MethodHandles;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = DEFINED_PORT)
public class RegisterOutputsServiceTest extends AbstractIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Autowired
    private RegisterOutputService registerOutputService;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mixService.__reset("12345678");
    }

    // TODO test bordereau already registered
    // TODO test invalid bordereau
    // TODO test invalid addresses
}