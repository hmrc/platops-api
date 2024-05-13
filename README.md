
# platops-api

- This service is the only officially supported API within the PlatOps infrastructure.
- It exposes catalogue apis for external teams and directs webhooks to the relevant microservice.
- These apis should be rigorously tested to ensure backward-compatibility.

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").

## Running Integration Tests
Note: the tests in this repo are destructive to the data in MongoDB and assume the data in the database can be removed
1. Before testing make sure to run [setup_clean_mongo.sh](./setup_clean_mongo.sh), this script performs the following actions:
  - Gracefully stops any running MongoDB
  - Starts a new mongodb in Docker called `platops-api-it`


2. Run the required services with service-manager OR locally with `sbt`
    > You will likely need to launch with config to enable the test-only routes, and possibly to use your local.conf override.
    this might look something like:
    ```bash
    sbt "run -Dconfig.resource=local.conf -Dapplication.router=testOnlyDoNotUseInAppConf.Routes"
    ```
## Starting services via service-manager ([sm2](https://github.com/hmrc/sm2/))

> GOTCHA: Make sure you have the most up-to-date `service-manager-config` configuration.

You can either do a `git pull` on the checked out repository, or with `sm2` just call `sm2 --update-config`

### Before

For Service manager to run, you should:

- Be connected to VPN
- Have mongo running in the background (see above)

  Mongo needs to be at least `5.0` and run as a replica set.

  One way of doing so would be through docker. E.g.:
  - ```bash
      docker run -d -p 27017:27017 --name mongodb percona/mongo:5.0 --replSet rs0
      docker exec -it mongodb mongo
      > rs.initiate()
    ```

   in subsequent runs, you can simply run `docker start mongodb`

  - ```bash
    sm2 --start CATALOGUE
    ```

> TIP: Service manager may fail to start sometimes, so you might need to retry launching a couple of services manually if they fail to start.
For example, `sm2 --start CATALOGUE_FRONTEND`
To diagnose any issues use: `sm2 --logs CATALOGUE_FRONTEND`


## Clean up
- Stop mongo
  `docker stop platops-api-it`
- Stop service-manager:
  `sm2 --stop CATALOGUE`

