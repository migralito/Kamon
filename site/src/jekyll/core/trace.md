---
title: Kamon | Core | Documentation
layout: default
---

Traces
======

In Kamon, a Trace is a group of events that are related to each other which together form a meaningful piece of functionality
for a given application. For example, if in order to fulfill a `GET` request to the `/users/kamon` resource, a application
sends a message to an actor, which reads the user data from a database and sends a message back with the user information to
finish the request, all those interactions would be considered as part of the same `TraceContext`. 

Back in the day tracing used to be simpler: if you create a Thread per request and manage everything related to that request
in that single Thread, then you could use a ThreadLocal and store all the valuable information you want about that request from
anywhere in the codebase and flush it all when the request is fulfilled. Sounds easy, right?, hold on that thought, we will 
disprove it soon.

When developing reactive applications on top of Akka the perspective of a trace changes from thread local to event local. 
If the system described above were to handle a hundred clients requesting for user's details, you might have a handful
of database access actors handling those requests. The load might be distributed across those actors, and within each actor
some messages will be procesed in the same Thread, then the dispatcher might schedule the actor to run in a different Thread,
but still, even while many messages can be processed in the same Thread, they are likely to be completely unrelated.

In order to cope with this situation Kamon provides with the notion of a `TraceContext` to group all related events and
collect the information we need about them. Once a `TraceContext` is created, Kamon will propagate it when new events are
generated within the context and once the `TraceContext` is finished, all the gathered information is flushed. The 
`TraceContext` is effectively stored in a ThreadLocal, but only during the processing of certain specific events and then
it is cleared out to avoid propagating it to unrelated events.



Starting a `TraceContext`
-------------------------

The `TraceRecorder` object provides you with a simple API to create, propagate and finish a context. To start a new context
use the `TraceRecorder.withNewTraceContext(..)` method. Let's dig into this with a simple example:

Suppouse you want to trace a process that involves a couple actors, and you want to make sure all related events become part 
of the same `TraceContext`. Our actors might look like this:

```scala
class UpperCaser extends Actor {
  val lengthCalculator = context.actorOf(Props[LengthCalculator], "length-calculator")

  def receive = {
    case anyString: String => lengthCalculator ! anyString.toUpperCase
  }
}
```



```scala
import kamon.trace.TraceRecorder

TraceRecorder.w

```



Rules for `TraceContext` Propagation
------------------------------------

* Sending messages to a Actor.