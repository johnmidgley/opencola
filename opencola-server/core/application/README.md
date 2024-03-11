<img src="../../../img/pull-tab.svg" width="150" alt="OpenCola"/>

# Application

The application library contains code for constructing the application (dependency injection, config, resources, etc.). 
Most of this code is specific to the collaboration tool and should likely be refactored to some other place.

```yaml
name: server # Configuration name

system: 
    resume: 
        enabled: false            # When enables, have the server try to detect wake from sleep (not super reliable)
        desiredDelayMillis: 10000 # Check the clock at this interval
        maxDelayMillis: 30000     # If the time since last check is greater than this, raise a resume event

eventBus:
    maxAttempts: 3 # Maximum number of times the event bus will retry handling an event on exception

server:
  host: 0.0.0.0 # Bind to any local address
  port: 5795    # Http port
  ssl:
    port: 5796  # Https port
    # sans: Other subject access names (, delimited) to include in cert. This is usually done automatically.

security:
  login:
    username: oc
    # password: password # Set if you're ok with less security for convenience 
    # authenticationRequired: false # Set if you're ok with less security for convenience    

network:
#  requestTimeoutMilliseconds: 20000 # Default is 20 seconds. May need to adjust for higher latency environments
#  socksProxy: # Socks proxy config. Only used by HttpNetworkProvider right now
#    host: hostname
#    port: 1080
#    offlineMode: true # Uncomment to start in offline mode (won't send / revieve any requests)

resources:
  # If you would like to edit resources (css, images, etc), uncomment 'allowEdit: true' below. When updating to a new
  # release,you may need to move your changes out, start the server so that new versions of the resources are created,
  # and then re-apply your changes.
  # WARNING: When set to false (the default), resources will be overwritten on server restart.
  # allowEdit: true

```
