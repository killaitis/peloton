package peloton

class DurableStateStoreMySQLSpec extends DurableStateStoreSpec:
  val config = MySQLSpec.testContainerConfig

class DurableStateStoreMySQLCurrentSpec extends DurableStateStoreSpec:
  val config = MySQLSpec.currentTestContainerConfig
