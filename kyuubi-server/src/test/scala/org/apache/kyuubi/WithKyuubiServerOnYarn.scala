/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.kyuubi

import scala.collection.JavaConverters._
import scala.concurrent.duration._

import org.apache.kyuubi.client.api.v1.dto.BatchRequest
import org.apache.kyuubi.config.KyuubiConf
import org.apache.kyuubi.config.KyuubiConf._
import org.apache.kyuubi.config.KyuubiConf.FrontendProtocols.FrontendProtocol
import org.apache.kyuubi.engine.YarnApplicationOperation
import org.apache.kyuubi.engine.spark.SparkProcessBuilder
import org.apache.kyuubi.operation.{FetchOrientation, HiveJDBCTestHelper, OperationState}
import org.apache.kyuubi.operation.OperationState.ERROR
import org.apache.kyuubi.server.MiniYarnService
import org.apache.kyuubi.session.{KyuubiBatchSessionImpl, KyuubiSessionManager}

/**
 * To developers:
 *   You should specify JAVA_HOME before running test with mini yarn server. Otherwise the error
 * may be thrown `/bin/bash: /bin/java: No such file or directory`.
 */
sealed trait WithKyuubiServerOnYarn extends WithKyuubiServer {

  protected lazy val yarnOperation: YarnApplicationOperation = {
    val operation = new YarnApplicationOperation()
    operation.initialize(miniYarnService.getConf)
    operation
  }

  protected var miniYarnService: MiniYarnService = _

  override def beforeAll(): Unit = {
    conf.set("spark.master", "yarn")
      .set("spark.executor.instances", "1")
    miniYarnService = new MiniYarnService()
    miniYarnService.initialize(conf)
    miniYarnService.start()
    conf.set(s"$KYUUBI_ENGINE_ENV_PREFIX.HADOOP_CONF_DIR", miniYarnService.getHadoopConfDir)
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    // stop kyuubi server
    // stop yarn operation client
    // stop yarn cluster
    super.afterAll()
    yarnOperation.stop()
    if (miniYarnService != null) {
      miniYarnService.stop()
      miniYarnService = null
    }
  }
}

class KyuubiOperationYarnClusterSuite extends WithKyuubiServerOnYarn with HiveJDBCTestHelper {

  private val preDefinedAppName = "kyuubi-batch-job"

  override protected val frontendProtocols: Seq[FrontendProtocol] =
    FrontendProtocols.THRIFT_BINARY :: FrontendProtocols.REST :: Nil

  override protected val conf: KyuubiConf = {
    new KyuubiConf()
      .set(s"$KYUUBI_BATCH_CONF_PREFIX.spark.spark.app.name", preDefinedAppName)
      .set(BATCH_CONF_IGNORE_LIST, Seq("spark.app.name"))
      .set(BATCH_APPLICATION_CHECK_INTERVAL, 3000L)
  }

  override protected def jdbcUrl: String = getJdbcUrl

  test("KYUUBI #527- Support test with mini yarn cluster") {
    withJdbcStatement() { statement =>
      val resultSet = statement.executeQuery("""SELECT "${spark.app.id}" as id""")
      assert(resultSet.next())
      assert(resultSet.getString("id").startsWith("application_"))
    }
  }

  test("session_user shall work on yarn") {
    withJdbcStatement() { statement =>
      val resultSet = statement.executeQuery("SELECT SESSION_USER() as su")
      assert(resultSet.next())
      assert(resultSet.getString("su") === user)
    }
  }

  private def sessionManager: KyuubiSessionManager =
    server.backendService.sessionManager.asInstanceOf[KyuubiSessionManager]

  test("open batch session") {
    val sparkProcessBuilder = new SparkProcessBuilder("kyuubi", conf)

    val batchRequest = new BatchRequest(
      "spark",
      sparkProcessBuilder.mainResource.get,
      sparkProcessBuilder.mainClass,
      null,
      Map(
        "spark.master" -> "yarn",
        "spark.app.name" -> "customName",
        s"spark.${ENGINE_SPARK_MAX_LIFETIME.key}" -> "5000",
        s"spark.${ENGINE_CHECK_INTERVAL.key}" -> "1000").asJava,
      Seq.empty[String].asJava)

    val sessionHandle = sessionManager.openBatchSession(
      "kyuubi",
      "passwd",
      "localhost",
      batchRequest.getConf.asScala.toMap,
      batchRequest)

    val session = sessionManager.getSession(sessionHandle).asInstanceOf[KyuubiBatchSessionImpl]
    val batchJobSubmissionOp = session.batchJobSubmissionOp

    eventually(timeout(3.minutes), interval(50.milliseconds)) {
      val state = batchJobSubmissionOp.currentApplicationState
      assert(state.nonEmpty)
      assert(state.exists(_("id").startsWith("application_")))
      assert(state.exists(_("name") == preDefinedAppName))
    }

    val killResponse = yarnOperation.killApplicationByTag(sessionHandle.identifier.toString)
    assert(killResponse._1)
    assert(killResponse._2 startsWith "Succeeded to terminate:")

    val appInfo = yarnOperation.getApplicationInfoByTag(sessionHandle.identifier.toString)

    assert(appInfo("state") === "KILLED")

    eventually(timeout(10.minutes), interval(50.milliseconds)) {
      assert(batchJobSubmissionOp.getStatus.state === ERROR)
    }

    val resultColumns = batchJobSubmissionOp.getNextRowSet(FetchOrientation.FETCH_NEXT, 10)
      .getColumns.asScala

    val keys = resultColumns.head.getStringVal.getValues.asScala
    val values = resultColumns.apply(1).getStringVal.getValues.asScala
    val rows = keys.zip(values).toMap
    val appId = rows("id")
    val appName = rows("name")
    val appState = rows("state")
    val appUrl = rows("url")
    val appError = rows("error")

    val state2 = batchJobSubmissionOp.currentApplicationState.get
    assert(appId === state2("id"))
    assert(appName === state2("name"))
    assert(appState === state2("state"))
    assert(appUrl === state2("url"))
    assert(appError === state2("error"))
    sessionManager.closeSession(sessionHandle)
  }

  test("prevent dead loop if the batch job submission process it not alive") {
    val sparkProcessBuilder = new SparkProcessBuilder("kyuubi", conf)

    val batchRequest = new BatchRequest(
      "spark",
      sparkProcessBuilder.mainResource.get,
      sparkProcessBuilder.mainClass,
      "spark-batch-submission",
      Map(
        "spark.master" -> "invalid",
        s"spark.${ENGINE_SPARK_MAX_LIFETIME.key}" -> "5000",
        s"spark.${ENGINE_CHECK_INTERVAL.key}" -> "1000").asJava,
      Seq.empty[String].asJava)

    val sessionHandle = sessionManager.openBatchSession(
      "kyuubi",
      "passwd",
      "localhost",
      batchRequest.getConf.asScala.toMap,
      batchRequest)

    val session = sessionManager.getSession(sessionHandle).asInstanceOf[KyuubiBatchSessionImpl]
    val batchJobSubmissionOp = session.batchJobSubmissionOp

    eventually(timeout(3.minutes), interval(50.milliseconds)) {
      assert(batchJobSubmissionOp.currentApplicationState.isEmpty)
      assert(batchJobSubmissionOp.getStatus.state === OperationState.ERROR)
    }
  }
}
