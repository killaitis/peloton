package peloton

class DurableStateStorePostgreSQLSpec extends DurableStateStoreSpec:
  val config = PostgreSQLSpec.testContainerConfig

class DurableStateStorePostgreSQLCurrentSpec extends DurableStateStoreSpec:
  val config = PostgreSQLSpec.currentTestContainerConfig
