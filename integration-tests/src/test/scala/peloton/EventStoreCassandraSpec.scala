package peloton

class EventStoreCassandraSpec extends EventStoreSpec:
  val config = CassandraSpec.testContainerConfig

class EventStoreCassandraCurrentSpec extends EventStoreSpec:
  val config = CassandraSpec.currentTestContainerConfig
