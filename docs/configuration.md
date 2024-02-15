# Peloton Configuration

Peloton uses the [Typesafe Config](https://github.com/lightbend/config) with HOCON configuration 
files to configure your application. Usually, you'd create your own configuration file 
(`./src/main/resources/application.conf`), but of cause you can use different ways the Typesafe 
Config provides to configure your system.

By default, all Peloton features that require special configuration parameters are optional and 
disabled by default in the default configuration. 

Refer to the [reference configuration](/core/src/main/resources/reference.conf) for an overview.

## Persistence configuration

To use persistent actors in your actor system, you have to specify a Peloton driver and a database 
connection, including database-specific connection or pooling parameters. 

The Peloton driver connects the generic Peloton persistence interface to a database-specific 
persistence implementation, e.g., PostgreSQL.

Both the durable state store and the event store have independent configuration sections. 
This allows you to use different database backends for both types of persistence.

A typical persistence configuration looks like this:

```
peloton {
  persistence {
    durable-state-store {
      driver = peloton.persistence.postgresql.Driver
      params {
        url      = "jdbc:postgresql://db.mycompany.com:5432/peloton-durable-state"
        user     = "peloton-dss"
        password = "SuperSecret1"
      }
    }

    event-store {
      driver = peloton.persistence.postgresql.Driver
      params {
        url      = "jdbc:postgresql://db.mycompany.com:5432/peloton-journal"
        user     = "peloton-es"
        password = "EvenMoreSecret"
      }
    }
  }
}
```

Here, different database connections are configured for the durable state store
and the event store. 

If you're just using a single database for both persistence types, you can use 
HOCON's reference features to define it once and reference it twice:

```
peloton {
  persistence {
    durable-state-store = ${my-postgresql-config}
    event-store         = ${my-postgresql-config}
  }
}

my-postgresql-config {
  driver = peloton.persistence.postgresql.Driver
  params {
    url               = "jdbc:postgresql://mydb.com:5432/peloton"
    user              = "alvin"
    password          = "stardust"
  }
}
```

Storing sensitive database credentials in a configuration file is usually a bad idea 
(especially if the configuration file is also stored in a git repository), so you 
might consider leaving them out in the configuration file and passing them via 
Java system properties:

```
peloton {
  persistence {
    event-store {
      driver = peloton.persistence.postgresql.Driver
    }
  }
}
```

```bash
java \
    "-Dpeloton.persistence.event-store.params.url=${DB_URL}" \
    "-Dpeloton.persistence.event-store.params.user=${DB_USER}" \
    "-Dpeloton.persistence.event-store.params.password=${DB_PASSWD}" \
    ...
```

### Driver-specific configuration

#### PostgreSQL

Peleton uses the PostgreSQL JDBC driver in conjunction with a [Hikari](https://github.com/brettwooldridge/HikariCP) 
connection pool.

*Driver name*:
`peloton.persistence.postgresql.Driver`

*Parameters*:
| Name                | Required?          | Description |
| ------------------- | ------------------ | ----------- |
| `url`               | :white_check_mark: | The JDBC URL for the PostgreSQL database. Example: `jdbc:postgresql://mydb.com:5432/peloton` |
| `user`              | :white_check_mark: | The name of the database user account |
| `password`          | :white_check_mark: | The password of the database user account|
| `maximum-pool-size` |                    | The maximum size of the Hikari connection pool. Default: `10` |


#### Cassandra (experimental)

*Driver name*:
`peloton.persistence.cassandra.Driver`

*Parameters*:
| Name                   | Required?          | Description |
| ---------------------- | ------------------ | ----------- |
| `contact-points`       | :white_check_mark: | a comma-separated list of contact point addresses (format: "host:port") |
| `datacenter`           | :white_check_mark: | the name of the datacenter to connect to |
| `user`                 | :white_check_mark: | The name of the database user account |
| `password`             | :white_check_mark: | The password of the database user account|
| `replication-strategy` |                    | The keyspace replication strategy. Default: `SimpleStrategy` |
| `replication-factor`   |                    | The keyspace replication factor. Default: 1 |


## Peloton remote actor configuration

By default, Peloton actor systems are local-only and do not provide access to their actors via HTTP. 
To enable access to the actors of a local actor system, you have to specify a host name and a port 
in the configuration:

```
peloton {
  http {
    hostname = pelo1.intra.foo.net
    port     = 5000
  }
}
```

This will start an HTTP server on the local machine. Actors from other machines/processes can then use 
a remote actor reference to send messages to actors belonging to the actor system on this machine.