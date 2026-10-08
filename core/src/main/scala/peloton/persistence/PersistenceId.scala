package peloton.persistence


opaque type PersistenceId = String

// extension (id: PersistenceId)
//   def toString(): String = id.toString()

object PersistenceId:
  
  def of(id: String): PersistenceId =
    if id eq null then 
      throw IllegalArgumentException("Persistence ID must not be null")

    if id.trim.isEmpty then 
      throw IllegalArgumentException("Persistence ID must not be empty")

    if id.length > 255 then
      throw IllegalArgumentException("Persistence ID must not exceed 255 characters")

    id
