package peloton.persistence

import cats.effect.* 

import peloton.config.Config
import scala.util.Try

trait Driver:
  def create(persistenceConfig: Config.Persistence): IO[Resource[IO, DurableStateStore]]


object DurableStateStoreFactory:

  def create(config: Config): IO[Resource[IO, DurableStateStore]] =
    for
      persistenceConfig  <- IO.fromOption(config.peloton.persistence)(new IllegalArgumentException("Invalid peloton config: no persistence section found.")) 
      driver             <- IO.fromTry(Try {
                              val classLoader = this.getClass().getClassLoader()
                              val driverClass = classLoader.loadClass(persistenceConfig.driver)
                              val ctor        = driverClass.getConstructor()
                              val driver      = ctor.newInstance().asInstanceOf[Driver]
                              driver
                            })
      store            <- driver.create(persistenceConfig)
    yield store

  def withDurableStateStore(config: Config)(f: DurableStateStore => IO[?]): IO[Unit] = 
    for
      store <- create(config)
      _     <- store.use(f)
    yield ()

end DurableStateStoreFactory