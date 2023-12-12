# Missing features
  - setup and shutdown behavior
  - actor lifetime / automatic termination
  - actor hierarchy: concept of child actors
  - event sourcing actors

# Improvements
  - this reflection thingy in ActorRef
  - the message handler loop should not be cancellable while processing a message
  
# Open issues
  - Remote actors: it works if called correctly, but it is possible to shoot yourself in the foot by asking unsupported messages
