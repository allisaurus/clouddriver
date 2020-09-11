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

package com.netflix.spinnaker.clouddriver.ecs.test;

import static io.restassured.RestAssured.given;
import static org.junit.Assert.assertNotNull;

import com.netflix.spinnaker.clouddriver.ecs.EcsSpec;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.io.IOException;
import org.junit.Test;
import org.junit.jupiter.api.DisplayName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateServerGroupSpec extends EcsSpec {

  private final Logger log = LoggerFactory.getLogger(getClass());

  @DisplayName(
      ".\n===\n"
          + "Given createServerGroup operation, successfully submit createService call to ECS"
          + "\n===")
  @Test
  public void createServerGroupOperationTest() throws IOException { // no response
    // given
    String url = getTestUrl("/ecs/ops/createServerGroup");
    String requestBody = generateStringFromTestFile("/createServerGroup-inputs.json");
    log.info("request body:");
    log.info(requestBody);

    // when
    Response response =
        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post(url)
            .then()
            // .statusCode(200)
            .contentType("")
            .extract()
            .response();

    log.info("CREATE SG response:");
    log.info(response.asString()); //

    // then
    assertNotNull(response); // TODO inspect response contents
  }
}
