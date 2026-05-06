package com.canals.orders.integration

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["external.payment.latency-ms=0"],
)
@AutoConfigureEmbeddedDatabase
abstract class AbstractIntegrationTest
