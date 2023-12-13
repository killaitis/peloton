# Planned features
  - actor setup and shutdown behavior
  - actor hierarchy: concept of child actors
  - event sourcing actors

# Improvements / Refactoring
  
# Open issues
  - Remote actors: it works if called correctly, but it is possible to shoot yourself in the foot by asking unsupported messages. 
    This is related to the dirty ClassTag thingy in ActorRef
