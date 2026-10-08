package peloton

class EventStoreMySQLSpec extends EventStoreSpec:
  val config = MySQLSpec.testContainerConfig

class EventStoreMySQLCurrentSpec extends EventStoreSpec:
  val config = MySQLSpec.currentTestContainerConfig
