Command	    | What it does
------------------------------------------------------------------------------------------------------------------------------------
make dev	| Builds the backend JAR, then starts both servers concurrently. Ctrl+C stops everything.
make build	| Produces backend/target/backend-0.0.1-SNAPSHOT.jar and builds frontend/.next/ — the two artifacts you deploy to EC2.
make stop	| Kills anything still running on ports 8080 and 3000.