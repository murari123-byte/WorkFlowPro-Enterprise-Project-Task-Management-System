# Troubleshooting

## Service fails to start: "Port 808x was already in use"
**Cause:** another application on the machine was already listening on 8080–8084.
**Fix (applied in Step 1):** WorkFlowPro defaults to ports **9080–9084**.
If a port is still taken, check who holds it and override it with an env var:
```bash
ss -ltnp | grep 9081
AUTH_SERVICE_PORT=9181 mvn -pl auth-service spring-boot:run
```

## Warning during tests: "Mockito is currently self-attaching to enable the inline-mock-maker"
Harmless on Java 21. Tests still pass. It can be silenced later by configuring Mockito as a
Java agent in the Surefire plugin; not needed now.

## Gateway health shows `discoveryComposite: UNKNOWN`
Expected. No service registry (Eureka etc.) is used; routes will use fixed URLs from
environment variables. Overall gateway status is still `UP`.

## `pkill -f <pattern>` kills your own terminal command
If the pattern also matches the command line of the shell running `pkill`, that shell is
killed too (exit code 144). Stop services with `Ctrl+C`, or match a narrower pattern such as
`pkill -f 'auth-service-0.1.0-SNAPSHOT.jar'`.
