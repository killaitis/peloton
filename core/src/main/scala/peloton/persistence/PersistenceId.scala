package peloton.persistence

opaque type PersistenceId = String

extension (id: PersistenceId)
  def toString(): String = id.toString()

object PersistenceId:
  
  def of(id: String): PersistenceId =
    if id eq null then 
      throw IllegalArgumentException("Persistence ID must not be null")

    if id.trim.isEmpty then 
      throw IllegalArgumentException("Persistence ID must not be empty")

    id
