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

package org.apache.kyuubi.server.metadata

import java.util.UUID

import org.scalatest.time.SpanSugar.convertIntToGrainOfTime

import org.apache.kyuubi.{KyuubiException, KyuubiFunSuite}
import org.apache.kyuubi.config.KyuubiConf
import org.apache.kyuubi.server.metadata.api.Metadata
import org.apache.kyuubi.session.SessionType

class MetadataManagerSuite extends KyuubiFunSuite {
  val metadataManager = new MetadataManager()
  val conf = KyuubiConf().set(KyuubiConf.METADATA_REQUEST_RETRY_INTERVAL, 100L)

  override def beforeAll(): Unit = {
    super.beforeAll()
    metadataManager.initialize(conf)
    metadataManager.start()
  }

  override def afterAll(): Unit = {
    metadataManager.getBatches(null, null, null, 0, 0, 0, Int.MaxValue).foreach { batch =>
      metadataManager.cleanupMetadataById(batch.getId)
    }
    metadataManager.stop()
    super.afterAll()
  }

  test("retry the metadata store requests") {
    val metadata = Metadata(
      identifier = UUID.randomUUID().toString,
      sessionType = SessionType.BATCH,
      realUser = "kyuubi",
      username = "kyuubi",
      ipAddress = "127.0.0.1",
      kyuubiInstance = "localhost:10009",
      state = "PENDING",
      resource = "intern",
      className = "org.apache.kyuubi.SparkWC",
      requestName = "kyuubi_batch",
      requestConf = Map("spark.master" -> "local"),
      requestArgs = Seq("100"),
      createTime = System.currentTimeMillis(),
      engineType = "spark",
      clusterManager = Some("local"))
    metadataManager.insertMetadata(metadata)
    intercept[KyuubiException] {
      metadataManager.insertMetadata(metadata, retryOnError = false)
    }
    metadataManager.insertMetadata(metadata, retryOnError = true)
    val retryRef = metadataManager.getMetadataRequestsRetryRef(metadata.identifier)
    val metadataToUpdate = metadata.copy(state = "RUNNING")
    retryRef.addRetryingMetadataRequest(UpdateMetadata(metadataToUpdate))
    eventually(timeout(3.seconds)) {
      assert(retryRef.hasRemainingRequests())
      assert(metadataManager.getBatch(metadata.identifier).getState === "PENDING")
    }

    val metadata2 = metadata.copy(identifier = UUID.randomUUID().toString)
    val metadata2ToUpdate = metadata2.copy(
      engineId = "app_id",
      engineName = "app_name",
      engineUrl = "app_url",
      engineState = "app_state",
      state = "RUNNING")

    metadataManager.addMetadataRetryRequest(InsertMetadata(metadata2))
    metadataManager.addMetadataRetryRequest(UpdateMetadata(metadata2ToUpdate))

    val retryRef2 = metadataManager.getMetadataRequestsRetryRef(metadata2.identifier)

    eventually(timeout(3.seconds)) {
      assert(!retryRef2.hasRemainingRequests())
      assert(metadataManager.getBatch(metadata2.identifier).getState === "RUNNING")
    }
  }
}
