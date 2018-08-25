package com.samourai.whirlpool.server.tools;

import com.samourai.whirlpool.server.beans.RpcTransaction;
import com.samourai.whirlpool.server.services.BlockchainDataService;
import com.samourai.whirlpool.server.utils.TestUtils;
import com.samourai.whirlpool.server.utils.Utils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.junit4.SpringRunner;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

/**
 * Utility for RPC testing.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = DEFINED_PORT)
@Profile("default") // not test profile for this tool
public class RpcToolsApplication {
	private static final Logger log = LoggerFactory.getLogger(RpcToolsApplication.class);

	@Autowired
	private BlockchainDataService blockchainDataService;

	@Autowired
	private TestUtils testUtils;

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	public void testRun() throws Exception {
		Assert.assertEquals(BlockchainDataService.class, blockchainDataService.getClass());
		Assert.assertTrue(blockchainDataService.testConnectivity());

		log.info("------------ application-rpc ------------");

		String[] txids = {
				"cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187",
				"7ea75da574ebabf8d17979615b059ab53aae3011926426204e730d164a0d0f16",
				"5369dfb71b36ed2b91ca43f388b869e617558165e4f8306b80857d88bdd624f2",
				"3bb546df988d8a577c2b2f216a18b7e337ebaf759187ae88e0eee01829f04eb1",

		};
		for (String txid : txids) {
			printTx(txid);
		}
	}

	private void printTx(String txid) throws Exception {
		RpcTransaction tx = blockchainDataService.__getRpcTransaction(txid).get();
		Assert.assertEquals(txid, tx.getTxid());
		String json = Utils.toJsonString(tx);

		String fileName = testUtils.getMockFileName(txid);
		System.out.println("writing " + fileName + ": " + json);
		Files.write(Paths.get(fileName), json.getBytes(), StandardOpenOption.CREATE);

	}
}
