package peloton

class DurableStateStoreCassandraSpec extends DurableStateStoreSpec:
  val config = CassandraSpec.testContainerConfig

class DurableStateStoreCassandraCurrentSpec extends DurableStateStoreSpec:
  val config = CassandraSpec.currentTestContainerConfig
