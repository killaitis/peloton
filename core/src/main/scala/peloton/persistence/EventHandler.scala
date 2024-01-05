package peloton.persistence

/**
  * The event handler is used to modify the state of an event sourced actor. 
  * 
  * It is applied to the current state of the actor and each event and returns the 
  * new state of the actor. It is used in two different actor lifetime cycles:
  * 
  * - when the actor is already running and the actor's [[MessageHandler]] decides 
  *   to create an event. In this case, the event handler is applied to the new event 
  *   after successfully writing the event to the event store.
  * - when the actor is created, all previous events for this actor's persistence ID
  *   (starting at the most recent snapshot) are fetched from the event store.
  * 
  */
type EventHandler[S, E] = (state: S, event: E) => S
