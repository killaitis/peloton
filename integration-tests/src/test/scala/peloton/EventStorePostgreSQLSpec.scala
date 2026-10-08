package peloton

class EventStorePostgreSQLSpec extends EventStoreSpec:
  val config = PostgreSQLSpec.testContainerConfig

class EventStorePostgreSQLCurrentSpec extends EventStoreSpec:
  val config = PostgreSQLSpec.currentTestContainerConfig
