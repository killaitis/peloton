package peloton

class EventStorePostgreSQLSpec extends EventStoreSpec:
  val config = PostgreSQLSpec.testContainerConfig
