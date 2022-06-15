# Filtered Device Mirror for Hubitat

Some devices have lots of capabilities. Lots and LOTS of capabilities. Maybe
it's one device with lots of capabilities. Maybe it's a parent-child device with
different capabilities exposed as children.  Normally, that's awesome.

However, if you have multiple hubs, you're probably using Hub Mesh to share a
few devices across them. We all try to have logical boundaries so most things
stay on one hub, but there's always that *one* automation that would be easier
if it could refer to a device on the other hub. And then that thing. Oh, and one
other.

But wait -- now every state change of those complex devices is causing events
and load on *both* hubs, when the other hub really only cares about one
particular property.  Isn't there a better way?

#### What This App Does

This app lets you select devices on your hub and create a virtual device
that reflects a single capability of that device.  You can then share
this filtered device using Hub Mesh, and the other hub only sees state
changes for the capability that you chose to expose.

# Change Log

* [5/15/2022] Initial release

