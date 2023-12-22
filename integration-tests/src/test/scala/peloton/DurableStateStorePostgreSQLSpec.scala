package peloton

class DurableStateStorePostgreSQLSpec extends DurableStateStoreSpec:
  val config = PostgreSQLSpec.testContainerConfig
