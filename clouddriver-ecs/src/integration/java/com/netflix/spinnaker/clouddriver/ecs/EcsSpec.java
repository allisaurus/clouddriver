/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under
 * the License.
 */

package com.netflix.spinnaker.clouddriver.ecs;

import static io.restassured.RestAssured.get;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.DescribeRegionsRequest;
import com.amazonaws.services.ec2.model.DescribeRegionsResult;
import com.amazonaws.services.ec2.model.Region;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.clouddriver.Main;
import com.netflix.spinnaker.clouddriver.aws.security.*;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(
    classes = {Main.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"spring.config.location = classpath:clouddriver.yml"})
public class EcsSpec {

  @TestConfiguration
  public static class TestConfig {
    /**
     * Creating a @MockBean for AmazonClientProvider allows mocked responses
     * during tests to work, but isn't initialized soon enough to work during
     * app start up. This work is a (so far unsuccessful) attempt to mock the EC2 calls
     * required to load the Application Context so that we exercise the
     * AWS/ECS account bootstrapping in clouddriver-aws vs. mocking it all away
     * with a @MockBean AmazonAccountsSynchronizer.
     *
     * currently, this results in:
     * ```
     * o.a.c.loader.WebappClassLoaderBase : Illegal access: this web application
     *    instance has been stopped already. Could not load [org.mockito.codegen.AmazonClientProvider$MockitoMock$1894940203Customizer].
     *    The following stack trace is thrown for debugging purposes as well as to attempt to
     *    terminate the thread which caused the illegal access.
     * ```
     *
     * custom context loader needed, maybe?
     * */

    Logger log = LoggerFactory.getLogger(getClass());

    @Bean
    @Primary
    public AmazonClientProvider mockAwsProvider() {
      log.info("ALLIE Calling mockAwsProvider() ...");
      // MockitoAnnotations.initMocks(this);
      Region testRegion = new Region().withRegionName("us-west-2");

      AmazonClientProvider mockAwsProviderContext = mock(AmazonClientProvider.class);
      AmazonEC2 mockEc2 = mock(AmazonEC2.class);
      when(mockEc2.describeRegions(any(DescribeRegionsRequest.class)))
          .thenReturn(
              new DescribeRegionsResult().withRegions(Collections.singletonList(testRegion)));

      when(mockAwsProviderContext.getAmazonEC2(any(AWSCredentialsProvider.class), anyString()))
          // .thenReturn(mockEc2);
          .thenThrow(new RuntimeException("this is bad"));

      return mockAwsProviderContext;

      // answer vs. thenReturn()?
    }
  }

  protected static final String TEST_OPERATIONS_LOCATION =
      "src/integration/resources/testoperations";
  protected final String ECS_ACCOUNT_NAME = "ecs-account";
  protected final String TEST_REGION = "us-west-2";

  private final Logger log = LoggerFactory.getLogger(getClass());

  @Value("${ecs.enabled}")
  Boolean ecsEnabled;

  @Value("${aws.enabled}")
  Boolean awsEnabled;

  @LocalServerPort private int port;

  // @MockBean protected AmazonClientProvider mockAwsProvider;

  @Autowired protected AmazonClientProvider mockAwsProvider;

  /*@MockBean AmazonAccountsSynchronizer mockAccountsSyncer;

  @BeforeEach
  public void setup() {
    NetflixAmazonCredentials mockAwsCreds = mock(NetflixAmazonCredentials.class);
    when(mockAccountsSyncer.synchronize(
            any(CredentialsLoader.class),
            any(CredentialsConfig.class),
            any(AccountCredentialsRepository.class),
            any(DefaultAccountConfigurationProperties.class),
            any(CatsModule.class)))
        .thenReturn(Collections.singletonList(mockAwsCreds));
  }*/

  @DisplayName(".\n===\n" + "Assert AWS and ECS providers are enabled" + "\n===")
  @Test
  public void configTest() {
    // given
    String url = getTestUrl("/credentials");

    // when
    Response response = get(url).then().contentType(ContentType.JSON).extract().response();
    log.info("creds response: ");
    log.info(response.asString());

    assertTrue(awsEnabled);
    assertTrue(ecsEnabled);
  }

  protected String generateStringFromTestFile(String path) throws IOException {
    return new String(Files.readAllBytes(Paths.get(TEST_OPERATIONS_LOCATION, path)));
  }

  protected String getTestUrl(String path) {
    return "http://localhost:" + port + path;
  }

  protected DefaultCacheResult buildCacheResult(
      Map<String, Object> attributes, String namespace, String key) {
    Collection<CacheData> dataPoints = new LinkedList<>();
    dataPoints.add(new DefaultCacheData(key, attributes, Collections.emptyMap()));

    Map<String, Collection<CacheData>> dataMap = new HashMap<>();
    dataMap.put(namespace, dataPoints);

    return new DefaultCacheResult(dataMap);
  }

  protected void retryUntilTrue(BooleanSupplier func, String failMsg, int retrySeconds)
      throws InterruptedException {
    for (int i = 0; i < retrySeconds; i++) {
      if (!func.getAsBoolean()) {
        Thread.sleep(1000);
      } else {
        return;
      }
    }
    fail(failMsg);
  }
}
