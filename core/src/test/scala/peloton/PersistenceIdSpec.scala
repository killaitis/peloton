package peloton

import peloton.persistence.PersistenceId

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PersistenceIdSpec extends AnyFlatSpec with Matchers:

  behavior of "PersistenceId"

  it should "accept IDs up to 255 characters" in:
    noException should be thrownBy PersistenceId.of("x" * 255)

  it should "reject IDs longer than 255 characters" in:
    intercept[IllegalArgumentException](PersistenceId.of("x" * 256))

end PersistenceIdSpec