# Peloton Configuration

Peloton uses [Typesafe Config](https://github.com/lightbend/config) and HOCON. The usual place for application settings
is `src/main/resources/application.conf`. Peloton loads the default Typesafe Config sources; Java system properties can
override values from the configuration file.

Features that require configuration are optional and disabled when their configuration is absent. See the
[reference configuration](../core/src/main/resources/reference.conf) for the available top-level settings.

You can also load a `Config` explicitly with `Config.file[IO](path)` or `Config.string[IO](hocon)`, then pass it to
`ActorSystem.use(config)`, `DurableStateStore.use(config)`, or `EventStore.use(config)`.

## Persistence configuration

To use persistent actors, configure a persistence driver and its database connection, including any driver-specific
connection or pooling parameters. Add the corresponding `peloton-persistence-*` module to your application's runtime
dependencies; the driver class is loaded by name and is not included in `peloton-core`.

The driver connects Peloton's persistence interfaces to a database-specific implementation.

Durable state and event storage have independent configuration sections. They can use separate database connections or
different drivers.

### Integration-test database versions

The integration-test suite currently exercises these versions:

| Database   | Older tested version | Newer tested version |
| ---------- | -------------------- | -------------------- |
| MySQL      | 8.4                  | 9.7                  |
| PostgreSQL | 15                   | 18                   |
| Cassandra  | 4.1                  | 5.0                  |

These are the versions exercised by the tests, not a guarantee about every version in between.

A typical PostgreSQL configuration looks like this:

```hocon
peloton {
  persistence {
    durable-state-store {
      driver = peloton.persistence.postgresql.Driver
      params {
        url      = "jdbc:postgresql://db.mycompany.com:5432/peloton-durable-state"
        user     = "replace-with-database-user"
        password = "replace-with-database-password"
      }
    }

    event-store {
      driver = peloton.persistence.postgresql.Driver
      params {
        url      = "jdbc:postgresql://db.mycompany.com:5432/peloton-journal"
        user     = "replace-with-database-user"
        password = "replace-with-database-password"
      }
    }
  }
}
```

This example uses separate database connections for durable state and event storage.

To use one database configuration for both stores, define it once and reference it from both sections:

```hocon
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
    user              = "replace-with-database-user"
    password          = "replace-with-database-password"
  }
}
```

Do not commit real credentials to a repository. You can leave the connection values out of the file and pass them as
Java system properties instead:

```hocon
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
    "-Dpeloton.persistence.event-store.params.url=$DB_URL" \
    "-Dpeloton.persistence.event-store.params.user=$DB_USER" \
    "-Dpeloton.persistence.event-store.params.password=$DB_PASSWORD" \
    ...
```

The Java system properties override the corresponding values in `application.conf`. Use your deployment platform's
secret manager to provide credentials rather than storing them in source control.

### Driver-specific configuration

#### PostgreSQL

Peloton uses the PostgreSQL JDBC driver with a [Hikari](https://github.com/brettwooldridge/HikariCP) connection pool.

*Driver name*:
`peloton.persistence.postgresql.Driver`

*Parameters*:
| Name                | Required?          | Description |
| ------------------- | ------------------ | ----------- |
| `url`               | :white_check_mark: | The JDBC URL for the PostgreSQL database, e.g. `jdbc:postgresql://mydb.com:5432/peloton`. |
| `user`              | :white_check_mark: | The name of the database user account |
| `password`          | :white_check_mark: | The password of the database user account |
| `maximum-pool-size` |                    | The maximum Hikari connection-pool size. Default: `10` |


#### MySQL

Peloton uses the MySQL Connector/J together with a [Hikari](https://github.com/brettwooldridge/HikariCP)
connection pool.

*Driver name*:
`peloton.persistence.mysql.Driver`

*Parameters*:
| Name                | Required?          | Description |
| ------------------- | ------------------ | ----------- |
| `url`               | :white_check_mark: | The JDBC URL for the MySQL database, e.g. `jdbc:mysql://mydb.com:3306/peloton`. |
| `user`              | :white_check_mark: | The name of the database user account |
| `password`          | :white_check_mark: | The password of the database user account |
| `maximum-pool-size` |                    | The maximum Hikari connection-pool size. Default: `10` |


#### Cassandra (experimental)

The Cassandra adapter is integration-tested against Cassandra 4.1 and 5.0, but has not yet been
validated in production. Treat it as experimental for production deployments.

*Driver name*:
`peloton.persistence.cassandra.Driver`

*Parameters*:
| Name                   | Required?          | Description |
| ---------------------- | ------------------ | ----------- |
| `contact-points`       | :white_check_mark: | Comma-separated contact points in `host:port` format |
| `datacenter`           | :white_check_mark: | The local datacenter to connect to |
| `user`                 | :white_check_mark: | The name of the database user account |
| `password`             | :white_check_mark: | The password of the database user account |
| `replication-strategy` |                    | Replication class used when creating the keyspace. Default: `SimpleStrategy`. |
| `replication-factor`   |                    | Replication factor passed to Cassandra. Default: `1`. |

Both Cassandra stores use the same `peloton` keyspace. The keyspace is created with `IF NOT EXISTS`, so its replication
settings are applied only when it is first created. If both stores are enabled, configure matching settings for each.
The adapter passes a single replication factor to Cassandra. The default `SimpleStrategy` with a factor of `1` is
intended for simple or development setups; verify that the configured strategy and factor are appropriate for your
cluster before creating the keyspace.


## Peloton remote actor configuration

By default, actor systems are local-only and do not expose actors over HTTP. To enable the HTTP endpoint, configure a
hostname and port:

```hocon
peloton {
  http {
    hostname = "localhost"
    port     = 5000
  }
}
```

When the actor system starts, it binds an HTTP server to the configured hostname and port. For clients on other
machines, use a hostname or address they can reach and allow the connection through your network configuration.

The endpoint has no built-in authentication or TLS and deserializes message payloads with Kryo. Do not expose it to
untrusted networks; bind it to loopback or a trusted interface, or place it behind a TLS/authenticating proxy. See
[Remote Actors](./introduction.md#remote-actors) for usage details.